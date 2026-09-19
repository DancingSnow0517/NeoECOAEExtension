package cn.dancingsnow.neoecoae.api.me;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;

/**
 * Binary compatibility base for Useless Mod 2.3.8.
 *
 * <p>The implementation moved to {@code crafting.execution.ECOCraftingCPU}; the
 * old package name remains in the type hierarchy because Useless performs a
 * direct instanceof/checkcast against this class.</p>
 */
@Deprecated(forRemoval = false)
public abstract class ECOCraftingCPU implements ICraftingCPU {
    protected ECOCraftingCPU() {
    }

    /** Compatibility method used by Useless's NeoECOAE bridge. */
    public abstract IGrid getGrid();
}
