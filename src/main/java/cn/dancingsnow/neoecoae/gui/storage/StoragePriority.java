package cn.dancingsnow.neoecoae.gui.storage;

/** @deprecated Use {@link cn.dancingsnow.neoecoae.api.storage.StoragePriority}. */
@Deprecated(forRemoval = false)
public final class StoragePriority {
    private StoragePriority() {}

    public static int mountPriority(int hostPriority, boolean markedBulk) {
        return cn.dancingsnow.neoecoae.api.storage.StoragePriority.mountPriority(hostPriority, markedBulk);
    }

    public static int mountPriority(int hostPriority, int cellPriority) {
        return cn.dancingsnow.neoecoae.api.storage.StoragePriority.mountPriority(hostPriority, cellPriority);
    }

    public static int adjust(int current, int delta) {
        return cn.dancingsnow.neoecoae.api.storage.StoragePriority.adjust(current, delta);
    }
}
