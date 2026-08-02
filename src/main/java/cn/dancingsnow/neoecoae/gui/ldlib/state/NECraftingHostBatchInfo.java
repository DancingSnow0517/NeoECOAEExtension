package cn.dancingsnow.neoecoae.gui.ldlib.state;

/** Per-host batch capacity displayed by the crafting host statistics tooltip. */
public record NECraftingHostBatchInfo(boolean highEnergy, int threadCount, long maxBatchPerThread) {}
