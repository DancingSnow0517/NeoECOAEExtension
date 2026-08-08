package cn.dancingsnow.neoecoae.mixins.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.network.ECOCycleDiagnosticsPayload;
import cn.dancingsnow.neoecoae.network.ECOPlannerNoticePayload;
import cn.dancingsnow.neoecoae.util.ByteAmountFormatter;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Surfaces the server's planner-path choice in AE2's existing Crafting Plan title slot. */
@Mixin(value = CraftConfirmScreen.class, remap = false)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> {
    protected CraftConfirmScreenMixin(
            CraftConfirmMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void neoecoae$showPlannerFallback(CallbackInfo ci) {
        CraftConfirmMenu menu = getMenu();
        ECOPlannerNoticePayload.getClientNoticeData(menu.containerId).ifPresent(notice -> {
            ECOPlannerFallbackReason reason = notice.reason();
            if (notice.overflow()) {
                String elapsed = String.format(Locale.ROOT, "%.2f", notice.elapsedNanos() / 1_000_000.0D);
                setTextContent(
                        "dialog_title",
                        Component.translatable("gui.neoecoae.planning.overflow_title", elapsed, notice.formattedBytes())
                                .withStyle(style -> style.withColor(0xFF8080)));
            } else if (notice.elapsedNanos() > 0L && menu.getPlan() != null) {
                String elapsed = String.format(Locale.ROOT, "%.2f", notice.elapsedNanos() / 1_000_000.0D);
                Component title = Component.translatable("gui.neoecoae.planning.title")
                        .append(Component.translatable("gui.neoecoae.planning.eco_fast_suffix", elapsed)
                                .withStyle(style -> style.withColor(0x8377FF)))
                        .append(Component.literal(" | "
                                + ByteAmountFormatter.format(menu.getPlan().getUsedBytes())));
                setTextContent("dialog_title", title);
            } else if (reason != ECOPlannerFallbackReason.FAST_PATH) {
                setTextContent(
                        "dialog_title",
                        reason == ECOPlannerFallbackReason.DYNAMIC_SMITHING
                                ? Component.translatable(reason.translationKey())
                                        .withStyle(ChatFormatting.GOLD)
                                : Component.translatable(
                                                "gui.neoecoae.planning.ae2_fallback_title",
                                                Component.translatable(reason.translationKey()))
                                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void neoecoae$showMissingCycleSeed(CallbackInfo ci) {
        var diagnostics = ECOCycleDiagnosticsPayload.getClientDiagnostics(getMenu().containerId)
                .orElse(null);
        if (diagnostics == null || diagnostics.missingSeeds().isEmpty()) {
            return;
        }
        setTextContent(
                "cpu_status",
                Component.translatable(
                        "gui.neoecoae.planning.partial_plan_cycle",
                        Component.translatable("gui.neoecoae.planning.partial_plan_cycle.warning")
                                .withStyle(style -> style.withColor(0xFF8080))));
    }

    @Inject(method = "drawFG", at = @At("TAIL"))
    private void neoecoae$showMissingCycleSeedTooltip(
            GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        var diagnostics = ECOCycleDiagnosticsPayload.getClientDiagnostics(getMenu().containerId)
                .orElse(null);
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
                    .append(Component.literal(
                            " x" + missing.getKey().formatAmount(missing.getValue(), AmountFormat.SLOT)));
        }
        var tooltip = new java.util.ArrayList<Component>();
        tooltip.add(Component.translatable("gui.neoecoae.planning.cycle_missing_seed", items)
                .withStyle(ChatFormatting.YELLOW));

        Component statusText = Component.translatable(
                "gui.neoecoae.planning.partial_plan_cycle",
                Component.translatable("gui.neoecoae.planning.partial_plan_cycle.warning")
                        .withStyle(style -> style.withColor(0xFF8080)));
        var statusStyle = style.getText().get("cpu_status");
        if (statusStyle != null && statusStyle.getPosition() != null) {
            var point = statusStyle
                    .getPosition()
                    .resolve(new net.minecraft.client.renderer.Rect2i(0, 0, imageWidth, imageHeight));
            int width = font.width(statusText);
            if (mouseX >= point.getX() - width / 2
                    && mouseX <= point.getX() + width / 2
                    && mouseY >= point.getY() - font.lineHeight
                    && mouseY <= point.getY() + font.lineHeight) {
                drawTooltip(guiGraphics, getGuiLeft() + mouseX, getGuiTop() + mouseY, tooltip);
            }
        }
    }
}
