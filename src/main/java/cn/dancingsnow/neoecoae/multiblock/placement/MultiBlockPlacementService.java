package cn.dancingsnow.neoecoae.multiblock.placement;

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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class MultiBlockPlacementService {
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
        int repeats
    ) {
        return preview(level, controllerPos, controllerState, definition, repeats, false);
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
        List<ItemStack> requiredItems = new ArrayList<>();
        int reusedBlockCount = 0;

        for (PlannedBlock plannedBlock : context.getPlannedBlocks()) {
            if (plannedBlock.relativePos().equals(MultiBlockRotation.CONTROLLER_ANCHOR)) {
                continue;
            }
            BlockPos worldPos = MultiBlockRotation.localToWorld(plannedBlock.relativePos(), controllerPos, facing, mirrored);
            BlockState targetState = MultiBlockRotation.rotateState(plannedBlock.targetState(), facing, mirrored);
            WorldPlannedBlock worldBlock = new WorldPlannedBlock(worldPos, targetState, plannedBlock.requiredItem().copy());
            allBlocks.add(worldBlock);

            BlockState existingState = level.getBlockState(worldPos);
            if (existingState.equals(targetState)) {
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

    public static boolean buildInstant(ServerLevel level, MultiBlockPlacementPlan plan) {
        if (!plan.getConflictPositions().isEmpty()) {
            return false;
        }
        for (WorldPlannedBlock worldBlock : plan.getMissingBlocks()) {
            level.setBlock(worldBlock.worldPos(), worldBlock.targetState(), Block.UPDATE_ALL);
        }
        return true;
    }

    public static MultiBlockBuildSession createBuildSession(ServerLevel level, MultiBlockPlacementPlan plan) {
        return new MultiBlockBuildSession(plan.getMissingBlocks(), nextPlacementDelay(level));
    }

    public static PlacementTickResult tickBuild(ServerLevel level, MultiBlockBuildSession session, ServerPlayer player) {
        if (session == null || session.isFinished()) {
            return PlacementTickResult.COMPLETED;
        }
        if (!session.tickDelay()) {
            return PlacementTickResult.WAITING;
        }

        WorldPlannedBlock worldBlock = session.getCurrentBlock();
        BlockState existingState = level.getBlockState(worldBlock.worldPos());
        if (!existingState.equals(worldBlock.targetState()) && !(existingState.isAir() || existingState.canBeReplaced())) {
            return PlacementTickResult.BLOCKED;
        }

        if (!existingState.equals(worldBlock.targetState())) {
            if (!player.isCreative() && !consumeRequiredItem(player, worldBlock.requiredItem())) {
                return PlacementTickResult.BLOCKED;
            }
            level.setBlock(worldBlock.worldPos(), worldBlock.targetState(), Block.UPDATE_ALL);
            playPlacementSound(level, worldBlock);
        }

        session.advance(nextPlacementDelay(level));
        return session.isFinished() ? PlacementTickResult.COMPLETED : PlacementTickResult.ADVANCED;
    }

    public static boolean hasRequiredItems(Player player, List<ItemStack> requiredItems) {
        for (ItemStack requiredItem : requiredItems) {
            if (countMatchingItems(player, requiredItem) < requiredItem.getCount()) {
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

    private static void mergeItem(List<ItemStack> requiredItems, ItemStack toAdd) {
        for (ItemStack requiredItem : requiredItems) {
            if (ItemStack.isSameItemSameComponents(requiredItem, toAdd)) {
                requiredItem.grow(toAdd.getCount());
                return;
            }
        }
        requiredItems.add(toAdd.copy());
    }

    private static int nextPlacementDelay(ServerLevel level) {
        return 1;
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
