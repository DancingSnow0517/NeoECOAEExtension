package cn.dancingsnow.neoecoae.mixins;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.me.crafting.CraftingStatusTableRenderer;
import cn.dancingsnow.neoecoae.util.NETextFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CraftingStatusTableRenderer.class, remap = false)
public abstract class CraftingStatusTableRendererMixin {
    @Redirect(
            method = {"getEntryDescription", "getEntryTooltip"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/stacks/AEKey;formatAmount(JLappeng/api/stacks/AmountFormat;)Ljava/lang/String;"))
    private String neoecoae$formatLongCraftingStatusAmount(AEKey key, long amount, AmountFormat format) {
        return amount >= 1_000_000_000_000_000L
                ? NETextFormat.formatItemAmount(amount)
                : key.formatAmount(amount, format);
    }
}
