package cn.dancingsnow.neoecoae.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface NEBlockEntityTicker<T extends BlockEntity> {
    void tick(T blockEntity, Level var1, BlockPos var2, BlockState var3);
}
