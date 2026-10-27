package cn.dancingsnow.neoecoae.impl.storage;

/**
 * Optional change marker for a mounted inventory. Equal versions guarantee an identical
 * {@code getAvailableStacks} result, so AE2's network cache may reuse the previous listing.
 */
public interface StorageListingVersion {
    /** The listing may have changed; the inventory must be listed again. */
    long UNKNOWN = Long.MIN_VALUE;

    long neoecoae$listingVersion();
}
