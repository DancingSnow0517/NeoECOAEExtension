package cn.dancingsnow.neoecoae.gui;

public final class StoragePriority {
    private StoragePriority() {
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
