package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.ECOCycleItemList;
import cn.dancingsnow.neoecoae.api.me.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftConfirmMenu.class)
public class CraftConfirmMenuMixin implements ECOCraftConfirmMenuMode {
    @Unique
    private static final Logger NEOECOAE_LOGGER = LoggerFactory.getLogger("neoecoae");
    @Unique
    @GuiSync(99)
    private boolean neoecoae$showFastPlannerReport;

    @Unique
    @GuiSync(104)
    private boolean neoecoae$cyclePlanningEnabled;

    @Unique
    @GuiSync(100)
    private long neoecoae$calculationNanos;

    /** Zero means absent; otherwise this is {@code PlanningStatus.ordinal() + 1}. */
    @Unique
    @GuiSync(101)
    private int neoecoae$planningStatusCode;

    @Unique
    @GuiSync(102)
    public ECOCycleItemList neoecoae$cycleItems = ECOCycleItemList.EMPTY;

    @Unique
    @GuiSync(103)
    public CraftingGraphSnapshot neoecoae$craftingGraph = CraftingGraphSnapshot.EMPTY;

    /** Server-side result paired with the plan whose confirmation page the player actually saw. */
    @Unique
    private @Nullable ECOPlanningResult neoecoae$confirmedPlanningResult;

    @Shadow
    private ICraftingPlan result;


    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureFastPlannerMode(int id, Inventory inventory, ISubMenuHost host, CallbackInfo ci) {
        if (inventory.player.level().isClientSide() || !(host instanceof IActionHost actionHost)) {
            return;
        }
        var node = actionHost.getActionableNode();
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(node == null ? null : node.getGrid());
        neoecoae$showFastPlannerReport = settings != null && settings.neoecoae$shouldUseFastPlanner();
        neoecoae$cyclePlanningEnabled = settings != null && settings.neoecoae$isCyclePlanningEnabled();
    }

    @Inject(method = "planJob", at = @At("HEAD"))
    private void resetPlannerDiagnostics(AEKey what, int amount, CalculationStrategy strategy,
            CallbackInfoReturnable<Boolean> cir) {
        neoecoae$calculationNanos = 0;
        neoecoae$planningStatusCode = 0;
        neoecoae$cycleItems = ECOCycleItemList.EMPTY;
        neoecoae$craftingGraph = CraftingGraphSnapshot.EMPTY;
        neoecoae$confirmedPlanningResult = null;
    }

    @Inject(
        method = "broadcastChanges",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/menu/me/crafting/CraftingPlanSummary;fromJob("
                + "Lappeng/api/networking/IGrid;"
                + "Lappeng/api/networking/security/IActionSource;"
                + "Lappeng/api/networking/crafting/ICraftingPlan;"
                + ")Lappeng/menu/me/crafting/CraftingPlanSummary;"
        )
    )
    private void capturePlannerDiagnostics(CallbackInfo ci) {
        neoecoae$calculationNanos = 0;
        neoecoae$planningStatusCode = 0;
        neoecoae$cycleItems = ECOCycleItemList.EMPTY;
        neoecoae$craftingGraph = CraftingGraphSnapshot.EMPTY;
        ECOPlanningResult planningResult = result instanceof ECOCraftingPlanDiagnostics diagnostics
            ? diagnostics.neoecoae$getPlanningResult()
            : null;
        String source = "plan-field";
        if (planningResult == null) {
            planningResult = ECOPlanningResultRegistry.find(result);
            source = planningResult == null ? "none" : "registry-exact";
        }
        NEOECOAE_LOGGER.info(
            "[ECO-CONFIRM] capture finalOutput={} planClass={} diagnosticsPresent={} planningResultPresent={} "
                + "source={} status={} solvedCycleExpected={} cycleSafetyRequired={} components={} "
                + "registrySchedules={} registryMetadata={}",
            result == null ? null : result.finalOutput(), result == null ? null : result.getClass().getName(),
            result instanceof ECOCraftingPlanDiagnostics, planningResult != null, source,
            planningResult == null ? null : planningResult.status(),
            ECOPlanningResultRegistry.cycleExpected(planningResult),
            ECOPlanningResultRegistry.cycleSafetyRequired(result, planningResult),
            ECOPlanningResultRegistry.describePlanningResult(planningResult),
            ECOPlanningResultRegistry.registeredScheduleCount(), ECOPlanningResultRegistry.registeredMetadataCount());
        if (planningResult != null) {
            neoecoae$confirmedPlanningResult = planningResult;
            neoecoae$calculationNanos = planningResult.calculationNanos();
            neoecoae$planningStatusCode = planningResult.status().ordinal() + 1;
            CraftingGraphSnapshot snapshot = CraftingGraphSnapshotFactory.create(planningResult);
            neoecoae$craftingGraph = snapshot;
            LinkedHashMap<AEKey, ECOCycleItemList.Entry> cycleItems = new LinkedHashMap<>();
            for (var cycle : snapshot.cycleGroups()) {
                LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
                cycle.exactSingleNetOutputs().forEach(value -> keys.add(value.key()));
                cycle.exactTotalNetOutputs().forEach(value -> keys.add(value.key()));
                cycle.availableAmounts().forEach(value -> keys.add(value.key()));
                for (int memberId : cycle.memberNodeIds()) {
                    snapshot.nodes().stream().filter(node -> node.nodeId() == memberId).findFirst().ifPresent(node ->
                        keys.add(node.key()));
                }
                // A legacy/partially populated diagnostic may not have net-output entries yet. The member list
                // still needs a selectable row so every unresolved SCC remains openable from the report.
                for (AEKey key : keys) {
                    cycleItems.putIfAbsent(key, new ECOCycleItemList.Entry(key,
                        exactAmountFor(cycle.exactSingleNetOutputs(), key),
                        exactAmountFor(cycle.exactTotalNetOutputs(), key), cycle.executionCountKnowledge(),
                        cycle.solveStatus(), cycle.componentId()));
                }
            }
            neoecoae$cycleItems = new ECOCycleItemList(List.copyOf(cycleItems.values()));
        }
    }

