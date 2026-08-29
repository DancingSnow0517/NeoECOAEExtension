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
    private ECOPlanningResult neoecoae$lastPlanningResult;

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

        KeyCounter inventory = grid.getStorageService().getInventory().getAvailableStacks();
        this.neoecoae$plannerSession = NEOECOAE_DAG_PLANNER.createSession(
            grid.getCraftingService(), output.what(), inventory);
    }

    @Inject(method = "runCraftAttempt", at = @At("HEAD"), cancellable = true)
    private void runEcoDagAttempt(boolean simulate, long amount,
            CallbackInfoReturnable<CraftingPlan> cir) throws InterruptedException {
        ECOPlanningResult result = neoecoae$plannerSession.plan(amount, simulate, this::handlePausing);
        neoecoae$lastPlanningResult = result;
        switch (result.status()) {
            case SUCCESS -> cir.setReturnValue(result.plan());
            case MISSING_ITEMS -> cir.setReturnValue(simulate ? result.plan() : null);
            case CYCLE_UNSUPPORTED, AMOUNT_OVERFLOW -> cir.setReturnValue(result.plan());
            case CANCELLED -> throw new InterruptedException("ECO DAG crafting calculation cancelled");
            case PARTIAL_UNSUPPORTED, INTERNAL_ERROR -> {
                // Keep the structured diagnostic, but preserve AE2 semantics through its native planner.
            }
        }
    }

    @Inject(method = "runCraftAttempt", at = @At("RETURN"))
    private void attachEcoDiagnosticToNativePlan(boolean simulate, long amount,
            CallbackInfoReturnable<CraftingPlan> cir) {
        CraftingPlan plan = cir.getReturnValue();
        if (plan != null && (Object) plan instanceof ECOCraftingPlanDiagnostics diagnostics) {
            diagnostics.neoecoae$setPlanningResult(neoecoae$lastPlanningResult);
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
