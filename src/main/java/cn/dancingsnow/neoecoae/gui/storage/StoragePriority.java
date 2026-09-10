package cn.dancingsnow.neoecoae.gui.storage;

public final class StoragePriority {
    private StoragePriority() {}

    public static int adjust(int current, int delta) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) current + delta));
    }
}
