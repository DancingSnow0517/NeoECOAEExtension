package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import appeng.me.storage.DriveWatcher;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.prioritylist.DefaultPriorityList;
import appeng.util.prioritylist.IPartitionList;
import cn.dancingsnow.neoecoae.storage.StorageListingDelta;
import cn.dancingsnow.neoecoae.storage.StorageListingVersion;
import java.util.function.ObjLongConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2 15.4.11 read-view bridge. Mutable/nonempty partition rules conservatively keep full listing. */
@Mixin(value = MEInventoryHandler.class, remap = false)
public abstract class MEInventoryHandlerListingMixin extends DelegatingMEInventory implements StorageListingDelta {
    @Shadow private boolean filterAvailableContents;
    @Shadow private boolean allowExtraction;
    @Shadow private boolean gettingAvailableContent;
    @Shadow private IPartitionList partitionList;
    private MEStorage neoecoae$source;
    private Object neoecoae$sourceIdentity;
    private Object neoecoae$configuration = new Object();

    protected MEInventoryHandlerListingMixin(MEStorage delegate) { super(delegate); }

    private boolean neoecoae$knownView() {
        Class<?> type=getClass();
        return (type==MEInventoryHandler.class || type==DriveWatcher.class)
            && (!filterAvailableContents || partitionList==DefaultPriorityList.INSTANCE);
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
        // This path is transparent; no nonempty filter is bypassed.
        gettingAvailableContent=true;
        try { return source.neoecoae$visitListingChanges(since,current,visitor); }
        finally { gettingAvailableContent=false; }
    }
}
