package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.DelegatingMEInventoryAccessor;
import cn.dancingsnow.neoecoae.storage.StorageListingDelta;
import cn.dancingsnow.neoecoae.storage.StorageListingVersion;
import java.util.function.ObjLongConsumer;
import org.spongepowered.asm.mixin.Mixin;

/** Only AE2's exact transparent class is eligible; unknown overriding subclasses stay unversioned. */
@Mixin(value = DelegatingMEInventory.class, remap = false)
public abstract class DelegatingStorageListingMixin implements StorageListingDelta {
    private MEStorage neoecoae$lastDelegate;
    private Object neoecoae$lastDelegateIdentity;
    private Object neoecoae$viewIdentity = new Object();

    private MEStorage neoecoae$delegate() { return ((DelegatingMEInventoryAccessor)this).neoecoae$getDelegate(); }

    @Override public long neoecoae$listingVersion() {
        return ((Object)this).getClass() == DelegatingMEInventory.class && neoecoae$delegate() instanceof StorageListingVersion source
            ? source.neoecoae$listingVersion() : UNKNOWN;
    }

    @Override public Object neoecoae$listingIdentity() {
        if (((Object)this).getClass() != DelegatingMEInventory.class) return neoecoae$viewIdentity;
        MEStorage delegate=neoecoae$delegate();
        Object identity=delegate instanceof StorageListingVersion source ? source.neoecoae$listingIdentity() : delegate;
        if(delegate!=neoecoae$lastDelegate || identity!=neoecoae$lastDelegateIdentity) {
            neoecoae$lastDelegate=delegate;neoecoae$lastDelegateIdentity=identity;neoecoae$viewIdentity=new Object();
        }
        return neoecoae$viewIdentity;
    }

    @Override public boolean neoecoae$visitListingChanges(long since,long current,ObjLongConsumer<AEKey> visitor) {
        return ((Object)this).getClass() == DelegatingMEInventory.class && neoecoae$delegate() instanceof StorageListingDelta source
            && since >= 0 && current >= 0 && source.neoecoae$listingVersion() == current
            && source.neoecoae$visitListingChanges(since,current,visitor);
    }
}
