package cn.dancingsnow.neoecoae.api.me;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;

/**
 * Binary compatibility base for Useless Mod 2.3.8 and AE2 Lightning Tech 2.1.
 *
 * <p>The implementation moved to {@code crafting.execution.ECOCraftingCPU}; the
 * old package name remains in the type hierarchy because Useless performs a
 * direct instanceof/checkcast against this class. AE2 Lightning Tech also targets
 * this class with a Mixin invoker for {@link #markDirty()}.</p>
 */
@Deprecated(forRemoval = false)
public abstract class ECOCraftingCPU implements ICraftingCPU {
    protected ECOCraftingCPU() {
    }

    /** Compatibility method used by Useless's NeoECOAE bridge. */
    public abstract IGrid getGrid();

    /**
     * Marks the owning crafting thread for saving; CPUs without an owner do nothing.
     * Declared here so legacy Mixin invokers can resolve the method on their exact
     * target class and dispatch to the current implementation.
     */
    public abstract void markDirty();

    /**
     * Returns the legacy logic type used by ExtendedAE Plus' NeoECOAE virtual-crafting mixins.
     * The current implementation overrides this covariantly with its execution-package logic.
     */
    public ECOCraftingCPULogic getLogic() {
        return null;
    }
}
