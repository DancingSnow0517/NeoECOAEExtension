package com.beipuo.mekenergistics.blockentity.support;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;

/** Minimal reflection ABI fixture; queue behavior is supplied by each test. */
public abstract class AbstractMeAeSupport<T> {
    public abstract IManagedGridNode getMainNode();
    public abstract boolean hasRegisteredPattern(IPatternDetails pattern);
    public abstract boolean enqueueSmartPattern(IPatternDetails pattern, KeyCounter[] inputs);
}
