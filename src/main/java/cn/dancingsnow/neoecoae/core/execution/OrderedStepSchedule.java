package cn.dancingsnow.neoecoae.core.execution;

/** Immutable repeat layout. The adapter owns/persists the mutable arrays and supplies them by reference. */
public final class OrderedStepSchedule {
    private final long[] counts;
    private final int[] widths;

    public OrderedStepSchedule(long[] counts, int[] widths) {
        if (counts.length != widths.length) throw new IllegalArgumentException("Step layout shape changed");
        this.counts = counts.clone();
        this.widths = widths.clone();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] <= 0 || widths[i] <= 0 || widths[i] > i + 1) {
                throw new IllegalArgumentException("Invalid repeated step layout");
            }
        }
    }

    /** Advances only completed witness steps, consuming accepted future copies before restarting a lap. */
    public int advance(int cursor, long[] remaining, long[] laps, long[] ahead) {
        while (cursor < counts.length && remaining[cursor] == 0L) {
            int end = cursor;
            if (laps[end] > 1L) {
                int start = end - widths[end] + 1;
                long skip = laps[end] - 1L;
                for (int i = start; i <= end; i++) skip = Math.min(skip, ahead[i] / counts[i]);
                for (int i = start; i <= end; i++) ahead[i] -= skip * counts[i];
                laps[end] -= skip;
                if (laps[end] == 1L) { cursor++; continue; }
                laps[end]--;
                for (int i = start; i <= end; i++) {
                    long consumed = Math.min(ahead[i], counts[i]);
                    remaining[i] = counts[i] - consumed;
                    ahead[i] -= consumed;
                }
                cursor = start;
            } else {
                cursor++;
            }
        }
        return cursor;
    }

    public long allowance(int index, int groupEnd, long[] remaining, long[] laps, long[] ahead) {
        long current = remaining[index];
        if (groupEnd < 0) return current;
        long future = Math.multiplyExact(laps[groupEnd] - 1L, counts[index]);
        return Math.addExact(current, future - ahead[index]);
    }
}
