package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;
import java.util.function.ObjLongConsumer;

/**
 * Optional incremental listing for a versioned mount. Lets the network cache update only the keys that changed
 * since its previous listing instead of listing the whole inventory again.
 */
public interface StorageListingDelta extends StorageListingVersion {
    /**
     * Reports every key whose listed amount may differ between the listing at version {@code since} and the
     * current listing, together with its current listed amount (0 when it is no longer listed). {@code current} is
     * the value {@link #neoecoae$listingVersion()} just returned. Keys reported with an unchanged amount are allowed.
     * Returns false, without calling {@code visitor}, when the changes are not known; the caller then lists in full.
     */
    boolean neoecoae$visitListingChanges(long since, long current, ObjLongConsumer<AEKey> visitor);
}
