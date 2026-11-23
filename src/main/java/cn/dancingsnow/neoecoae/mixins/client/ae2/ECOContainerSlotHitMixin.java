package cn.dancingsnow.neoecoae.mixins.client.ae2;

import cn.dancingsnow.neoecoae.client.gui.ECOModularContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ECOContainerSlotHitMixin {
    @Inject(method = "getHoveredSlot(DD)Lnet/minecraft/world/inventory/Slot;", at = @At("HEAD"), cancellable = true)
    private void neoecoae$resolveModularSlot(double mouseX, double mouseY, CallbackInfoReturnable<Slot> result) {
        if ((Object) this instanceof ECOModularContainerScreen screen) {
            Slot slot = screen.hitModularItemSlot(mouseX, mouseY);
            if (slot != null) result.setReturnValue(slot);
        }
    }
}
