package cn.dancingsnow.neoecoae.storage.transfer;

import appeng.api.storage.MEStorage;
import java.util.function.LongSupplier;

/** Limits storage-bus exclusion to the current ECO interface transfer, including nested networks. */
public final class StorageBusTransferFilter {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private StorageBusTransferFilter() {}

    /** Implemented by AE2's mounted storage-bus wrapper, rather than the external container itself. */
    public interface StorageBusInventory {}

    public static boolean excludes(MEStorage storage) {
        return isActive() && storage instanceof StorageBusInventory;
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static long transfer(boolean excludeStorageBuses, LongSupplier action) {
        Boolean previous = ACTIVE.get();
        if (excludeStorageBuses) ACTIVE.set(true);
        try {
            return action.getAsLong();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }
}
