package cn.dancingsnow.neoecoae.mixins.client;

import appeng.api.stacks.AmountFormat;
import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import cn.dancingsnow.neoecoae.network.ECOCycleDiagnosticsPayload;
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
        var diagnostics = currentDiagnostics();
        if (diagnostics != null && diagnostics.materials().containsKey(entry.getWhat())) {
            cir.setReturnValue(ECO_CYCLE_OVERLAY);
        }
    }

    @Inject(method = "getEntryTooltip", at = @At("RETURN"))
    private void neoecoae$prependCycleDetails(
        CraftingPlanSummaryEntry entry,
        CallbackInfoReturnable<List<Component>> cir
    ) {
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

    private static cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOCyclePlanningDiagnostics currentDiagnostics() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return ECOCycleDiagnosticsPayload.getClientDiagnostics(player.containerMenu.containerId).orElse(null);
    }
}
