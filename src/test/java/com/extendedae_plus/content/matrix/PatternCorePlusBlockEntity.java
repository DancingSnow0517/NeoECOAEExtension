package com.extendedae_plus.content.matrix;

import appeng.api.networking.crafting.ICraftingProvider;

/** A pattern core is batch-capable only while attached to a super matrix. */
public abstract class PatternCorePlusBlockEntity implements ICraftingProvider {
    public abstract Object eap$getSuperMatrixCluster();
}
