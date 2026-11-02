package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.api.power.ECOPowerRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOStorageVentBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster, ECOStorageVentBlockEntity> {
    public ECOStorageVentBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator::new);
    }

    @Override
    public void onReady() {
        super.onReady();
        applyIdlePower(ECOPowerRole.STORAGE_VENT, 64, 0);
    }
}
