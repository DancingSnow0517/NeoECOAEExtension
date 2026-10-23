package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.api.storage.MEStorage;
import appeng.api.storage.SupplierStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SupplierStorage.class)
public interface SupplierStorageAccessor {
    @Invoker("getDelegate")
    MEStorage neoecoae$getDelegate();
}
