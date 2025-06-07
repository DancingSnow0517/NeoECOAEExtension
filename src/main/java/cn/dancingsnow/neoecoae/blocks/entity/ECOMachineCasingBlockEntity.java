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
        BlockState newState = level.getBlockState(worldPosition);
        if (this.cluster != null) {
            if (newState.hasProperty(ECOMachineCasing.INVISIBLE)) {
                newState = newState.setValue(ECOMachineCasing.INVISIBLE, this.cluster.shouldCasingHide(this));
            }
        } else {
            if (newState.hasProperty(ECOMachineCasing.INVISIBLE)) {
                newState = newState.setValue(ECOMachineCasing.INVISIBLE, false);
            }
        }
        level.setBlock(
            worldPosition,
            newState,
            Block.UPDATE_CLIENTS
        );
    }
}
