package cn.dancingsnow.neoecoae.multiblock.network;

import java.util.Collection;

/** Assigns stable logical-network frequencies to newly formed hosts. */
public final class NEFrequencyAllocator {
    public static final int UNASSIGNED = -1;
    public static final int FREQUENCY_COUNT = 16;
    public static final int HOST_LIMIT = 8;

    private NEFrequencyAllocator() {}

    /**
     * Reuses a frequency with room for another host, then allocates the next
     * unused frequency. The bounded range is shared by computation and
     * crafting hosts on the same physical AE2 grid.
     */
    public static int allocate(Collection<Integer> assignedFrequencies) {
        int[] counts = new int[FREQUENCY_COUNT];
        for (Integer frequency : assignedFrequencies) {
            if (frequency != null && frequency >= 0 && frequency < FREQUENCY_COUNT) {
                counts[frequency]++;
            }
        }
        for (int frequency = 0; frequency < FREQUENCY_COUNT; frequency++) {
            if (counts[frequency] > 0 && counts[frequency] < HOST_LIMIT) {
                return frequency;
            }
        }

        int lastUsedFrequency = -1;
        for (int frequency = 0; frequency < FREQUENCY_COUNT; frequency++) {
            if (counts[frequency] > 0) {
                lastUsedFrequency = frequency;
            }
        }
        for (int offset = 1; offset <= FREQUENCY_COUNT; offset++) {
            int frequency = Math.floorMod(lastUsedFrequency + offset, FREQUENCY_COUNT);
            if (counts[frequency] == 0) {
                return frequency;
            }
        }

        int leastPopulated = 0;
        for (int frequency = 1; frequency < FREQUENCY_COUNT; frequency++) {
            if (counts[frequency] < counts[leastPopulated]) {
                leastPopulated = frequency;
            }
        }
        return leastPopulated;
    }

    public static int normalize(int frequency) {
        return Math.floorMod(frequency, FREQUENCY_COUNT);
    }
}
