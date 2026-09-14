package com.atir.molecularmanipulator.api.crafting;

import appeng.api.networking.crafting.ICraftingProvider;

/** Compile-only ABI declaration for OmniSequence 1.3.9 API v1. Never shipped. */
public interface OmniBatchCraftingProvider extends ICraftingProvider {
    OmniBatchAdmission prepareOmniBatch(OmniBatchProbe probe);
}
