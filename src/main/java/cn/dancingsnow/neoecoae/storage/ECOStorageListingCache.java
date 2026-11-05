package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.function.Consumer;

/** A private listing owned by one ECO inventory, never by AE2's network or another mod's inventory. */
public final class ECOStorageListingCache {
    private final KeyCounter cached = new KeyCounter();
    private final ObjectOpenHashSet<AEKey> changed = new ObjectOpenHashSet<>();
    private long version = StorageListingVersion.UNKNOWN;
    private boolean inUse;

    /** Callers receive native AE2 copies; mutating a terminal's counter cannot change this cache. */
    public synchronized void append(StorageListingVersion source, KeyCounter output, Consumer<KeyCounter> listing) {
        long current = source.neoecoae$listingVersion();
        if (inUse || current == StorageListingVersion.UNKNOWN) {
            if (!inUse) invalidate();
            listing.accept(output);
            return;
        }
        inUse = true;
        try {
            if (version != current) {
                if (version == StorageListingVersion.UNKNOWN || !(source instanceof StorageListingDelta delta)
                        || !update(delta, current)) {
                    cached.clear();
                    cached.removeEmptySubmaps();
                    listing.accept(cached);
                    cached.removeZeros();
                }
                version = current;
            }
            // addAll clones AE2's own variant groups when output is empty, keeping our cache private.
            if (output.isEmpty()) output.addAll(cached);
            else SaturatingStackAccumulator.addAll(output, cached);
        } catch (RuntimeException | Error failure) {
            invalidate();
            throw failure;
        } finally {
            inUse = false;
        }
    }

    private boolean update(StorageListingDelta source, long current) {
        changed.clear();
        try {
            if (!source.neoecoae$visitListingChanges(version, current, changed::add)) return false;
            boolean removed = false;
            for (AEKey key : changed) {
                long amount = source.neoecoae$listedAmount(key);
                if (amount <= 0) {
                    cached.remove(key);
                    removed = true;
                } else cached.set(key, amount);
            }
            if (removed) cached.removeEmptySubmaps();
            return true;
        } finally {
            boolean large = changed.size() > 4096;
            changed.clear();
            if (large) changed.trim();
        }
    }

    private void invalidate() {
        version = StorageListingVersion.UNKNOWN;
        cached.clear();
        cached.removeEmptySubmaps();
    }
}
