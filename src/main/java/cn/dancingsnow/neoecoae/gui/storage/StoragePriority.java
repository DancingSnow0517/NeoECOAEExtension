package cn.dancingsnow.neoecoae.gui.storage;

public final class StoragePriority {
    private StoragePriority() {
    }

    /** Reserve the upper slot for marked bulk cells, including at the integer ceiling. */
    public static int mountPriority(int hostPriority, boolean markedBulk) {
        return markedBulk ? adjust(hostPriority, 1) : Math.min(hostPriority, Integer.MAX_VALUE - 1);
    }

    public static int adjust(int current, int delta) {
        long adjusted = (long) current + delta;
        if (adjusted > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (adjusted < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) adjusted;
    }
}
