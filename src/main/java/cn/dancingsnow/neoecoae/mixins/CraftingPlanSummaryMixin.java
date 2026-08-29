package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.menu.me.crafting.CraftingPlanSummary;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanSummaryDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingPlanSummary.class)
public class CraftingPlanSummaryMixin implements ECOCraftingPlanSummaryDiagnostics {
    @Unique private long neoecoae$calculationNanos;
    @Unique private PlanningStatus neoecoae$planningStatus;

    @Inject(method = "fromJob", at = @At("RETURN"))
    private static void capturePlannerDiagnostics(
            appeng.api.networking.IGrid grid,
            appeng.api.networking.security.IActionSource actionSource,
            ICraftingPlan job,
            CallbackInfoReturnable<CraftingPlanSummary> cir) {
        if ((Object) job instanceof ECOCraftingPlanDiagnostics planDiagnostics
                && planDiagnostics.neoecoae$getPlanningResult() != null
                && (Object) cir.getReturnValue() instanceof ECOCraftingPlanSummaryDiagnostics summaryDiagnostics) {
            var result = planDiagnostics.neoecoae$getPlanningResult();
            summaryDiagnostics.neoecoae$setCalculationNanos(result.calculationNanos());
            summaryDiagnostics.neoecoae$setPlanningStatus(result.status());
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void writeEcoFields(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeVarLong(neoecoae$calculationNanos);
        buffer.writeBoolean(neoecoae$planningStatus != null);
        if (neoecoae$planningStatus != null) buffer.writeEnum(neoecoae$planningStatus);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void readEcoFields(RegistryFriendlyByteBuf buffer,
            CallbackInfoReturnable<CraftingPlanSummary> cir) {
        if ((Object) cir.getReturnValue() instanceof ECOCraftingPlanSummaryDiagnostics diagnostics) {
            diagnostics.neoecoae$setCalculationNanos(buffer.readVarLong());
            diagnostics.neoecoae$setPlanningStatus(buffer.readBoolean() ? buffer.readEnum(PlanningStatus.class) : null);
        }
    }

    @Override public long neoecoae$getCalculationNanos() { return neoecoae$calculationNanos; }
    @Override public void neoecoae$setCalculationNanos(long nanos) { neoecoae$calculationNanos = Math.max(0, nanos); }
    @Override @Nullable public PlanningStatus neoecoae$getPlanningStatus() { return neoecoae$planningStatus; }
    @Override public void neoecoae$setPlanningStatus(@Nullable PlanningStatus status) { neoecoae$planningStatus = status; }
}
