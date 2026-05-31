package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.menu.me.crafting.CraftingCPUMenu;
import cn.dancingsnow.neoecoae.compat.ae2.NeoECOCraftingCpuMenuBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin120 {

    @Inject(method = "removed", at = @At("TAIL"), require = 0)
    private void neoecoae$onRemoved(Player player, CallbackInfo ci) {
        Object self = this;
        if (self instanceof CraftingCPUMenu && self instanceof NeoECOCraftingCpuMenuBridge bridge) {
            bridge.neoecoae$cleanupEcoCpuListener();
        }
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"), require = 0)
    private void neoecoae$onBroadcastChanges(CallbackInfo ci) {
        Object self = this;
        if (self instanceof CraftingCPUMenu && self instanceof NeoECOCraftingCpuMenuBridge bridge) {
            bridge.neoecoae$broadcastEcoCpuChanges();
        }
    }
}
