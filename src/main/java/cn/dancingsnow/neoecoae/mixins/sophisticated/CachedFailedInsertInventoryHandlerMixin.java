package cn.dancingsnow.neoecoae.mixins.sophisticated;

import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOSophisticatedHandlerBridge;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.inventory.CachedFailedInsertInventoryHandler", remap = false)
public abstract class CachedFailedInsertInventoryHandlerMixin implements ECOSophisticatedHandlerBridge {
    @Shadow @Final private Supplier<?> wrappedHandlerGetter;

    @Override
    public Object neoecoae$getDelegate() {
        return wrappedHandlerGetter.get();
    }

    @Override
    public ItemStack neoecoae$extractMatching(ItemStack requested, boolean simulate) {
        Object delegate = wrappedHandlerGetter.get();
        return delegate instanceof ECOSophisticatedHandlerBridge bridge
            ? bridge.neoecoae$extractMatching(requested, simulate)
            : ItemStack.EMPTY;
    }
}
