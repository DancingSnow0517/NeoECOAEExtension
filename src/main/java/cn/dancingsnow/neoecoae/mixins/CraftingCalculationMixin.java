package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCalculationSettings;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

@Mixin(CraftingCalculation.class)
public abstract class CraftingCalculationMixin implements ECOCraftingCalculationSettings {
    @Unique
    private static final ECOCraftingPlannerService NEOECOAE_DAG_PLANNER = new ECOCraftingPlannerService();
    @Unique
    private boolean neoecoae$ignorePatternSubstitutions;
    @Unique
    private ECOCraftingPlannerService.Session neoecoae$plannerSession;
    @Unique
    private volatile ECOPlanningResult neoecoae$lastPlanningResult;
    /** Result of this exact runCraftAttempt invocation; candidate planners may invoke attempts concurrently. */
    @Unique
    private final ThreadLocal<ECOPlanningResult> neoecoae$attemptPlanningResult = new ThreadLocal<>();
    /** Prevents a native fallback path from re-entering the ECO hook if AE2 invokes the attempt again. */
    @Unique
    private boolean neoecoae$nativeFallbackBypass;

    @Shadow
    abstract void handlePausing() throws InterruptedException;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureNetworkPlanningMode(
        Level level,
        IGrid grid,
        ICraftingSimulationRequester simRequester,
        GenericStack output,
        CalculationStrategy strategy,
        CallbackInfo ci
    ) {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(grid);
        this.neoecoae$ignorePatternSubstitutions = settings != null
            && settings.neoecoae$isIgnoringPatternSubstitutions();

        if (settings == null || !settings.neoecoae$shouldUseFastPlanner()) {
            this.neoecoae$plannerSession = null;
            return;
        }

        KeyCounter inventory = grid.getStorageService().getInventory().getAvailableStacks();
        this.neoecoae$plannerSession = NEOECOAE_DAG_PLANNER.createSession(
            grid.getCraftingService(), output.what(), inventory,
            settings.neoecoae$isCyclePlanningEnabled());
    }

    @Inject(method = "runCraftAttempt", at = @At("HEAD"), cancellable = true)
    private void runEcoDagAttempt(boolean simulate, long amount,
            CallbackInfoReturnable<CraftingPlan> cir) throws InterruptedException {
        // A RETURN hook must never observe a result left by an earlier probe on a reused worker thread.
        neoecoae$attemptPlanningResult.remove();
        if (neoecoae$plannerSession == null) {
            return;
        }
        if (neoecoae$nativeFallbackBypass) {
            neoecoae$nativeFallbackBypass = false;
            return;
        }
        ECOPlanningResult result = neoecoae$plannerSession.plan(amount, simulate, this::handlePausing);
        neoecoae$attemptPlanningResult.set(result);
        neoecoae$lastPlanningResult = result;
        switch (result.status()) {
            case SUCCESS -> cir.setReturnValue(result.plan());
            case MISSING_ITEMS -> cir.setReturnValue(simulate ? result.plan() : null);
            case CYCLE_UNSUPPORTED, AMOUNT_OVERFLOW -> cir.setReturnValue(result.plan());
            case PLANNED_BUT_AMOUNT_UNREPRESENTABLE -> cir.setReturnValue(result.plan());
            case PARTIAL, CYCLE_UNRESOLVED -> cir.setReturnValue(result.plan());
            case CANCELLED -> throw new InterruptedException("ECO DAG crafting calculation cancelled");
            case PARTIAL_UNSUPPORTED, UNSUPPORTED, INTERNAL_ERROR -> {
                // Keep the structured diagnostic, but preserve AE2 semantics through its native planner.
                neoecoae$nativeFallbackBypass = true;
            }
        }
    }

    @Inject(method = "runCraftAttempt", at = @At("RETURN"), order = 2000)
    private void attachEcoDiagnosticToNativePlan(boolean simulate, long amount,
            CallbackInfoReturnable<CraftingPlan> cir) {
        ECOPlanningResult attemptResult = neoecoae$attemptPlanningResult.get();
        neoecoae$attemptPlanningResult.remove();
        CraftingPlan plan = cir.getReturnValue();
        if (plan != null && attemptResult != null
                && (Object) plan instanceof ECOCraftingPlanDiagnostics diagnostics
                && diagnostics.neoecoae$getPlanningResult() == null) {
            // Another RETURN transformer may rebuild/patch the public plan produced by this exact attempt.
            // Carry the attempt-local diagnostic onto that plan so the confirmation boundary can bind the
            // transformed signature back to the complete ECO executable plan. A calculation-wide "last"
            // result is unsafe here because multi-planners can evaluate candidates concurrently.
            diagnostics.neoecoae$setPlanningResult(attemptResult);
        }
    }

    @Override
    public boolean neoecoae$isIgnoringPatternSubstitutions() {
        return neoecoae$ignorePatternSubstitutions;
    }

    @Override
    @Nullable
    public ECOPlanningResult neoecoae$getLastPlanningResult() {
        return neoecoae$lastPlanningResult;
    }
}
