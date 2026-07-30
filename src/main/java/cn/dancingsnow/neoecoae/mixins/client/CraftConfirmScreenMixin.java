package cn.dancingsnow.neoecoae.mixins.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.network.ECOPlannerNoticePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Surfaces the server's planner-path choice in AE2's existing Crafting Plan title slot. */
@Mixin(CraftConfirmScreen.class)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> {
    protected CraftConfirmScreenMixin(
        CraftConfirmMenu menu,
        Inventory playerInventory,
        Component title,
        ScreenStyle style
    ) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void neoecoae$showPlannerFallback(CallbackInfo ci) {
        CraftConfirmMenu menu = getMenu();
        ECOPlannerNoticePayload.getClientNotice(menu.containerId).ifPresent(reason -> setTextContent(
            "dialog_title",
            Component.translatable(
                "gui.neoecoae.planning.ae2_fallback_title",
                Component.translatable(reason.translationKey())
            ).withStyle(ChatFormatting.GOLD)
        ));
    }
}
