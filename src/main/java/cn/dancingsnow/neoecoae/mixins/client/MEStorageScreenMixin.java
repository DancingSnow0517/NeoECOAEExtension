package cn.dancingsnow.neoecoae.mixins.client;

import appeng.api.client.AEKeyRendering;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.menu.me.common.GridInventoryEntry;
import cn.dancingsnow.neoecoae.api.me.ECOExactStorageMenu;
import cn.dancingsnow.neoecoae.util.ExactAmountFormatter;
import java.math.BigInteger;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Owns the exact entry rendering before downstream long-to-infinity formatters run. */
@Mixin(value = MEStorageScreen.class, remap = false, priority = 2000)
public abstract class MEStorageScreenMixin {
    @Shadow
    @Final
    protected Repo repo;

    @Shadow
    protected abstract boolean isViewOnlyCraftable();

    @Unique private BigInteger neoecoae$exact(GridInventoryEntry entry) {
        var screen = (MEStorageScreen<?>) (Object) this;
        return entry != null && screen.getMenu() instanceof ECOExactStorageMenu menu
                ? menu.neoecoae$getExactAmounts().get(entry.getWhat())
                : null;
    }

    // AE2 15.4.x ships this method under its runtime SRG name.
    @Inject(method = "m_280092_", at = @At("HEAD"), cancellable = true)
    private void neoecoae$renderExact(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (!(slot instanceof RepoSlot repoSlot) || !repo.hasPower() || isViewOnlyCraftable()) return;
        var entry = repoSlot.getEntry();
        BigInteger amount = neoecoae$exact(entry);
        if (amount == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        AEKeyRendering.drawInGui(minecraft, graphics, slot.x, slot.y, entry.getWhat());
        StackSizeRenderer.renderSizeLabel(
                graphics,
                minecraft.font,
                slot.x,
                slot.y,
                ExactAmountFormatter.compact(amount, entry.getWhat().getAmountPerUnit()));
        ci.cancel();
    }

    @Inject(method = "renderGridInventoryEntryTooltip", at = @At("HEAD"), cancellable = true)
    private void neoecoae$exactTooltip(GuiGraphics graphics, GridInventoryEntry entry, int x, int y, CallbackInfo ci) {
        BigInteger amount = neoecoae$exact(entry);
        if (amount == null || !repo.hasPower()) return;
        var lines = new ArrayList<>(AEKeyRendering.getTooltip(entry.getWhat()));
        lines.add(Component.translatable(
                "gui.neoecoae.exact_stored_amount",
                ExactAmountFormatter.full(amount, entry.getWhat().getAmountPerUnit())));
        if (entry.getWhat().getAmountPerUnit() != 1) {
            lines.add(Component.literal(amount.toString() + " (raw)"));
        }
        if (entry.getRequestableAmount() > 0) {
            lines.add(appeng.core.localization.Tooltips.getAmountTooltip(
                    appeng.core.localization.ButtonToolTips.RequestableAmount,
                    entry.getWhat(),
                    entry.getRequestableAmount()));
        }
        if (entry.isCraftable()) lines.add(appeng.core.localization.ButtonToolTips.Craftable.text());
        graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, x, y);
        ci.cancel();
    }
}
