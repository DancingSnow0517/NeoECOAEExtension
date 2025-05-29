package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOStorageVentBlockEntity extends NEBlockEntity<NEStorageCluster, ECOStorageVentBlockEntity> {
    public ECOStorageVentBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState, NEStorageClusterCalculator::new);
    }
}
