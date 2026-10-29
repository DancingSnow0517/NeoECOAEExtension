package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;
import java.util.function.Consumer;

/**
 * Optional incremental listing for a {@link StorageListingVersion} inventory whose listing changes in place, so a
 * network cache can update only the keys that changed instead of listing the whole inventory again.
 * Several readers may hold different versions at once.
 */
public interface StorageListingDelta extends StorageListingVersion {
    /**
     * Passes every key whose listed amount may differ between listing version {@code since} and {@code current}
     * (the inventory's present version); a key may be passed more than once. Returns {@code false}, possibly after
     * passing some keys, when those changes cannot be enumerated: the caller must then list the inventory fully.
     */
    boolean neoecoae$visitListingChanges(long since, long current, Consumer<AEKey> changed);

    /** The amount {@code getAvailableStacks} would currently list for {@code key}, or 0 when it is not listed. */
    long neoecoae$listedAmount(AEKey key);
}
