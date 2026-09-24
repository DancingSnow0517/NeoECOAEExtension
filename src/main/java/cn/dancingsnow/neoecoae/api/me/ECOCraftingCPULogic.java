package cn.dancingsnow.neoecoae.api.me;

/**
 * Binary compatibility target for integrations compiled against the original NeoECOAE package layout.
 *
 * <p>ExtendedAE Plus applies accessors to this exact class to inspect the active job and finish virtual
 * crafting. The implementation remains in {@code crafting.execution}; its subclass mirrors the legacy
 * job reference so those accessors continue to observe the live state.</p>
 */
@Deprecated(forRemoval = false)
public abstract class ECOCraftingCPULogic {
    protected ExecutingCraftingJob job;

    protected ECOCraftingCPULogic() {
    }

    protected abstract void finishJob(boolean success);
}
