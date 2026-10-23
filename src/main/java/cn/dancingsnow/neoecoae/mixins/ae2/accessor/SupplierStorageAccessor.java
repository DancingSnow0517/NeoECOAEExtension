package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.api.storage.MEStorage;
import appeng.api.storage.SupplierStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = SupplierStorage.class, remap = false)
public interface SupplierStorageAccessor {
    @Invoker(value = "getDelegate", remap = false)
    MEStorage neoecoae$getDelegate();
}
