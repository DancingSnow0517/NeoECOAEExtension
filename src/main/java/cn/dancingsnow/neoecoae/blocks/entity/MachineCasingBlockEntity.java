package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.blocks.MachineCasing;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MachineCasingBlockEntity<C extends NECluster<C>> extends NEBlockEntity<C, MachineCasingBlockEntity<C>> {

    public MachineCasingBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        NEClusterCalculator.Factory<C> factory
    ) {
        super(type, pos, blockState, factory);
    }

    @Override
    protected void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (this.cluster != null) {
            level.setBlock(
                worldPosition,
                level.getBlockState(worldPosition).setValue(MachineCasing.INVISIBLE, this.cluster.shouldCasingHide(this)),
                Block.UPDATE_CLIENTS
            );
        } else {
            level.setBlock(
                worldPosition,
                level.getBlockState(worldPosition).setValue(MachineCasing.INVISIBLE, false),
                Block.UPDATE_CLIENTS
            );
        }
    }
}
