package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import cn.dancingsnow.neoecoae.storage.transfer.StorageBusTransferFilter;
import org.spongepowered.asm.mixin.Mixin;

/** Tags the exact inventory mounted by storage buses, preserving their access and priority rules. */
@Mixin(targets = "appeng.parts.storagebus.StorageBusPart$StorageBusInventory", remap = false)
public abstract class StorageBusInventoryMixin implements StorageBusTransferFilter.StorageBusInventory {}
