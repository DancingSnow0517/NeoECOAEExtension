package cn.dancingsnow.neoecoae.mixins.terminalbigamount.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.menu.me.common.GridInventoryEntry;
import cn.dancingsnow.neoecoae.terminal.bigamount.ExactAmountClientCache;
import cn.dancingsnow.neoecoae.terminal.bigamount.ExactAmountFormatter;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MEStorageScreen.class)
public abstract class MEStorageScreenExactAmountMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void neoecoae$clearExactAmounts(CallbackInfo ci) {
        var self = (MEStorageScreen<?>) (Object) this;
        ExactAmountClientCache.clear(self.getMenu().containerId);
    }

    @ModifyExpressionValue(method = "renderSlot", at = @At(value = "INVOKE", target =
        "Lappeng/api/stacks/AEKey;formatAmount(JLappeng/api/stacks/AmountFormat;)Ljava/lang/String;"))
    private String neoecoae$renderExactSlotAmount(String original, @Local GridInventoryEntry entry) {
        MEStorageScreen<?> self = (MEStorageScreen<?>) (Object) this;
        var exact = ExactAmountClientCache.get(self.getMenu().containerId, entry.getWhat());
        return exact == null ? original : ExactAmountFormatter.slot(exact);
    }

    @ModifyExpressionValue(method = "renderGridInventoryEntryTooltip", at = @At(value = "INVOKE", target =
        "Lappeng/core/localization/Tooltips;getAmountTooltip(Lappeng/core/localization/ButtonToolTips;Lappeng/api/stacks/AEKey;J)Lnet/minecraft/network/chat/Component;"))
    private net.minecraft.network.chat.Component neoecoae$renderExactTooltip(
            net.minecraft.network.chat.Component original, @Local(argsOnly = true) GridInventoryEntry entry) {
        var self = (MEStorageScreen<?>) (Object) this;
        var exact = ExactAmountClientCache.get(self.getMenu().containerId, entry.getWhat());
        return exact == null ? original : appeng.core.localization.ButtonToolTips.StoredAmount
            .text(ExactAmountFormatter.full(exact))
            .withStyle(appeng.core.localization.Tooltips.MUTED_COLOR);
    }
}
