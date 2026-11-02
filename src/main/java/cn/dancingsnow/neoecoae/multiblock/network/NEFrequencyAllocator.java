package cn.dancingsnow.neoecoae.multiblock.network;

import java.util.Collection;

/** Assigns stable logical-network frequencies to newly formed hosts. */
public final class NEFrequencyAllocator {
    public static final int UNASSIGNED = -1;
    public static final int FREQUENCY_COUNT = 4;
    public static final int DEFAULT_FREQUENCY = 1;
    public static final int HOST_LIMIT = 8;

    private NEFrequencyAllocator() {
    }

    public static int allocate(Collection<Integer> assignedFrequencies) {
        int[] counts = new int[FREQUENCY_COUNT];
        for (Integer frequency : assignedFrequencies) {
            if (frequency != null && frequency >= 1 && frequency <= FREQUENCY_COUNT) {
                counts[frequency - 1]++;
            }
        }
        for (int frequency = 1; frequency <= FREQUENCY_COUNT; frequency++) {
            if (counts[frequency - 1] > 0 && counts[frequency - 1] < HOST_LIMIT) {
                return frequency;
            }
        }

        int lastUsedFrequency = -1;
        for (int frequency = 1; frequency <= FREQUENCY_COUNT; frequency++) {
            if (counts[frequency - 1] > 0) {
                lastUsedFrequency = frequency;
            }
        }
        if (lastUsedFrequency < 0) {
            return DEFAULT_FREQUENCY;
        }
        for (int offset = 1; offset <= FREQUENCY_COUNT; offset++) {
            int frequency = Math.floorMod(lastUsedFrequency - 1 + offset, FREQUENCY_COUNT) + 1;
            if (counts[frequency - 1] == 0) {
                return frequency;
            }
        }

        int leastPopulated = 1;
        for (int frequency = 2; frequency <= FREQUENCY_COUNT; frequency++) {
            if (counts[frequency - 1] < counts[leastPopulated - 1]) {
                leastPopulated = frequency;
            }
        }
        return leastPopulated;
    }

    public static int normalize(int frequency) {
        return Math.floorMod(frequency - 1, FREQUENCY_COUNT) + 1;
    }
}
