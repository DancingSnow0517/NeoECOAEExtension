package cn.dancingsnow.neoecoae.multiblock.placement;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirements;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MultiBlockPlacementService {
    private static final int PLACEMENT_DELAY_TICKS = 1;
    public enum PlacementTickResult {
        WAITING,
        ADVANCED,
        COMPLETED,
        BLOCKED
    }

    private MultiBlockPlacementService() {
    }

    public static MultiBlockPlacementPlan preview(
        Level level,
        BlockPos controllerPos,
        BlockState controllerState,
        MultiBlockDefinition definition,
        int repeats,
        boolean mirrored
    ) {
        Direction facing = controllerState.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
            .orElse(Direction.NORTH);
        MultiBlockPlanContext context = new MultiBlockPlanContext(repeats);
        definition.createLevel(context);

        List<WorldPlannedBlock> allBlocks = new ArrayList<>();
        List<WorldPlannedBlock> missingBlocks = new ArrayList<>();
        List<BlockPos> conflictPositions = new ArrayList<>();
        List<RequiredItem> requiredItems = new ArrayList<>();
        int reusedBlockCount = 0;

        for (PlannedBlock plannedBlock : context.getPlannedBlocks()) {
            if (plannedBlock.relativePos().equals(MultiBlockRotation.CONTROLLER_ANCHOR)) {
                continue;
            }
            BlockPos worldPos = MultiBlockRotation.localToWorld(plannedBlock.relativePos(), controllerPos, facing, mirrored);
            BlockState targetState = MultiBlockRotation.rotateState(plannedBlock.targetState(), facing, mirrored);
            WorldPlannedBlock worldBlock = new WorldPlannedBlock(worldPos, targetState, plannedBlock.requiredItem().copy(),
                plannedBlock.requirement().transform(state -> MultiBlockRotation.rotateState(state, facing, mirrored)));
            allBlocks.add(worldBlock);

            BlockState existingState = level.getBlockState(worldPos);
            if (acceptsExisting(level, worldBlock, existingState)) {
                reusedBlockCount++;
                continue;
            }
            if (existingState.isAir() || existingState.canBeReplaced()) {
                missingBlocks.add(worldBlock);
                mergeItem(requiredItems, worldBlock.requiredItem());
                continue;
            }
            conflictPositions.add(worldPos);
        }

        return new MultiBlockPlacementPlan(allBlocks, missingBlocks, conflictPositions, requiredItems, reusedBlockCount);
    }

    public static boolean buildInstant(ServerLevel level, MultiBlockPlacementPlan plan, ServerPlayer player) {
        if (!plan.getConflictPositions().isEmpty()
            || (!player.isCreative() && !hasRequiredItems(player, plan.getRequiredItems()))) {
            return false;
        }
        for (WorldPlannedBlock worldBlock : plan.getMissingBlocks()) {
            if (!placeBuildBlock(level, worldBlock, player)) return false;
        }
        return true;
    }

    public static MultiBlockBuildSession createBuildSession(ServerLevel level, MultiBlockPlacementPlan plan) {
        return new MultiBlockBuildSession(plan.getMissingBlocks(), PLACEMENT_DELAY_TICKS);
    }

    public static PlacementTickResult tickBuild(ServerLevel level, MultiBlockBuildSession session, ServerPlayer player) {
        if (session == null || session.isFinished()) {
            return PlacementTickResult.COMPLETED;
        }
        if (!session.tickDelay()) {
            return PlacementTickResult.WAITING;
        }

        if (!placeBuildBlock(level, session.getCurrentBlock(), player)) {
            return PlacementTickResult.BLOCKED;
        }
        session.advance(PLACEMENT_DELAY_TICKS);
        return session.isFinished() ? PlacementTickResult.COMPLETED : PlacementTickResult.ADVANCED;
    }

    private static boolean placeBuildBlock(ServerLevel level, WorldPlannedBlock worldBlock, ServerPlayer player) {
        if (!level.hasChunkAt(worldBlock.worldPos())
            || !level.getWorldBorder().isWithinBounds(worldBlock.worldPos())
            || !level.mayInteract(player, worldBlock.worldPos())) return false;
        BlockState existingState = level.getBlockState(worldBlock.worldPos());
        if (acceptsExisting(level, worldBlock, existingState)) return true;
        if (!(existingState.isAir() || existingState.canBeReplaced())) return false;
        if (!placeWithPermissionCheck(level, worldBlock, player)) return false;
        if (!player.isCreative() && !consumeRequiredItem(player, worldBlock.requiredItem())) {
            level.setBlock(worldBlock.worldPos(), existingState, Block.UPDATE_ALL);
            return false;
        }
        playPlacementSound(level, worldBlock);
        return true;
    }

    public static boolean acceptsExisting(Level level, WorldPlannedBlock planned, BlockState actual) {
        var role = ECOMultiblockParts.acceptsExpected(
            ECOMultiblockParts.context(level, planned.worldPos()), planned.targetState());
        return ECOMultiblockRequirements.accepts(
            new ECOMultiblockRequirements.Context(
                level, planned.worldPos(), planned.targetState(), actual),
            role == null ? planned.requirement().accepts(actual) : role);
    }

    /** Validates the same alternatives used by previews/building without mutating the world. */
    public static boolean matches(Level level, BlockPos controllerPos, BlockState controllerState,
            MultiBlockDefinition definition, int repeats, boolean mirrored) {
        var plan = preview(level, controllerPos, controllerState, definition, repeats, mirrored);
        return plan.getConflictPositions().isEmpty() && plan.getMissingBlocks().isEmpty();
    }

    public static boolean hasRequiredItems(Player player, List<RequiredItem> requiredItems) {
        for (RequiredItem requiredItem : requiredItems) {
            if (countMatchingItems(player, requiredItem.stack()) < requiredItem.count()) {
                return false;
            }
        }
        return true;
    }

    public static int countMatchingItems(Player player, ItemStack target) {
        Set<ResourceHandler<ItemResource>> visitedHandlers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            count += countMatchingItems(stack, target, visitedHandlers);
        }
        count += countMatchingItems(player.getInventory().getItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND), target, visitedHandlers);
        return count;
    }

    private static boolean consumeRequiredItem(Player player, ItemStack requiredItem) {
        Set<ResourceHandler<ItemResource>> visitedHandlers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        int remaining = requiredItem.getCount();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (remaining <= 0) {
                break;
            }
            remaining = consumeFromStack(stack, requiredItem, remaining, visitedHandlers);
        }
        if (remaining > 0) {
            remaining = consumeFromStack(player.getInventory().getItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND), requiredItem, remaining, visitedHandlers);
        }
        if (remaining > 0) {
            return false;
        }
        player.getInventory().setChanged();
        return true;
    }

    private static int countMatchingItems(ItemStack stack, ItemStack target, Set<ResourceHandler<ItemResource>> visitedHandlers) {
        if (stack.isEmpty()) {
            return 0;
        }

        int count = ItemStack.isSameItemSameComponents(stack, target) ? stack.getCount() : 0;
        return count + countMatchingItems(itemHandler(stack), target, visitedHandlers);
    }

    private static int countMatchingItems(ResourceHandler<ItemResource> handler, ItemStack target, Set<ResourceHandler<ItemResource>> visitedHandlers) {
        if (handler == null || !visitedHandlers.add(handler)) {
            return 0;
        }

        int count = 0;
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            if (resource.matches(target)) {
                count += handler.getAmountAsInt(slot);
            }
            count += countMatchingItems(nestedHandler(handler, slot), target, visitedHandlers);
        }
        return count;
    }

    private static int consumeFromStack(ItemStack stack, ItemStack target, int remaining, Set<ResourceHandler<ItemResource>> visitedHandlers) {
        if (remaining <= 0 || stack.isEmpty()) {
            return remaining;
        }

        if (ItemStack.isSameItemSameComponents(stack, target)) {
            int taken = Math.min(stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
            if (remaining <= 0) {
                return 0;
            }
        }

        return consumeFromHandler(itemHandler(stack), target, remaining, visitedHandlers);
    }

    private static int consumeFromHandler(ResourceHandler<ItemResource> handler, ItemStack target, int remaining, Set<ResourceHandler<ItemResource>> visitedHandlers) {
        if (handler == null || remaining <= 0 || !visitedHandlers.add(handler)) {
            return remaining;
        }

        for (int slot = 0; slot < handler.size(); slot++) {
            if (remaining <= 0) {
                return 0;
            }

            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            if (resource.matches(target)) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int extracted = handler.extract(slot, resource, remaining, transaction);
                    transaction.commit();
                    remaining -= extracted;
                }
                if (remaining <= 0) {
                    return 0;
                }
            }

            remaining = consumeFromHandler(nestedHandler(handler, slot), target, remaining, visitedHandlers);
        }
        return remaining;
    }

    private static ResourceHandler<ItemResource> itemHandler(ItemStack stack) {
        return stack.getCapability(Capabilities.Item.ITEM, ItemAccess.forStack(stack));
    }

    private static ResourceHandler<ItemResource> nestedHandler(ResourceHandler<ItemResource> handler, int slot) {
        return ItemAccess.forHandlerIndex(handler, slot).getCapability(Capabilities.Item.ITEM);
    }

    private static void mergeItem(List<RequiredItem> requiredItems, ItemStack toAdd) {
        for (int i = 0; i < requiredItems.size(); i++) {
            RequiredItem requiredItem = requiredItems.get(i);
            if (ItemStack.isSameItemSameComponents(requiredItem.stack(), toAdd)) {
                requiredItems.set(i, requiredItem.grow(toAdd.getCount()));
                return;
            }
        }
        requiredItems.add(new RequiredItem(toAdd, toAdd.getCount()));
    }

    private static boolean placeWithPermissionCheck(ServerLevel level, WorldPlannedBlock worldBlock, ServerPlayer player) {
        BlockPos pos = worldBlock.worldPos();
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos, Block.UPDATE_ALL);
        BlockState placedAgainst = level.getBlockState(pos.below());
        if (!level.setBlock(pos, worldBlock.targetState(), Block.UPDATE_ALL)) {
            return false;
        }
        BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, placedAgainst, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            snapshot.restore();
            return false;
        }
        return true;
    }

    private static void playPlacementSound(ServerLevel level, WorldPlannedBlock worldBlock) {
        SoundType soundType = worldBlock.targetState().getSoundType(level, worldBlock.worldPos(), null);
        level.playSound(
            null,
            worldBlock.worldPos(),
            soundType.getPlaceSound(),
            SoundSource.BLOCKS,
            (soundType.getVolume() + 1.0F) / 2.0F,
            soundType.getPitch() * 0.8F
        );
    }
}
