package cn.dancingsnow.neoecoae.api.me.provider;

/** Changes whenever the crafting service mounts, removes or refreshes providers. */
public interface ECOCraftingProviderRevision {
    long neoecoae$getProviderRevision();

    /** False while a provider refresh has yet to rebuild the advertised pattern list. */
    default boolean neoecoae$isProviderSnapshotStable() { return false; }
}
