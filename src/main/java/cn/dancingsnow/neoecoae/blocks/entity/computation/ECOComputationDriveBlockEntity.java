package cn.dancingsnow.neoecoae.blocks.entity.computation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOComputationDriveBlockEntity extends AbstractComputationBlockEntity<ECOComputationDriveBlockEntity>{
    public ECOComputationDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }
}
