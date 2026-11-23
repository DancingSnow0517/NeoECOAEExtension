package cn.dancingsnow.neoecoae.api.me;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimSink;

/**
 * Binary compatibility base for AE2 Lightning Tech 2.1 and other integrations.
 *
 * <p>The implementation moved to {@code crafting.execution.ECOCraftingCPU}; the
 * old package name remains in the type hierarchy for existing integrations.
 * AE2 Lightning Tech targets this class with a Mixin invoker for {@link #markDirty()}.</p>
 */
@Deprecated(forRemoval = false)
public abstract class ECOCraftingCPU implements ICraftingCPU {
    protected ECOCraftingCPU() {
    }

    /** The grid this CPU belongs to. */
    public abstract IGrid getGrid();

    /**
     * Stable output-claim boundary for integrations that claim crafting outputs.
     *
     * <p>This method is declared on the legacy base class itself for binary compatibility,
     * even though the live implementation is the execution
     * subclass. The subclass returns its existing logic-backed sink.</p>
     */
    public abstract ECOCraftingOutputClaimSink getOutputClaimSink();

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
