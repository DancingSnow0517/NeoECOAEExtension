package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import appeng.me.storage.DriveWatcher;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.prioritylist.DefaultPriorityList;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.prioritylist.PrecisePriorityList;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PrecisePriorityListAccessor;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import cn.dancingsnow.neoecoae.storage.StorageListingDelta;
import cn.dancingsnow.neoecoae.storage.StorageListingVersion;
import java.util.function.ObjLongConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2 19.2.18 read-view bridge, with bounded observation of mutable precise filters. */
@Mixin(value = MEInventoryHandler.class, remap = false)
public abstract class MEInventoryHandlerListingMixin extends DelegatingMEInventory implements StorageListingDelta {
    @Shadow private boolean filterAvailableContents;
    @Shadow private boolean allowExtraction;
    @Shadow private boolean gettingAvailableContent;
    @Shadow private IPartitionList partitionList;
    @Shadow protected abstract boolean canExtract(AEKey key);
    private MEStorage neoecoae$source;
    private Object neoecoae$sourceIdentity;
    private Object neoecoae$configuration = new Object();
    private ObjectOpenHashSet<AEKey> neoecoae$filterKeys;
    private boolean neoecoae$filterEmpty;

    protected MEInventoryHandlerListingMixin(MEStorage delegate) { super(delegate); }

    private boolean neoecoae$knownView() {
        Class<?> type = getClass();
        if (type != MEInventoryHandler.class && type != DriveWatcher.class) return false;
        if (!filterAvailableContents) return true;
        // AE2's filtered branch uses the delegate's no-arg listing. DelegatingMEInventory overrides
        // that overload and may bypass its own filtered view. Only these direct ECO sources have
        // a verified identical no-arg/consumer listing; nested and unknown views keep native queries.
        Class<?> sourceType = getDelegate().getClass();
        if (sourceType != cn.dancingsnow.neoecoae.storage.ECOStorageCell.class
                && sourceType != cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage.class) return false;
        if (partitionList == DefaultPriorityList.INSTANCE) return true;
        if (partitionList.getClass() != PrecisePriorityList.class
                || !(partitionList instanceof PrecisePriorityListAccessor accessor)) return false;
        // The builder and callers can mutate the same KeyCounter without calling setPartitionList.
        // Observe actual positive membership, not a hash/XOR stamp. Large/fuzzy/custom filters fall back.
        var keys = accessor.neoecoae$partitionKeys();
        boolean empty = partitionList.isEmpty();
        boolean same = neoecoae$filterKeys != null && empty == neoecoae$filterEmpty;
        int visited = 0, positive = 0;
        for (var entry : keys) {
            if (++visited > 64) return false;
            if (entry.getLongValue() > 0) {
                positive++;
                if (same && !neoecoae$filterKeys.contains(entry.getKey())) same = false;
            }
        }
        if (!same || positive != neoecoae$filterKeys.size()) {
            var next = new ObjectOpenHashSet<AEKey>(positive);
            for (var entry : keys) if (entry.getLongValue() > 0) next.add(entry.getKey());
            neoecoae$filterKeys = next;
            neoecoae$filterEmpty = empty;
            neoecoae$configuration = new Object();
        }
        return true;
    }

    @Inject(method={"setAllowExtraction","setAllowInsertion","setWhitelist","setPartitionList","setExtractFiltering","setVoidOverflow"},at=@At("RETURN"))
    private void neoecoae$invalidateView(CallbackInfo ci) { neoecoae$configuration=new Object(); }

    @Override public Object neoecoae$listingIdentity() {
        if (!neoecoae$knownView()) return neoecoae$configuration;
        MEStorage source=getDelegate();
        Object identity=source instanceof StorageListingVersion versioned ? versioned.neoecoae$listingIdentity() : source;
        if(source!=neoecoae$source || identity!=neoecoae$sourceIdentity) {
            neoecoae$source=source;neoecoae$sourceIdentity=identity;neoecoae$configuration=new Object();
        }
        return neoecoae$configuration;
    }

    @Override public long neoecoae$listingVersion() {
        if(!neoecoae$knownView() || gettingAvailableContent) return UNKNOWN;
        if(filterAvailableContents && !allowExtraction) return -1;
        return getDelegate() instanceof StorageListingVersion source ? source.neoecoae$listingVersion() : UNKNOWN;
    }

    @Override public boolean neoecoae$visitListingChanges(long since,long current,ObjLongConsumer<AEKey> visitor) {
        if(!neoecoae$knownView() || gettingAvailableContent || since<0 || current<0
            || filterAvailableContents && !allowExtraction || !(getDelegate() instanceof StorageListingDelta source)
            || source.neoecoae$listingVersion()!=current) return false;
        // Changes are absolute quantities. A filtered-out key contributes zero, including deletions.
        gettingAvailableContent=true;
        try {
            return source.neoecoae$visitListingChanges(since,current,
                (key, amount) -> visitor.accept(key, !filterAvailableContents || canExtract(key) ? amount : 0));
        }
        finally { gettingAvailableContent=false; }
    }

    @Override public boolean neoecoae$visitListingChanges(long since, long current,
            java.util.function.Consumer<AEKey> visitor) {
        return neoecoae$visitListingChanges(since, current, (key, amount) -> visitor.accept(key));
    }

    @Override public long neoecoae$listedAmount(AEKey key) {
        if (neoecoae$knownView() && getDelegate() instanceof StorageListingDelta source) {
            return !filterAvailableContents || allowExtraction && canExtract(key) ? source.neoecoae$listedAmount(key) : 0;
        }
        var listing = new appeng.api.stacks.KeyCounter();
        getAvailableStacks(listing);
        return listing.get(key);
    }
}
