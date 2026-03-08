package cn.dancingsnow.neoecoae.multiblock.placement;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

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
        ServerLevel level,
        BlockPos controllerPos,
        BlockState controllerState,
        MultiBlockDefinition definition,
        int repeats
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
            BlockPos worldPos = MultiBlockRotation.localToWorld(plannedBlock.relativePos(), controllerPos, facing);
            BlockState targetState = MultiBlockRotation.rotateState(plannedBlock.targetState(), facing);
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

    private static int countMatchingItems(Player player, ItemStack target) {
        Set<IItemHandler> visitedHandlers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        return countMatchingItems(player.getInventory().items, target, visitedHandlers)
            + countMatchingItems(player.getInventory().offhand, target, visitedHandlers);
    }

    private static boolean consumeRequiredItem(Player player, ItemStack requiredItem) {
        Set<IItemHandler> visitedHandlers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        int remaining = requiredItem.getCount();
        remaining = consumeFromList(player.getInventory().items, requiredItem, remaining, visitedHandlers);
        if (remaining > 0) {
            remaining = consumeFromList(player.getInventory().offhand, requiredItem, remaining, visitedHandlers);
        }
        if (remaining > 0) {
            return false;
        }
        player.getInventory().setChanged();
        return true;
    }

    private static int countMatchingItems(List<ItemStack> stacks, ItemStack target, Set<IItemHandler> visitedHandlers) {
        int count = 0;
        for (ItemStack stack : stacks) {
            count += countMatchingItems(stack, target, visitedHandlers);
        }
        return count;
    }

    private static int countMatchingItems(ItemStack stack, ItemStack target, Set<IItemHandler> visitedHandlers) {
        if (stack.isEmpty()) {
            return 0;
        }

        int count = ItemStack.isSameItemSameComponents(stack, target) ? stack.getCount() : 0;
        IItemHandler itemHandler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (itemHandler == null || !visitedHandlers.add(itemHandler)) {
            return count;
        }

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            count += countMatchingItems(itemHandler.getStackInSlot(slot), target, visitedHandlers);
        }
        return count;
    }

    private static int consumeFromList(List<ItemStack> stacks, ItemStack target, int remaining, Set<IItemHandler> visitedHandlers) {
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                return 0;
            }
            remaining = consumeFromStack(stack, target, remaining, visitedHandlers);
        }
        return remaining;
    }

    private static int consumeFromStack(ItemStack stack, ItemStack target, int remaining, Set<IItemHandler> visitedHandlers) {
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

        IItemHandler itemHandler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (itemHandler == null || !visitedHandlers.add(itemHandler)) {
            return remaining;
        }

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            if (remaining <= 0) {
                return 0;
            }

            ItemStack slotStack = itemHandler.getStackInSlot(slot);
            if (slotStack.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItemSameComponents(slotStack, target)) {
                int toExtract = Math.min(slotStack.getCount(), remaining);
                ItemStack extracted = itemHandler.extractItem(slot, toExtract, false);
                remaining -= extracted.getCount();
                if (remaining <= 0) {
                    return 0;
                }
            }

            remaining = consumeFromStack(itemHandler.getStackInSlot(slot), target, remaining, visitedHandlers);
        }
        return remaining;
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