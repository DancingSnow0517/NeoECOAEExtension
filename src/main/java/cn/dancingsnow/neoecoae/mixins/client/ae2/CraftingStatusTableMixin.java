package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.me.crafting.CraftingStatusTableRenderer;
import appeng.menu.me.crafting.CraftingStatusEntry;
import cn.dancingsnow.neoecoae.api.me.menu.ECOBigOrderStatusHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.network.chat.Component;
import java.util.List;

@Mixin(CraftingStatusTableRenderer.class)
public class CraftingStatusTableMixin {
    @Inject(method = "getEntryDescription(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void exactDescription(CraftingStatusEntry entry, CallbackInfoReturnable<List<Component>> cir) {
        var amount = exact(entry);
        if (amount == null) return;
        var lines = new java.util.ArrayList<Component>();
        if (entry.getStoredAmount() > 0) lines.add(appeng.core.localization.GuiText.FromStorage.text(
            entry.getWhat().formatAmount(entry.getStoredAmount(), appeng.api.stacks.AmountFormat.SLOT)));
        if (entry.getActiveAmount() > 0) lines.add(appeng.core.localization.GuiText.Crafting.text(
            entry.getWhat().formatAmount(entry.getActiveAmount(), appeng.api.stacks.AmountFormat.SLOT)));
        if (amount.signum() > 0) lines.add(appeng.core.localization.GuiText.Scheduled.text(
            cn.dancingsnow.neoecoae.gui.common.HostText.ae2Amount(amount)));
        cir.setReturnValue(lines);
    }
    @Inject(method = "getEntryTooltip(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void exactTooltip(CraftingStatusEntry entry, CallbackInfoReturnable<List<Component>> cir) {
        var amount = exact(entry);
        if (amount == null) return;
        var lines = new java.util.ArrayList<>(appeng.api.client.AEKeyRendering.getTooltip(entry.getWhat()));
        if (entry.getStoredAmount() > 0) lines.add(appeng.core.localization.GuiText.FromStorage.text(
            entry.getWhat().formatAmount(entry.getStoredAmount(), appeng.api.stacks.AmountFormat.FULL)));
        if (entry.getActiveAmount() > 0) lines.add(appeng.core.localization.GuiText.Crafting.text(
            entry.getWhat().formatAmount(entry.getActiveAmount(), appeng.api.stacks.AmountFormat.FULL)));
        if (amount.signum() > 0) lines.add(appeng.core.localization.GuiText.Scheduled.text(
            java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT).format(amount)));
        cir.setReturnValue(lines);
    }
    @org.spongepowered.asm.mixin.Unique
    private static java.math.BigInteger exact(CraftingStatusEntry entry) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.containerMenu instanceof ECOBigOrderStatusHost host
            ? host.neoecoae$getExactPending(entry.getWhat()) : null;
    }
}
