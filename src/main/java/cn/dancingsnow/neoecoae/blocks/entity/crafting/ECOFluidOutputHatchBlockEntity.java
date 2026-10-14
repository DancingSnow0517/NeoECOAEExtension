package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOFluidHatchBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps the original constructor and inherited public tank available to compiled addons. */
@Deprecated(forRemoval = false)
public class ECOFluidOutputHatchBlockEntity extends ECOFluidHatchBlockEntity<NECraftingCluster> {
    public ECOFluidOutputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NECraftingClusterCalculator::new, CRAFTING_OUTPUT);
    }
}
