package cn.dancingsnow.neoecoae.mixins.sophisticated;

import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOSophisticatedHandlerBridge;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOSophisticatedMutationBatch;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOSophisticatedSourceRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler", remap = false)
public abstract class InventoryHandlerMixin implements ECOSophisticatedHandlerBridge {
    @Shadow public abstract ItemStack extractItem(ItemStack stack, boolean simulate);
    @Shadow public abstract ItemStack getStackInSlot(int slot);
    @Shadow public abstract int getSlots();
    @Shadow public abstract void saveInventory();

    @WrapOperation(
        method = "onContentsChanged",
        at = @At(value = "INVOKE",
            target = "Lnet/p3pp3rf1y/sophisticatedcore/inventory/InventoryHandler;saveInventory()V"),
        require = 0
    )
    private void neoecoae$deferSave(InventoryHandler handler, Operation<Void> original) {
        if (!ECOSophisticatedMutationBatch.deferSave(handler)) original.call(handler);
    }

    @Inject(method = "onContentsChanged", at = @At("TAIL"), require = 0)
    private void neoecoae$publishSlotChange(int slot, CallbackInfo ci) {
        ECOSophisticatedSourceRegistry.onSlotChanged(this, slot, getStackInSlot(slot));
    }

    @Override
    public ItemStack neoecoae$extractMatching(ItemStack requested, boolean simulate) {
        return extractItem(requested, simulate);
    }

    @Override
    public int neoecoae$getSlots() {
        return getSlots();
    }

    @Override
    public ItemStack neoecoae$getStackInSlot(int slot) {
        return getStackInSlot(slot);
    }

    @Override
    public void neoecoae$saveInventory() {
        saveInventory();
    }
}
