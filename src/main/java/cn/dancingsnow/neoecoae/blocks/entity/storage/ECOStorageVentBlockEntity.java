package cn.dancingsnow.neoecoae.blocks.entity.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOStorageVentBlockEntity extends AbstractStorageBlockEntity<ECOStorageVentBlockEntity> {
    public ECOStorageVentBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState);
    }
}
