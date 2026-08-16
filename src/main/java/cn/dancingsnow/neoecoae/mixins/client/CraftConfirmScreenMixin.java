package cn.dancingsnow.neoecoae.mixins.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.api.client.AEKeyRendering;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.stacks.AmountFormat;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import cn.dancingsnow.neoecoae.network.ECOCycleDiagnosticsPayload;
import cn.dancingsnow.neoecoae.network.ECOPlanningMissingPayload;
import cn.dancingsnow.neoecoae.network.ECOPlannerNoticePayload;
import cn.dancingsnow.neoecoae.network.ECOSubmissionMissingPayload;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerDiagnostic;
import cn.dancingsnow.neoecoae.util.ByteAmountFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Inject(method = "updateBeforeRender", at = @At("HEAD"))
    private void neoecoae$keepSubmissionDeficitsOnPlan(CallbackInfo ci) {
        var missing = ECOSubmissionMissingPayload.getClientMissing(getMenu().containerId).orElse(java.util.Map.of());
        var error = getMenu().submitError.result();
        if (!missing.isEmpty()
            && error != null
            && error.errorCode() == CraftingSubmitErrorCode.MISSING_INGREDIENT) {
            // The ECO list supersedes AE2's single-item error screen and keeps retry available.
            getMenu().clearError();
        }
    }

    @Redirect(
        method = "drawFG",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/menu/me/crafting/CraftingPlanSummary;getEntries()Ljava/util/List;"
        )
    )
    private List<CraftingPlanSummaryEntry> neoecoae$prioritizeMissingEntries(CraftingPlanSummary plan) {
        List<CraftingPlanSummaryEntry> entries = plan.getEntries();
        var planningMissing = ECOPlanningMissingPayload.getClientMissing(getMenu().containerId)
            .orElse(java.util.Map.of());
        var submissionMissing = ECOSubmissionMissingPayload.getClientMissing(getMenu().containerId)
            .orElse(java.util.Map.of());
        if (planningMissing.isEmpty() && submissionMissing.isEmpty()) {
            return entries;
        }

        List<CraftingPlanSummaryEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(entry -> {
            if (entry.getMissingAmount() > 0L) {
                return 0;
            }
            if (planningMissing.getOrDefault(entry.getWhat(), 0L) > 0L
                || submissionMissing.getOrDefault(entry.getWhat(), 0L) > 0L) {
                return 1;
            }
            return 2;
        }));
        return sorted;
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void neoecoae$showPlannerFallback(CallbackInfo ci) {
        CraftConfirmMenu menu = getMenu();
        ECOPlannerNoticePayload.getClientNoticeData(menu.containerId).ifPresent(notice -> {
            var reason = notice.reason();
            if (notice.overflow()) {
                String elapsed = String.format(Locale.ROOT, "%.2f", notice.elapsedNanos() / 1_000_000.0D);
                Component title = Component.translatable(
                    "gui.neoecoae.planning.overflow_title",
                    elapsed,
                    notice.formattedBytes()
                ).withColor(0xFF8080);
                setTextContent("dialog_title", title);
            } else if (notice.elapsedNanos() > 0L
                && menu.getPlan() != null) {
                String elapsed = String.format(Locale.ROOT, "%.2f", notice.elapsedNanos() / 1_000_000.0D);
                Component title = Component.translatable("gui.neoecoae.planning.title")
                    .append(Component.translatable("gui.neoecoae.planning.eco_fast_suffix", elapsed)
                        .withColor(0x8377FF))
                    .append(Component.literal(" · " + ByteAmountFormatter.format(menu.getPlan().getUsedBytes())));
                setTextContent("dialog_title", title);
            } else if (reason != cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason.FAST_PATH) {
                setTextContent(
                    "dialog_title",
                    reason == cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason.DYNAMIC_SMITHING
                        ? Component.translatable(reason.translationKey()).withStyle(ChatFormatting.GOLD)
                        : Component.translatable(
                            "gui.neoecoae.planning.ae2_fallback_title",
                            Component.translatable(reason.translationKey())
                        ).withStyle(ChatFormatting.GOLD)
                );
            }
        });
    }

    @Inject(method = "drawFG", at = @At("TAIL"))
    private void neoecoae$showPlannerDiagnosticTooltip(
        GuiGraphics guiGraphics,
        int offsetX,
        int offsetY,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        var notice = ECOPlannerNoticePayload.getClientNoticeData(getMenu().containerId).orElse(null);
        if (notice == null || notice.diagnostics().isEmpty()) {
            return;
        }
        var titleStyle = style.getText().get("dialog_title");
        if (titleStyle == null || titleStyle.getPosition() == null) {
            return;
        }
        var point = titleStyle.getPosition().resolve(
            new net.minecraft.client.renderer.Rect2i(0, 0, imageWidth, imageHeight)
        );
        Component title = notice.reason() == cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason.FAST_PATH
            ? Component.translatable("gui.neoecoae.planning.title")
            : Component.translatable(notice.reason().translationKey());
        int titleWidth = font.width(title);
        if (mouseX < point.getX() - titleWidth / 2 || mouseX > point.getX() + titleWidth / 2
            || mouseY < point.getY() - font.lineHeight || mouseY > point.getY() + font.lineHeight) {
            return;
        }
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.translatable("gui.neoecoae.planning.diagnostic.header")
            .withStyle(ChatFormatting.YELLOW));
        for (ECOPlannerDiagnostic diagnostic : notice.diagnostics()) {
            lines.add(Component.translatable(diagnostic.translationKey()));
        }
        drawTooltip(guiGraphics, getGuiLeft() + mouseX, getGuiTop() + mouseY, lines);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void neoecoae$showMissingCycleSeed(CallbackInfo ci) {
        var submissionMissing = ECOSubmissionMissingPayload.getClientMissing(getMenu().containerId)
            .orElse(java.util.Map.of());
        if (!submissionMissing.isEmpty()) {
            setTextContent("cpu_status", GuiText.PartialPlan.text());
            return;
        }
        var diagnostics = ECOCycleDiagnosticsPayload.getClientDiagnostics(getMenu().containerId).orElse(null);
        if (diagnostics == null || diagnostics.missingSeeds().isEmpty()) {
            return;
        }
        setTextContent(
            "cpu_status",
            Component.translatable(
                "gui.neoecoae.planning.partial_plan_cycle",
                Component.translatable("gui.neoecoae.planning.partial_plan_cycle.warning")
                    .withStyle(style -> style.withColor(0xFF8080))
            )
        );
    }

    @Inject(method = "drawFG", at = @At("TAIL"))
    private void neoecoae$showMissingCycleSeedTooltip(
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
        var tooltip = new java.util.ArrayList<Component>();
        tooltip.add(Component.translatable("gui.neoecoae.planning.cycle_missing_seed", items)
            .withStyle(ChatFormatting.YELLOW));

        var statusText = Component.translatable(
            "gui.neoecoae.planning.partial_plan_cycle",
            Component.translatable("gui.neoecoae.planning.partial_plan_cycle.warning")
                .withStyle(style -> style.withColor(0xFF8080))
        );
        var statusStyle = style.getText().get("cpu_status");
        if (statusStyle != null && statusStyle.getPosition() != null) {
            var point = statusStyle.getPosition().resolve(new net.minecraft.client.renderer.Rect2i(0, 0, imageWidth, imageHeight));
            int width = font.width(statusText);
            int localX = mouseX - getGuiLeft();
            int localY = mouseY - getGuiTop();
            if (localX >= point.getX() - width / 2 && localX <= point.getX() + width / 2
                && localY >= point.getY() - font.lineHeight && localY <= point.getY() + font.lineHeight) {
                // drawFG runs with the GUI origin already translated into the pose stack.
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(-offsetX, -offsetY, 0);
                drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
                guiGraphics.pose().popPose();
            }
        }
    }
}
