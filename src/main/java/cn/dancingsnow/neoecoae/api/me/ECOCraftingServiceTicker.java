package cn.dancingsnow.neoecoae.api.me;

/** Internal bridge that lets the early tick Mixin run before compatibility mods throttle AE2 bookkeeping. */
public interface ECOCraftingServiceTicker {
    void neoecoae$tickComputationCpusNow();
}
