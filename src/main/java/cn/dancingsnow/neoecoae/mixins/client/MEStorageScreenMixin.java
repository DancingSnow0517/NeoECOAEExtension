package cn.dancingsnow.neoecoae.mixins.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.menu.me.common.GridInventoryEntry;
import cn.dancingsnow.neoecoae.api.me.ECOExactStorageMenu;
import cn.dancingsnow.neoecoae.util.ExactAmountFormatter;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.math.BigInteger;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Replaces only amount text; AE2 retains ownership of slot and tooltip rendering. */
@Mixin(value = MEStorageScreen.class, remap = false, priority = 2000)
public abstract class MEStorageScreenMixin {
    @Unique private BigInteger neoecoae$exact(GridInventoryEntry entry) {
        var screen = (MEStorageScreen<?>) (Object) this;
        return entry != null && screen.getMenu() instanceof ECOExactStorageMenu menu
                ? menu.neoecoae$getExactAmounts().get(entry.getWhat())
                : null;
    }

    // This vanilla override uses Mojmap in development and SRG in production.
    @ModifyExpressionValue(
            method = {
                "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V",
                "m_280092_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/stacks/AEKey;formatAmount(JLappeng/api/stacks/AmountFormat;)Ljava/lang/String;"),
            remap = false,
            require = 1,
            allow = 1)
    private String neoecoae$renderExact(String original, @Local GridInventoryEntry entry) {
        BigInteger amount = neoecoae$exact(entry);
        return amount == null
                ? original
                : ExactAmountFormatter.compact(amount, entry.getWhat().getAmountPerUnit());
    }

    @ModifyExpressionValue(
            method = "renderGridInventoryEntryTooltip",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/core/localization/Tooltips;getAmountTooltip(Lappeng/core/localization/ButtonToolTips;Lappeng/api/stacks/AEKey;J)Lnet/minecraft/network/chat/Component;",
                            ordinal = 0),
            require = 1,
            allow = 1)
    private Component neoecoae$exactTooltip(Component original, @Local(argsOnly = true) GridInventoryEntry entry) {
        BigInteger amount = neoecoae$exact(entry);
        return amount == null
                ? original
                : appeng.core.localization.ButtonToolTips.StoredAmount.text(ExactAmountFormatter.full(
                                amount, entry.getWhat().getAmountPerUnit()))
                        .withStyle(appeng.core.localization.Tooltips.MUTED_COLOR);
    }
}
