package cn.dancingsnow.neoecoae.api.me.provider;

/** Optional capacity contract for providers that can safely accept parallel ordinary crafts. */
public interface ECOParallelCraftingProvider {
    int eco$getAvailableParallelSlots();
}
