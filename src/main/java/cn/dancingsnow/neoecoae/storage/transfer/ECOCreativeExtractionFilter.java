package cn.dancingsnow.neoecoae.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.DelegatingMEInventoryAccessor;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.SupplierStorageAccessor;

import java.util.function.LongSupplier;

/** Identifies creative inventories before an IO transfer can debit or void physical stock. */
public final class ECOCreativeExtractionFilter {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    /** Implemented by creative inventories, including AE2's package-private creative cell. */
    public interface CreativeStorage {}

    private ECOCreativeExtractionFilter() {}

    public static boolean isActive(Actionable mode) {
        return mode == Actionable.MODULATE && Boolean.TRUE.equals(ACTIVE.get());
    }

    /** Every IO transfer excludes creative sinks; filtering creative sources remains optional. */
    public static long transfer(boolean ignoreCreativeInput, LongSupplier action) {
        Boolean previous = ACTIVE.get();
        ACTIVE.set(ignoreCreativeInput || Boolean.TRUE.equals(previous));
        try {
            return action.getAsLong();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static long extract(MEStorage storage, AEKey key, long amount, IActionSource source) {
        return transfer(true, () -> extractSource(storage, key, Actionable.MODULATE,
            () -> storage.extract(key, amount, Actionable.MODULATE, source)));
    }

    /** Called at each NetworkStorage mount, only while a filtered interface input is active. */
    public static long extractSource(MEStorage storage, AEKey key, Actionable mode, LongSupplier extraction) {
        if (isActive(mode) && isCreativeStorage(storage)) return 0L;
        return extraction.getAsLong();
    }

    public static boolean excludesInsert(MEStorage storage) {
        return ACTIVE.get() != null && isCreativeStorage(storage);
    }

    private static boolean isCreativeStorage(MEStorage storage) {
        while (storage != null) {
            if (storage instanceof CreativeStorage) return true;
            if (storage instanceof DelegatingMEInventoryAccessor wrapper) storage = wrapper.neoecoae$getDelegate();
            else if (storage instanceof SupplierStorageAccessor supplier) storage = supplier.neoecoae$getDelegate();
            else return false;
        }
        return false;
    }
}
