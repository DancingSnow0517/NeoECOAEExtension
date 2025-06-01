package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.blocks.ECOMachineCasing;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOMachineCasingBlockEntity<C extends NECluster<C>> extends NEBlockEntity<C, ECOMachineCasingBlockEntity<C>> {

    public ECOMachineCasingBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        NEClusterCalculator.Factory<C> factory
    ) {
        super(type, pos, blockState, factory);
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (this.level == null || this.notLoaded() || this.isRemoved()) {
            return;
        }
        if (this.cluster != null) {
            level.setBlock(
                worldPosition,
                level.getBlockState(worldPosition).setValue(ECOMachineCasing.INVISIBLE, this.cluster.shouldCasingHide(this)),
                Block.UPDATE_CLIENTS
            );
        } else {
            level.setBlock(
                worldPosition,
                level.getBlockState(worldPosition).setValue(ECOMachineCasing.INVISIBLE, false),
                Block.UPDATE_CLIENTS
            );
        }
    }
}