    /**
     * Bind only the synchronous submission represented by this confirmation menu to its complete ECO plan,
     * execution schedule and independent cycle expectation. The latter remains true when schedule propagation
     * fails, allowing the executor to stop instead of silently treating a solved cycle as a vanilla DAG.
     */
    @WrapOperation(
        method = "startJob",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingService;submitJob("
                + "Lappeng/api/networking/crafting/ICraftingPlan;"
                + "Lappeng/api/networking/crafting/ICraftingRequester;"
                + "Lappeng/api/networking/crafting/ICraftingCPU;"
                + "ZLappeng/api/networking/security/IActionSource;"
                + ")Lappeng/api/networking/crafting/ICraftingSubmitResult;"
        )
    )
    private ICraftingSubmitResult submitConfirmedCyclePlan(
            ICraftingService service,
            ICraftingPlan submittedPlan,
            @Nullable ICraftingRequester requestingMachine,
            @Nullable ICraftingCPU target,
            boolean prioritizePower,
            IActionSource source,
            Operation<ICraftingSubmitResult> original) {
        ECOPlanningResult planningResult = result instanceof ECOCraftingPlanDiagnostics diagnostics
            ? diagnostics.neoecoae$getPlanningResult()
            : null;
        if (planningResult == null) planningResult = neoecoae$confirmedPlanningResult;
        if (planningResult == null) planningResult = ECOPlanningResultRegistry.find(result);
        NEOECOAE_LOGGER.info(
            "[ECO-CONFIRM] submit finalOutput={} planningResultPresent={} status={} solvedCycleExpected={} "
                + "cycleSafetyRequired={} resultSignature={} submittedSignature={} strictPlanMatch={} "
                + "components={} registrySchedules={} registryMetadata={}",
            result == null ? null : result.finalOutput(), planningResult != null,
            planningResult == null ? null : planningResult.status(),
            ECOPlanningResultRegistry.cycleExpected(planningResult),
            ECOPlanningResultRegistry.cycleSafetyRequired(result, planningResult),
            cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity.describe(
                cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity.of(result)),
            cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity.describe(
                cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity.of(submittedPlan)),
            cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity.matches(result, submittedPlan),
            ECOPlanningResultRegistry.describePlanningResult(planningResult),
            ECOPlanningResultRegistry.registeredScheduleCount(), ECOPlanningResultRegistry.registeredMetadataCount());
        ECOPlanningResult boundResult = planningResult;
        return ECOPlanningResultRegistry.withSubmissionAlias(submittedPlan, boundResult,
            () -> original.call(service, submittedPlan, requestingMachine, target, prioritizePower, source));
    }

    @Unique
    private static long amountFor(List<CraftingGraphSnapshot.KeyAmount> values, AEKey key) {
        return values.stream().filter(value -> value.key().equals(key)).mapToLong(
            CraftingGraphSnapshot.KeyAmount::amount).findFirst().orElse(0L);
    }

    @Unique
    private static boolean hasAmountFor(List<CraftingGraphSnapshot.KeyAmount> values, AEKey key) {
        return values.stream().anyMatch(value -> value.key().equals(key));
    }

    @Unique
    private static java.math.BigInteger exactAmountFor(
            List<cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.ExactKeyAmount> values, AEKey key) {
        return values.stream().filter(value -> value.key().equals(key)).map(value -> value.amount().value())
            .findFirst().orElse(java.math.BigInteger.ZERO);
    }

    @Override
    public boolean neoecoae$shouldShowFastPlannerReport() {
        return neoecoae$showFastPlannerReport;
    }

    @Override
    public boolean neoecoae$isCyclePlanningEnabled() {
        return neoecoae$cyclePlanningEnabled;
    }

    @Override
    public long neoecoae$getCalculationNanos() {
        return neoecoae$calculationNanos;
    }

    @Override
    public @Nullable PlanningStatus neoecoae$getPlanningStatus() {
        int ordinal = neoecoae$planningStatusCode - 1;
        PlanningStatus[] values = PlanningStatus.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    @Override
    public List<ECOCycleItemList.Entry> neoecoae$getCycleItems() {
        return neoecoae$cycleItems.items();
    }

    @Override
    public CraftingGraphSnapshot neoecoae$getCraftingGraphSnapshot() {
        return neoecoae$craftingGraph;
    }
}
