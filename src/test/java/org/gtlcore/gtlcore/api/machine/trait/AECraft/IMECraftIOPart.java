package org.gtlcore.gtlcore.api.machine.trait.AECraft;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

/** Test-only shape of the optional GTLCore provider contract. */
public interface IMECraftIOPart extends ICraftingProvider {
    long gtlcore$getMaxPatternOperations(IPatternDetails pattern, long requestedOperations);
}
