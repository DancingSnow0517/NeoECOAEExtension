package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.MEInventoryHandler;
import cn.dancingsnow.neoecoae.storage.transfer.StorageBusTransferFilter;
import org.spongepowered.asm.mixin.Mixin;

/** Tags the exact inventory mounted by storage buses, preserving their access and priority rules. */
@Mixin(targets = "appeng.parts.storagebus.StorageBusPart$StorageBusInventory", remap = false)
public abstract class StorageBusInventoryMixin extends MEInventoryHandler implements StorageBusTransferFilter.StorageBusInventory {
    protected StorageBusInventoryMixin(MEStorage inventory) { super(inventory); }

    // These methods are inherited in AE2's bus wrapper. Adding the overrides confines the
    // transfer-scope check to buses and preserves the original handler's filters and voiding.
    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return StorageBusTransferFilter.isActive() ? 0 : super.insert(key, amount, mode, source);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        return StorageBusTransferFilter.isActive() ? 0 : super.extract(key, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!StorageBusTransferFilter.isActive()) super.getAvailableStacks(out);
    }
}
