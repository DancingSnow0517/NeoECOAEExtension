package cn.dancingsnow.neoecoae.multiblock.placement;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@Getter
public class MultiBlockPlacementPlan {
    private final List<WorldPlannedBlock> allBlocks;
    private final List<WorldPlannedBlock> missingBlocks;
    private final List<BlockPos> conflictPositions;
    private final List<ItemStack> requiredItems;
    private final int reusedBlockCount;

    public MultiBlockPlacementPlan(
        List<WorldPlannedBlock> allBlocks,
        List<WorldPlannedBlock> missingBlocks,
        List<BlockPos> conflictPositions,
        List<ItemStack> requiredItems,
        int reusedBlockCount
    ) {
        this.allBlocks = List.copyOf(allBlocks);
        this.missingBlocks = List.copyOf(missingBlocks);
        this.conflictPositions = List.copyOf(conflictPositions);
        this.requiredItems = requiredItems.stream().map(ItemStack::copy).toList();
        this.reusedBlockCount = reusedBlockCount;
    }

    public int getRequiredItemCount() {
        return requiredItems.stream().mapToInt(ItemStack::getCount).sum();
    }
}