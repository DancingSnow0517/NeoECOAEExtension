package cn.dancingsnow.neoecoae.mixins.client;

import appeng.api.stacks.AmountFormat;
import appeng.api.client.AEKeyRendering;
import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import cn.dancingsnow.neoecoae.network.ECOCycleDiagnosticsPayload;
import cn.dancingsnow.neoecoae.network.ECOSubmissionMissingPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftConfirmTableRenderer.class)
public abstract class CraftConfirmTableRendererMixin {
    private static final int ECO_CYCLE_OVERLAY = 0x383FA9F5;

    @Inject(method = "getEntryOverlayColor", at = @At("RETURN"), cancellable = true)
    private void neoecoae$highlightCycleMaterial(
        CraftingPlanSummaryEntry entry,
        CallbackInfoReturnable<Integer> cir
    ) {
        Long missing = currentSubmissionMissing().get(entry.getWhat());
        if (missing != null && missing > 0L) {
            cir.setReturnValue(0x1AFF0000);
            return;
        }
        var diagnostics = currentDiagnostics();
        if (diagnostics != null && diagnostics.materials().containsKey(entry.getWhat())) {
            cir.setReturnValue(ECO_CYCLE_OVERLAY);
        }
    }

    @Inject(method = "getEntryDescription", at = @At("RETURN"), cancellable = true)
    private void neoecoae$showLiveSubmissionAmounts(
        CraftingPlanSummaryEntry entry,
        CallbackInfoReturnable<List<Component>> cir
    ) {
        Long missing = currentSubmissionMissing().get(entry.getWhat());
        if (missing == null || missing <= 0L) {
            return;
        }
        List<Component> lines = new ArrayList<>(3);
        long available = Math.max(0L, entry.getStoredAmount() - missing);
        if (available > 0L) {
            lines.add(GuiText.FromStorage.text(entry.getWhat().formatAmount(available, AmountFormat.SLOT)));
        }
        lines.add(GuiText.Missing.text(entry.getWhat().formatAmount(missing, AmountFormat.SLOT)));
        if (entry.getCraftAmount() > 0L) {
            lines.add(GuiText.ToCraft.text(entry.getWhat().formatAmount(entry.getCraftAmount(), AmountFormat.SLOT)));
        }
        cir.setReturnValue(lines);
    }

    @Inject(method = "getEntryTooltip", at = @At("RETURN"), cancellable = true)
    private void neoecoae$prependCycleDetails(
        CraftingPlanSummaryEntry entry,
        CallbackInfoReturnable<List<Component>> cir
    ) {
        Long liveMissing = currentSubmissionMissing().get(entry.getWhat());
        if (liveMissing != null && liveMissing > 0L) {
            List<Component> lines = AEKeyRendering.getTooltip(entry.getWhat());
            long available = Math.max(0L, entry.getStoredAmount() - liveMissing);
            if (available > 0L) {
                lines.add(GuiText.FromStorage.text(entry.getWhat().formatAmount(available, AmountFormat.FULL)));
            }
            lines.add(GuiText.Missing.text(entry.getWhat().formatAmount(liveMissing, AmountFormat.FULL)));
            if (entry.getCraftAmount() > 0L) {
                lines.add(GuiText.ToCraft.text(
                    entry.getWhat().formatAmount(entry.getCraftAmount(), AmountFormat.FULL)));
            }
            cir.setReturnValue(lines);
        }
        var diagnostics = currentDiagnostics();
        if (diagnostics == null) {
            return;
        }
        var stats = diagnostics.materials().get(entry.getWhat());
        if (stats == null) {
            return;
        }
        String initial = entry.getWhat().formatAmount(stats.initial(), AmountFormat.FULL);
        String consumed = entry.getWhat().formatAmount(stats.consumed(), AmountFormat.FULL);
        String produced = entry.getWhat().formatAmount(stats.produced(), AmountFormat.FULL);
        String remaining = entry.getWhat().formatAmount(stats.remaining(), AmountFormat.FULL);
        List<Component> cycleLines = new ArrayList<>(5);
        cycleLines.add(Component.translatable("gui.neoecoae.planning.cycle_tooltip.header")
            .withStyle(ChatFormatting.AQUA));
        cycleLines.add(Component.translatable("gui.neoecoae.planning.cycle_tooltip.initial", initial));
        cycleLines.add(Component.translatable("gui.neoecoae.planning.cycle_tooltip.consumed", consumed));
        cycleLines.add(Component.translatable("gui.neoecoae.planning.cycle_tooltip.produced", produced));
        cycleLines.add(Component.translatable("gui.neoecoae.planning.cycle_tooltip.remaining", remaining));
        cir.getReturnValue().addAll(0, cycleLines);
    }

    private static java.util.Map<appeng.api.stacks.AEKey, Long> currentSubmissionMissing() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return java.util.Map.of();
        }
        return ECOSubmissionMissingPayload.getClientMissing(player.containerMenu.containerId)
            .orElse(java.util.Map.of());
    }

    private static cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOCyclePlanningDiagnostics currentDiagnostics() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return ECOCycleDiagnosticsPayload.getClientDiagnostics(player.containerMenu.containerId).orElse(null);
    }
}
