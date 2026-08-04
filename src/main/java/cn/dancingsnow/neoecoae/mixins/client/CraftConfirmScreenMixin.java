package cn.dancingsnow.neoecoae.mixins.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.style.WidgetStyle;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.network.ECOCycleDiagnosticsPayload;
import cn.dancingsnow.neoecoae.network.ECOPlannerNoticePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
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

    @Inject(method = "drawFG", at = @At("TAIL"))
    private void neoecoae$showMissingCycleSeed(
        GuiGraphics guiGraphics,
        int offsetX,
        int offsetY,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        var diagnostics = ECOCycleDiagnosticsPayload.getClientDiagnostics(getMenu().containerId).orElse(null);
        if (diagnostics == null || diagnostics.missingSeeds().isEmpty()) {
            return;
        }
        Component items = Component.empty();
        boolean first = true;
        for (var missing : diagnostics.missingSeeds().entrySet()) {
            if (!first) {
                items = items.copy().append(Component.literal(", "));
            }
            first = false;
            items = items.copy()
                .append(AEKeyRendering.getDisplayName(missing.getKey()))
                .append(Component.literal(" x" + missing.getKey().formatAmount(
                    missing.getValue(), AmountFormat.SLOT)));
        }
        Component warning = Component.translatable(
            "gui.neoecoae.planning.cycle_missing_seed",
            items
        ).withStyle(ChatFormatting.YELLOW);

        Rect2i bounds = new Rect2i(0, 0, imageWidth, imageHeight);
        WidgetStyle selectCpu = style.getWidget("selectCpu");
        WidgetStyle cancel = style.getWidget("cancel");
        int warningTop = selectCpu.resolve(bounds).getY() + selectCpu.getHeight() + 4;
        int warningBottom = cancel.resolve(bounds).getY() - 4;
        int warningHeight = Math.max(font.lineHeight, warningBottom - warningTop);
        var lines = font.split(warning, imageWidth - 16);
        int lineCount = Math.min(warningHeight / font.lineHeight, lines.size());
        int firstLineY = warningTop + Math.max(0, (warningHeight - lineCount * font.lineHeight) / 2);
        for (int i = 0; i < lineCount; i++) {
            var line = lines.get(i);
            int x = Math.max(8, (imageWidth - font.width(line)) / 2);
            guiGraphics.drawString(font, line, x, firstLineY + i * font.lineHeight, 0xFFE5C95A, false);
        }
    }
}
