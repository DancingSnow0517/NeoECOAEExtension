package cn.dancingsnow.neoecoae.api.storage;

/** Priority rules shared by storage hosts and addon drives. */
public final class StoragePriority {
    private StoragePriority() {}

    /** Reserve the upper slot for marked bulk cells, including at the integer ceiling. */
    public static int mountPriority(int hostPriority, boolean markedBulk) {
        return markedBulk ? adjust(hostPriority, 1) : Math.min(hostPriority, Integer.MAX_VALUE - 1);
    }

    /** Adds a cell-local offset to the host baseline, with integer saturation. */
    public static int mountPriority(int hostPriority, int cellPriority) {
        return adjust(hostPriority, cellPriority);
    }

    public static int adjust(int current, int delta) {
        return Math.clamp((long) current + delta, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
