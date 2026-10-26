package com.jerry.mekextras.common.tile.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Minimal upstream ABI: Extras factories do not extend Mekanism's TileEntityFactory. */
public class TileEntityExtraFactory extends BlockEntity {
    public Object[] recipeCacheLookupMonitors;
    public boolean[] activeStates;
    public int[] progress;
    public int ticksRequired;
    public TileEntityExtraFactory() { super(net.minecraft.world.level.block.entity.BlockEntityType.FURNACE, BlockPos.ZERO, Blocks.FURNACE.defaultBlockState()); }
    public int getTicksRequired() { return ticksRequired; }
    public int getProgress(int index) { return progress[index]; }
}
