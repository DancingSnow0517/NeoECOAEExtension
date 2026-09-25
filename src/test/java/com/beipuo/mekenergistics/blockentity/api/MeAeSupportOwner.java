package com.beipuo.mekenergistics.blockentity.api;

import appeng.api.networking.crafting.ICraftingProvider;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;

/** Minimal reflection ABI fixture from Mek-Energistics 3.0.8 (af0745d); never shipped. */
public interface MeAeSupportOwner extends ICraftingProvider {
    boolean isSmartPatternMultiplicationEnabled();
    AbstractMeAeSupport<?> getPatternAeSupport();
}
