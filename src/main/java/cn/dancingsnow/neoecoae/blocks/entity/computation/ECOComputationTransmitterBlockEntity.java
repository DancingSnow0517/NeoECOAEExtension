package cn.dancingsnow.neoecoae.blocks.entity.computation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOComputationTransmitterBlockEntity extends AbstractComputationBlockEntity<ECOComputationTransmitterBlockEntity> {
    public ECOComputationTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }
}
