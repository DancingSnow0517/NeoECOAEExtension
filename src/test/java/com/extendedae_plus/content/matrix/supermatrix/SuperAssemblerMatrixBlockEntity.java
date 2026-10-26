package com.extendedae_plus.content.matrix.supermatrix;

import appeng.api.networking.crafting.ICraftingProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Test ABI fixture; both EAP structures expose this provider superclass. */
public abstract class SuperAssemblerMatrixBlockEntity extends BlockEntity implements ICraftingProvider {
    protected SuperAssemblerMatrixBlockEntity() { super(null, BlockPos.ZERO, Blocks.AIR.defaultBlockState()); }
    public Object eap$getSuperMatrixCluster() { return null; }
}
