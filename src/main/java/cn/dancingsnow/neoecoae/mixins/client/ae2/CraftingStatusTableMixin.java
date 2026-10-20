package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.me.crafting.CraftingStatusTableRenderer;
import appeng.menu.me.crafting.CraftingStatusEntry;
import cn.dancingsnow.neoecoae.api.me.menu.ECOBigOrderStatusHost;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
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
        if (amount == null && exactStored(entry) == null && exactActive(entry) == null) return;
        var lines = new java.util.ArrayList<Component>();
        if (entry.getStoredAmount() > 0) lines.add(appeng.core.localization.GuiText.FromStorage.text(
            formatExact(exactStored(entry), entry, entry.getStoredAmount(), false)));
        if (entry.getActiveAmount() > 0) lines.add(appeng.core.localization.GuiText.Crafting.text(
            formatExact(exactActive(entry), entry, entry.getActiveAmount(), false)));
        if (amount != null && amount.signum() > 0) lines.add(appeng.core.localization.GuiText.Scheduled.text(
            cn.dancingsnow.neoecoae.gui.common.HostText.ae2Amount(amount)));
        cir.setReturnValue(lines);
    }
    @Inject(method = "getEntryTooltip(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void exactTooltip(CraftingStatusEntry entry, CallbackInfoReturnable<List<Component>> cir) {
        var amount = exact(entry);
        if (amount == null && exactStored(entry) == null && exactActive(entry) == null) return;
        var lines = new java.util.ArrayList<>(appeng.api.client.AEKeyRendering.getTooltip(entry.getWhat()));
        if (entry.getStoredAmount() > 0) lines.add(appeng.core.localization.GuiText.FromStorage.text(
            formatExact(exactStored(entry), entry, entry.getStoredAmount(), true)));
        if (entry.getActiveAmount() > 0) lines.add(appeng.core.localization.GuiText.Crafting.text(
            formatExact(exactActive(entry), entry, entry.getActiveAmount(), true)));
        if (amount != null && amount.signum() > 0) lines.add(appeng.core.localization.GuiText.Scheduled.text(
            BigNumberFormatter.format(amount, 1, true)));
        cir.setReturnValue(lines);
    }
    @org.spongepowered.asm.mixin.Unique
    private static String formatExact(java.math.BigInteger amount, CraftingStatusEntry entry, long fallback, boolean full) {
        if (amount == null) return entry.getWhat().formatAmount(fallback,
            full ? appeng.api.stacks.AmountFormat.FULL : appeng.api.stacks.AmountFormat.SLOT);
        return full ? BigNumberFormatter.format(amount, 1, true)
            : cn.dancingsnow.neoecoae.gui.common.HostText.ae2Amount(amount);
    }
    @org.spongepowered.asm.mixin.Unique
    private static java.math.BigInteger exactStored(CraftingStatusEntry entry) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.containerMenu instanceof ECOBigOrderStatusHost host
            ? host.neoecoae$getExactStored(entry.getWhat()) : null;
    }
    @org.spongepowered.asm.mixin.Unique
    private static java.math.BigInteger exactActive(CraftingStatusEntry entry) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.containerMenu instanceof ECOBigOrderStatusHost host
            ? host.neoecoae$getExactActive(entry.getWhat()) : null;
    }
    @org.spongepowered.asm.mixin.Unique
    private static java.math.BigInteger exact(CraftingStatusEntry entry) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.containerMenu instanceof ECOBigOrderStatusHost host
            ? host.neoecoae$getExactPending(entry.getWhat()) : null;
    }
}
