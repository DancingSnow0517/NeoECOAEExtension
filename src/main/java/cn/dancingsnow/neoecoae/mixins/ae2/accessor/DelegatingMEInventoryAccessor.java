package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DelegatingMEInventory.class)
public interface DelegatingMEInventoryAccessor {
    @Accessor("delegate")
    MEStorage neoecoae$getDelegate();
}
