package com.jerry.mekmm.common.tile.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Minimal upstream ABI: MoreMachine factories independently implement the factory contract. */
public class TileEntityMoreMachineFactory extends BlockEntity {
    public Object[] recipeCacheLookupMonitors;
    public boolean[] activeStates;
    public int[] progress;
    public int ticksRequired;
    public TileEntityMoreMachineFactory() { super(net.minecraft.world.level.block.entity.BlockEntityType.FURNACE, BlockPos.ZERO, Blocks.FURNACE.defaultBlockState()); }
    public int getTicksRequired() { return ticksRequired; }
    public int getProgress(int index) { return progress[index]; }
}
