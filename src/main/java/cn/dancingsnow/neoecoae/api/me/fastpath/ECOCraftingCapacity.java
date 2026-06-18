package cn.dancingsnow.neoecoae.api.me.fastpath;

public final class ECOCraftingCapacity {
    private ECOCraftingCapacity() {}

    public static int maxInFlightCrafts(int threadCount, int structureLength, int threadSlotsPerLength) {
        if (threadCount <= 0 || structureLength <= 0 || threadSlotsPerLength <= 0) {
            return 0;
        }
        long structureLimit = (long) structureLength * threadSlotsPerLength;
        return (int) Math.min(threadCount, Math.min(Integer.MAX_VALUE, structureLimit));
    }

    public static int availableCraftSlots(int maxInFlightCrafts, int runningCrafts) {
        return Math.max(0, maxInFlightCrafts - Math.max(0, runningCrafts));
    }
}
