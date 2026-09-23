package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.core.network.clientbound.CraftConfirmPlanPacket;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingServiceDiagnostics;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.CraftingPlanSummaryAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Future;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
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

// Include Data Energistics' merged long-amount planning entry point (priority 1000).
@Mixin(value = CraftConfirmMenu.class, priority = 1100)
public class CraftConfirmMenuMixin implements ECOCraftConfirmMenuMode {
    @Unique
    private static final Logger NEOECOAE_LOGGER = LoggerFactory.getLogger("neoecoae");
    @Unique
    @GuiSync(99)
    private boolean neoecoae$showFastPlannerReport;

    @Unique
    @GuiSync(106)
    private boolean neoecoae$ecoPlannerAvailable;

    @Unique
    @GuiSync(107)
    private boolean neoecoae$ecoReportReady;

    @Unique
    @GuiSync(104)
    private boolean neoecoae$cyclePlanningEnabled;

    @Unique
    @GuiSync(100)
    private long neoecoae$calculationNanos;

    @Unique
    @GuiSync(105)
    private String neoecoae$theoreticalBytes = "0";

    /** Zero means absent; otherwise this is {@code PlanningStatus.ordinal() + 1}. */
    @Unique
    @GuiSync(101)
    private int neoecoae$planningStatusCode;

    @Unique
    public ECOCycleItemList neoecoae$cycleItems = ECOCycleItemList.EMPTY;

    @Unique
    public CraftingGraphSnapshot neoecoae$craftingGraph = CraftingGraphSnapshot.EMPTY;

    /** Server-side result paired with the plan whose confirmation page the player actually saw. */
    @Unique
    private @Nullable ECOPlanningResult neoecoae$confirmedPlanningResult;
    @Unique private ECOPlannerOptions neoecoae$originalOptions;
    @Unique @GuiSync(108) private boolean neoecoae$bigOrderCpu;
    @Unique @GuiSync(109) private String neoecoae$planningDiagnostic = "";

    @Unique @GuiSync(110) private long neoecoae$diagnosticsVersion;
    @Unique private long neoecoae$receivedDiagnosticsVersion = -1;
    @Unique private CraftingGraphSnapshot neoecoae$sentGraph;
    @Unique private ECOCycleItemList neoecoae$sentCycles;
    @Unique private long neoecoae$startButtonAttemptStartedNanos;

    @Override public boolean neoecoae$diagnosticsReady() {
        return !((CraftConfirmMenu) (Object) this).isClientSide()
            || neoecoae$receivedDiagnosticsVersion == neoecoae$diagnosticsVersion;
    }

    @Override public void neoecoae$setDiagnostics(long version, CraftingGraphSnapshot graph, ECOCycleItemList cycles) {
        neoecoae$receivedDiagnosticsVersion = version;
        neoecoae$craftingGraph = graph;
        neoecoae$cycleItems = cycles;
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void neoecoae$sendDiagnostics(CallbackInfo ci) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (!(menu.getPlayer() instanceof ServerPlayer player) || player.containerMenu != menu) return;
        if (neoecoae$sentGraph == neoecoae$craftingGraph && neoecoae$sentCycles == neoecoae$cycleItems) return;
        neoecoae$diagnosticsVersion++;
        // A changed plan invalidates the previous stream, even when its first chunks have already arrived.
        cn.dancingsnow.neoecoae.network.MenuDataTransport.cancel(player,
            cn.dancingsnow.neoecoae.network.MenuDataTransport.Channel.GRAPH);
        cn.dancingsnow.neoecoae.network.MenuDataTransport.send(player,
            cn.dancingsnow.neoecoae.network.MenuDataTransport.Channel.GRAPH, buf -> {
                buf.writeVarLong(neoecoae$diagnosticsVersion);
                neoecoae$craftingGraph.writeToPacket(buf);
                neoecoae$cycleItems.writeToPacket(buf);
            });
        neoecoae$sentGraph = neoecoae$craftingGraph;
        neoecoae$sentCycles = neoecoae$cycleItems;
    }

    @Override public String neoecoae$getPlanningDiagnostic() {
        return neoecoae$planningDiagnostic == null ? "" : neoecoae$planningDiagnostic;
    }

    @Override public boolean neoecoae$bigOrderCpuAvailable() { return neoecoae$bigOrderCpu; }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void neoecoae$syncBigOrderCpu(CallbackInfo ci) {
        if (((CraftConfirmMenu) (Object) this).isClientSide()) return;
        neoecoae$bigOrderCpu = neoecoae$resolveBigOrderCpu() != null;
    }

    @Unique
    private cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU neoecoae$resolveBigOrderCpu() {
        var grid = getGrid();
        if (grid == null) return null;
        if (selectedCpu != null) return selectedCpu instanceof cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU eco
                && !eco.isBusy() && eco.isActive() && grid.getCraftingService().getCpus().contains(eco) ? eco : null;
        return grid.getCraftingService().getCpus().stream()
                .filter(cpu -> cpu instanceof cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU)
                .map(cpu -> (cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU) cpu)
                .filter(cpu -> !cpu.isBusy() && cpu.isActive()
                    && cpu.getSelectionMode() != appeng.api.config.CpuSelectionMode.MACHINE_ONLY)
                .findFirst().orElse(null);
    }

    @Inject(method = "cpuMatches", at = @At("HEAD"), cancellable = true)
    private void neoecoae$includeSegmentCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (neoecoae$confirmedPlanningResult != null
                && cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission.allows(
                    neoecoae$confirmedPlanningResult.status(), true)
                && cpu instanceof cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU)
            cir.setReturnValue(!cpu.isBusy());
    }

    @Override public void neoecoae$startBigOrder(boolean forced) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (menu.isClientSide()) return;
        if (result == null || neoecoae$originalOptions == null) {
            neoecoae$rejectBigOrder(result == null ? "NO_PLAN" : "NO_PLANNER_OPTIONS",
                appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        var exact = neoecoae$resolveOwnedPlanningResult(result);
        if (exact == null || exact != neoecoae$confirmedPlanningResult) {
            neoecoae$rejectBigOrder(exact == null ? "NO_EXACT_RESULT" : "CONFIRMED_RESULT_CHANGED",
                appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        if (!cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission.allows(exact, forced)) {
            NEOECOAE_LOGGER.warn(
                "[big-order-submit] Admission denied: container={}, status={}, forced={}, hasPlan={}, components={}, missingNodes={}",
                menu.containerId, exact.status(), forced, exact.plan() != null,
                exact.components().stream().filter(component -> component.status()
                        != cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.PLANNED
                    && component.status()
                        != cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.NOT_REQUIRED)
                    .map(component -> component.componentId() + ":" + component.status()).toList(),
                exact.trace().nodes().stream().filter(node -> node.exactMissing().signum() > 0).count());
            neoecoae$rejectBigOrder("ADMISSION_DENIED", appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        var cpu = neoecoae$resolveBigOrderCpu();
        var grid = getGrid();
        if (cpu == null || grid == null) {
            neoecoae$rejectBigOrder(grid == null ? "NO_GRID" : "NO_ELIGIBLE_ECO_CPU",
                grid == null ? appeng.crafting.execution.CraftingSubmitResult.CPU_OFFLINE
                    : appeng.crafting.execution.CraftingSubmitResult.NO_CPU_FOUND);
            return;
        }
        if (!forced) {
            var inventory = cn.dancingsnow.neoecoae.crafting.planner.ECOPlannerInventory.capture(grid);
            for (var node : exact.trace().nodes()) {
                if (node.key() == null || node.exactFromInventory().signum() <= 0) continue;
                var required = node.exactFromInventory();
                // Validate the full order; the exact ledger owns deferred inputs beyond long.
                if (!cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission.hasStoredAmount(
                        required, inventory.isUnbounded(node.key()), amount ->
                            grid.getStorageService().getInventory().extract(node.key(), amount,
                                Actionable.SIMULATE, getActionSrc()))) {
                    NEOECOAE_LOGGER.warn("[big-order-submit] Inventory check failed: container={}, key={}, required={}, unbounded={}",
                        menu.containerId, node.key(), required, inventory.isUnbounded(node.key()));
                    neoecoae$rejectBigOrder("MISSING_INGREDIENT",
                        appeng.crafting.execution.CraftingSubmitResult.missingIngredient(
                            new appeng.api.stacks.GenericStack(node.key(),
                                required.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact())));
                    return;
                }
            }
        }
        final cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan admission;
        try {
            admission = new cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan(exact, forced);
        } catch (RuntimeException invalid) {
            NEOECOAE_LOGGER.warn("[big-order-submit] Exact order rejected", invalid);
            neoecoae$rejectBigOrder("EXACT_ORDER_INVALID",
                appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        NEOECOAE_LOGGER.info("[big-order-submit] Submitting to CPU: container={}, output={}, forced={}, cpu={}",
            menu.containerId, result.finalOutput(), forced, neoecoae$describeCpu(cpu));
        var submitted = cpu.getCluster().submitJob(grid, admission, getActionSrc(), null);
        menu.setAutoStart(false);
        if (submitted.successful()) {
            NEOECOAE_LOGGER.info("[big-order-submit] Accepted: container={}, output={}", menu.containerId, result.finalOutput());
            result = null;
            neoecoae$confirmedPlanningResult = null;
            menu.getHost().returnToMainMenu(menu.getPlayer(), menu);
        } else neoecoae$rejectBigOrder("CPU_SUBMISSION_FAILED", submitted);
    }

    @Unique
    private void neoecoae$rejectBigOrder(String reason, ICraftingSubmitResult failure) {
        var menu = (CraftConfirmMenu) (Object) this;
        menu.setAutoStart(false);
        menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(failure);
        NEOECOAE_LOGGER.warn(
            "[big-order-submit] Rejected: reason={}, container={}, output={}, status={}, selectedCpu={}, error={}, detail={}",
            reason, menu.containerId, result == null ? null : result.finalOutput(), neoecoae$getPlanningStatus(),
            neoecoae$describeCpu(selectedCpu), failure.errorCode(), failure.errorDetail());
    }

    @Unique
    private String neoecoae$lastStartDiagnostic;
    @Unique private long neoecoae$nextStartDiagnosticTick;

    @Shadow
    private ICraftingPlan result;

    @Shadow
    private CraftingPlanSummary plan;

    @Shadow
    private @Nullable ICraftingCPU selectedCpu;

    @Shadow
    public boolean noCPU;

    @Shadow
    private IGrid getGrid() {
        throw new AssertionError();
    }

    @Shadow
    private IActionSource getActionSrc() {
        throw new AssertionError();
    }


    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureFastPlannerMode(int id, Inventory inventory, ISubMenuHost host, CallbackInfo ci) {
        if (inventory.player.level().isClientSide() || !(host instanceof IActionHost actionHost)) {
            return;
        }
        var node = actionHost.getActionableNode();
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(node == null ? null : node.getGrid());
        neoecoae$ecoPlannerAvailable = settings != null && settings.neoecoae$shouldUseFastPlanner();
        neoecoae$showFastPlannerReport = false;
        neoecoae$ecoReportReady = false;
        neoecoae$cyclePlanningEnabled = settings != null && settings.neoecoae$isCyclePlanningEnabled();
    }

    @Inject(method = {"planJob", "data_energistics$planJob"}, at = @At("HEAD"))
    private void resetPlannerDiagnostics(CallbackInfoReturnable<Boolean> cir) {
        neoecoae$planningDiagnostic = "";
        neoecoae$showFastPlannerReport = false;
        neoecoae$ecoReportReady = false;
        neoecoae$calculationNanos = 0;
        neoecoae$theoreticalBytes = "0";
        neoecoae$planningStatusCode = 0;
        neoecoae$cycleItems = ECOCycleItemList.EMPTY;
        neoecoae$craftingGraph = CraftingGraphSnapshot.EMPTY;
        neoecoae$confirmedPlanningResult = null;
        neoecoae$originalOptions = null;
        neoecoae$lastStartDiagnostic = null;
        neoecoae$nextStartDiagnosticTick = 0;
    }

    /**
     * Routes an enabled ECO confirmation request to its own planner. Passing only a requester marker through
     * the normal service lets another planner consume the request before ECO ever creates a result or report.
     * Disabled requests retain the original service path.
     */
    @WrapOperation(
        method = {"planJob", "data_energistics$planJob"},
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingService;beginCraftingCalculation("
                + "Lnet/minecraft/world/level/Level;"
                + "Lappeng/api/networking/crafting/ICraftingSimulationRequester;"
                + "Lappeng/api/stacks/AEKey;"
                + "J"
                + "Lappeng/api/networking/crafting/CalculationStrategy;"
                + ")Ljava/util/concurrent/Future;"
        )
    )
    private Future<ICraftingPlan> neoecoae$routeEcoPlanningRequest(
            ICraftingService service,
            Level level,
            ICraftingSimulationRequester requester,
            AEKey what,
            long amount,
            CalculationStrategy strategy,
            Operation<Future<ICraftingPlan>> original) {
        ECOCraftingNetworkSettings settings = service instanceof ECOCraftingNetworkSettings ecoSettings
            ? ecoSettings
            : null;
        boolean fastPlannerEnabled = settings != null && settings.neoecoae$isFastPlannerEnabled();
        boolean hasComputationHost = fastPlannerEnabled && settings.neoecoae$hasComputationHost();
        if (NEConfig.ecoCraftSubmissionDebug && settings != null && !fastPlannerEnabled) {
            hasComputationHost = settings.neoecoae$hasComputationHost();
        }
        boolean useEco = fastPlannerEnabled && hasComputationHost;
        neoecoae$ecoPlannerAvailable = useEco;
        if (NEConfig.ecoCraftSubmissionDebug) {
            neoecoae$logEcoScreenRouting(settings, fastPlannerEnabled, hasComputationHost, useEco, what, amount);
        }
        if (useEco) {
            neoecoae$originalOptions = ECOPlannerOptions.from(settings);
            return ECOPlanningService.begin(level, getGrid(), requester.getActionSource(), what, amount, strategy,
                neoecoae$originalOptions);
        }
        return original.call(service, level, requester, what, amount, strategy);
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
        if (neoecoae$ownsPlan(result)) {
            neoecoae$applyPlannerDiagnostics(result);
            neoecoae$showFastPlannerReport = true;
            neoecoae$ecoReportReady = true;
            if (NEConfig.ecoCraftSubmissionDebug) {
                NEOECOAE_LOGGER.info(
                    "[ECO-CRAFT-SUBMIT] ECO report ready; client may switch to the ECO screen: output={}, "
                        + "resultType={}",
                    result.finalOutput(), result.getClass().getName());
            }
        } else {
            // A service wrapper may have accepted the marked request and returned its own plan. Clear any report
            // state left by an earlier calculation on this menu; ownership follows the result, not eligibility.
            neoecoae$showFastPlannerReport = false;
            neoecoae$ecoReportReady = false;
            neoecoae$confirmedPlanningResult = null;
            if (NEConfig.ecoCraftSubmissionDebug && neoecoae$ecoPlannerAvailable) {
                NEOECOAE_LOGGER.warn(
                    "[ECO-CRAFT-SUBMIT] ECO screen unavailable: reason=ECO_ROUTED_RESULT_HAS_NO_DIAGNOSTICS, "
                        + "resultType={}, output={}",
                    result == null ? "<null>" : result.getClass().getName(),
                    result == null ? "<null>" : result.finalOutput());
            }
        }
    }

    @Unique
    private void neoecoae$logEcoScreenRouting(
            @Nullable ECOCraftingNetworkSettings settings,
            boolean fastPlannerEnabled,
            boolean hasComputationHost,
            boolean useEco,
            AEKey what,
            long amount) {
        String player = getActionSrc().player()
            .map(value -> value.getGameProfile().getName())
            .orElse("<machine>");
        if (settings == null) {
            NEOECOAE_LOGGER.warn(
                "[ECO-CRAFT-SUBMIT] ECO screen unavailable: reason=CRAFTING_SERVICE_HAS_NO_ECO_SETTINGS, "
                    + "player={}, output={}, amount={}, serviceType={}",
                player, what, amount, getGrid().getCraftingService().getClass().getName());
            return;
        }

        if (!useEco) {
            var hosts = getGrid().getMachines(ECOComputationSystemBlockEntity.class);
            long formedHosts = hosts.stream().filter(ECOComputationSystemBlockEntity::isFormed).count();
            long onlineHosts = hosts.stream().filter(host -> host.getMainNode().isOnline()).count();
            long eligibleHosts = hosts.stream()
                .filter(host -> host.isFormed() && host.getMainNode().isOnline())
                .count();
            String reason = !fastPlannerEnabled
                ? (hasComputationHost ? "FAST_PLANNER_DISABLED" : "FAST_PLANNER_DISABLED_AND_NO_ELIGIBLE_HOST")
                : "NO_FORMED_ONLINE_COMPUTATION_HOST";
            NEOECOAE_LOGGER.warn(
                "[ECO-CRAFT-SUBMIT] ECO screen unavailable: reason={}, player={}, output={}, amount={}, "
                    + "fastPlannerEnabled={}, computationHosts(total/formed/online/eligible)={}/{}/{}/{}",
                reason, player, what, amount, fastPlannerEnabled, hosts.size(), formedHosts, onlineHosts,
                eligibleHosts);
            return;
        }

        NEOECOAE_LOGGER.info(
            "[ECO-CRAFT-SUBMIT] ECO planner selected; waiting for an ECO-owned result: player={}, output={}, "
                + "amount={}",
            player, what, amount);
    }

    @Unique
    private boolean neoecoae$ownsPlan(@Nullable ICraftingPlan plan) {
        return neoecoae$resolveOwnedPlanningResult(plan) != null;
    }

    /**
     * Restores metadata at the menu boundary if another integration copied the plan or the mixed-in field was lost.
     * Exact-object registration proves provenance for every ECO diagnostic status. Structural recovery is allowed
     * only after this menu itself routed the request directly to ECO, and still requires the complete plan identity.
     */
    @Unique
    private @Nullable ECOPlanningResult neoecoae$resolveOwnedPlanningResult(@Nullable ICraftingPlan plan) {
        if (plan == null) return null;
        ECOPlanningResult planningResult = plan instanceof ECOCraftingPlanDiagnostics diagnostics
            ? diagnostics.neoecoae$getPlanningResult()
            : null;
        String recoverySource = null;
        if (planningResult == null) {
            planningResult = ECOPlanningResultRegistry.findExact(plan);
            if (planningResult != null) recoverySource = "exact-object";
        }
        if (planningResult == null && neoecoae$ecoPlannerAvailable) {
            planningResult = ECOPlanningResultRegistry.find(plan);
            if (planningResult != null) recoverySource = "strict-plan-identity";
        }
        if (planningResult != null && plan instanceof ECOCraftingPlanDiagnostics diagnostics) {
            diagnostics.neoecoae$setPlanningResult(planningResult);
        }
        if (recoverySource != null && NEConfig.ecoCraftSubmissionDebug) {
            NEOECOAE_LOGGER.info(
                "[ECO-CRAFT-SUBMIT] Restored ECO planning diagnostics at menu boundary: source={}, "
                    + "resultType={}, output={}",
                recoverySource, plan.getClass().getName(), plan.finalOutput());
        }
        return planningResult;
    }

    @Unique
    private void neoecoae$applyPlannerDiagnostics(@Nullable ICraftingPlan diagnosticPlan) {
        neoecoae$planningDiagnostic = "";
        neoecoae$calculationNanos = 0;
        neoecoae$theoreticalBytes = "0";
        neoecoae$planningStatusCode = 0;
        neoecoae$cycleItems = ECOCycleItemList.EMPTY;
        neoecoae$craftingGraph = CraftingGraphSnapshot.EMPTY;
        ECOPlanningResult planningResult = neoecoae$resolveOwnedPlanningResult(diagnosticPlan);
        if (planningResult != null) {
            neoecoae$confirmedPlanningResult = planningResult;
            neoecoae$calculationNanos = planningResult.calculationNanos();
            neoecoae$theoreticalBytes = planningResult.theoreticalBytes().toString();
            neoecoae$planningStatusCode = planningResult.status().ordinal() + 1;
            if (planningResult.shouldUseNativeFallback()) {
                neoecoae$planningDiagnostic = planningResult.trace().diagnostics().stream()
                    .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
                    .collect(java.util.stream.Collectors.joining("\n"));
                if (neoecoae$planningDiagnostic.length() > 4096) {
                    neoecoae$planningDiagnostic = neoecoae$planningDiagnostic.substring(0, 4096);
                }
                NEOECOAE_LOGGER.warn("[ECO-CRAFT-SUBMIT] ECO diagnostic: status={}, output={}, diagnostics={}",
                    planningResult.status(), diagnosticPlan.finalOutput(), neoecoae$planningDiagnostic);
            }
            CraftingGraphSnapshot snapshot = CraftingGraphSnapshotFactory.create(planningResult);
            neoecoae$craftingGraph = snapshot;
            LinkedHashMap<AEKey, ECOCycleItemList.Entry> cycleItems = new LinkedHashMap<>();
            for (var cycle : snapshot.cycleGroups()) {
                LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
                cycle.exactSingleNetOutputs().forEach(value -> keys.add(value.key()));
                cycle.exactTotalNetOutputs().forEach(value -> keys.add(value.key()));
                cycle.availableAmounts().forEach(value -> keys.add(value.key()));
                cycle.externalInputs().forEach(value -> keys.add(value.key()));
                cycle.requiredOutputs().forEach(value -> keys.add(value.key()));
                for (int memberId : cycle.memberNodeIds()) {
                    snapshot.nodes().stream().filter(node -> node.nodeId() == memberId).findFirst().ifPresent(node ->
                        keys.add(node.key()));
                }
                snapshot.patterns().stream().filter(pattern -> pattern.componentId() == cycle.componentId()
                        && pattern.firingCount() > 0L)
                    .flatMap(pattern -> java.util.stream.Stream.concat(pattern.inputs().stream(),
                        pattern.outputs().stream()))
                    .map(CraftingGraphSnapshot.Relationship::materialNodeId)
                    .forEach(materialId -> snapshot.nodes().stream()
                        .filter(node -> node.nodeId() == materialId).findFirst().ifPresent(node -> keys.add(node.key())));
                // A legacy/partially populated diagnostic may not have net-output entries yet. The member list
                // still needs a selectable row so every unresolved SCC remains openable from the report.
                planningResult.components().stream()
                    .filter(component -> component.componentId() == cycle.componentId())
                    .forEach(component -> keys.addAll(component.externalMissingItems().keySet()));
                for (AEKey key : keys) {
                    // MaterialNode is built from the final executable plan. PatternNode firing counts are only
                    // structural cycle metadata and can legitimately be zero for a populated plan.
                    CraftingGraphSnapshot.MaterialNode material = neoecoae$materialFor(snapshot, key);
                    var entry = new ECOCycleItemList.Entry(key,
                        material == null ? java.math.BigInteger.ZERO : material.consumedBigInteger(),
                        material == null ? java.math.BigInteger.ZERO : material.producedBigInteger(),
                        neoecoae$exactAmountFor(cycle.exactSingleNetOutputs(), key),
                        neoecoae$exactAmountFor(cycle.exactTotalNetOutputs(), key),
                        material == null ? java.math.BigInteger.ZERO : material.missingBigInteger(),
                        cycle.executionCountKnowledge(),
                        cycle.solveStatus(), cycle.componentId());
                    // An unresolved structural cycle has no authoritative total. Do not project
                    // the ordinary plan's consumed/produced fields into the cycle list: doing so
                    // makes an unused cycle look as if it consumed a real amount and falls back
                    // to the misleading "quantity unknown" row. Keep concrete startup shortages,
                    // which remain actionable even when the cycle itself was not solved.
                    if (entry.totalNetOutputKnown() || entry.exactMissing().signum() > 0) {
                        cycleItems.putIfAbsent(key, entry);
                    }
                }
                // A cycle can be unresolved before it produces any output. Keep its required startup seeds in
                // the left-hand list so the report remains actionable instead of showing an empty plan.
                for (var seed : cycle.requiredSeed()) {
                    CraftingGraphSnapshot.MaterialNode material = neoecoae$materialFor(snapshot, seed.key());
                    var entry = new ECOCycleItemList.Entry(seed.key(),
                        material == null ? java.math.BigInteger.ZERO : material.consumedBigInteger(),
                        material == null ? java.math.BigInteger.ZERO : material.producedBigInteger(),
                        java.math.BigInteger.ZERO, java.math.BigInteger.ZERO,
                        material == null ? java.math.BigInteger.ZERO : material.missingBigInteger(),
                        cycle.executionCountKnowledge(), cycle.solveStatus(), cycle.componentId());
                    if (entry.totalNetOutputKnown() || entry.exactMissing().signum() > 0) {
                        cycleItems.putIfAbsent(seed.key(), entry);
                    }
                }
            }
            neoecoae$cycleItems = new ECOCycleItemList(List.copyOf(cycleItems.values()));
        }
    }

    @WrapOperation(
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
    private CraftingPlanSummary neoecoae$recheckCraftingPlanSummary(
            IGrid grid,
            IActionSource source,
            ICraftingPlan job,
            Operation<CraftingPlanSummary> original) {
        CraftingPlanSummary summary = original.call(grid, source, job);
        // Only an ECO plan may be re-described with AE2 stored/missing semantics. A foreign plan keeps the summary
        // its own planner published, so this wrap operation is order-independent against other projection mixins.
        return neoecoae$ownsPlan(job) ? neoecoae$recheckStoredAmounts(grid, source, summary) : summary;
    }

    /**
     * Recheck both complete and simulated ECO plans against the current network inventory. Keep the
     * plan's simulation flag unchanged: this refresh only describes
     * current availability and must not permanently disable Start when ingredients arrive later.
     */
    @Unique
    private static CraftingPlanSummary neoecoae$recheckStoredAmounts(
            IGrid grid, IActionSource source, CraftingPlanSummary summary) {
        var storage = grid.getStorageService().getInventory();
        var entries = new ArrayList<CraftingPlanSummaryEntry>(summary.getEntries().size());

        for (var entry : summary.getEntries()) {
            var key = entry.getWhat();
            long required = entry.getStoredAmount() + entry.getMissingAmount();
            long storedAmount = required;
            long missingAmount = 0L;

            if (required > 0L) {
                storedAmount = storage.extract(key, required, Actionable.SIMULATE, source);
                missingAmount = Math.max(0L, required - storedAmount);
            }

            entries.add(new CraftingPlanSummaryEntry(
                key,
                missingAmount,
                storedAmount,
                entry.getCraftAmount()
            ));
        }

        Collections.sort(entries);
        ((CraftingPlanSummaryAccessor) (Object) summary).neoecoae$setEntries(List.copyOf(entries));
        return summary;
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void logDisabledStartButton(CallbackInfo ci) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (menu.isClientSide() || result == null
                || !neoecoae$ecoPlannerAvailable && !NEConfig.ecoCraftSubmissionDebug) {
            return;
        }
        long tick = menu.getLevel().getGameTime();
        if (tick < neoecoae$nextStartDiagnosticTick) return;
        neoecoae$nextStartDiagnosticTick = tick + 20;
        PlanningStatus status = neoecoae$getPlanningStatus();
        boolean unrepresentable = status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE;
        if (unrepresentable && neoecoae$bigOrderCpu
                || !noCPU && !result.simulation() && !unrepresentable) {
            if (neoecoae$lastStartDiagnostic != null) {
                NEOECOAE_LOGGER.info("[ECO-CRAFT-SUBMIT] Server start gate cleared: container={}", menu.containerId);
                neoecoae$lastStartDiagnostic = null;
            }
            return;
        }

        IGrid grid = getGrid();
        String cpuDetails = grid != null
            && grid.getCraftingService() instanceof ECOCraftingServiceDiagnostics diagnostics
            ? diagnostics.neoecoae$describeCpuSelection(result, getActionSrc())
            : "crafting service diagnostics unavailable";
        String reason = unrepresentable ? "NO_ELIGIBLE_ECO_CPU"
            : noCPU ? "NO_MATCHING_CPU" : "SIMULATION_REQUIRES_FORCE";
        String diagnostic = reason + ":" + status + ":" + result.bytes() + ":" + result.simulation()
            + ":" + neoecoae$describeCpu(selectedCpu) + ":" + cpuDetails;
        if (diagnostic.equals(neoecoae$lastStartDiagnostic)) return;
        neoecoae$lastStartDiagnostic = diagnostic;
        NEOECOAE_LOGGER.warn(
            "[ECO-CRAFT-SUBMIT] Start disabled: reason={}, container={}, player={}, output={}, amount={}, bytes={}, simulation={}, noCPU={}, "
                + "planningStatus={}, selectedCpu={}\n{}",
            reason, menu.containerId,
            getActionSrc().player().map(player -> player.getGameProfile().getName()).orElse("<machine>"),
            result.finalOutput().what(), result.finalOutput().amount(), result.bytes(), result.simulation(), noCPU,
            status, neoecoae$describeCpu(selectedCpu), cpuDetails);
    }

    @Unique
    private static String neoecoae$describeCpu(@Nullable ICraftingCPU cpu) {
        if (cpu == null) return "<automatic>";
        return cpu.getClass().getName() + "{name="
            + (cpu.getName() == null ? "<unnamed>" : cpu.getName().getString())
            + ", storage=" + cpu.getAvailableStorage()
            + ", busy=" + cpu.isBusy()
            + ", coprocessors=" + cpu.getCoProcessors()
            + ", selectionMode=" + cpu.getSelectionMode() + "}";
    }

    /**
     * Close the confirmation-page TOCTOU window as far as possible by refreshing immediately before submission. If
     * ingredients disappeared since the page was opened, retain the menu and send the refreshed red rows instead of
     * submitting a plan that is already known to fail with MISSING_INGREDIENT.
     *
     * <p>Only a plan ECO produced may be rechecked here. Another planner can describe its stored/missing split with
     * different semantics, so re-extracting it from storage can report phantom shortages and reject submission.</p>
     */
    @Inject(
        method = "startJob",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/menu/me/crafting/CraftConfirmMenu;getGrid()Lappeng/api/networking/IGrid;"
        ),
        cancellable = true
    )
    private void neoecoae$refreshMissingIngredientsBeforeStart(CallbackInfo ci) {
        if (!neoecoae$ownsPlan(result) || result.simulation()
                || cn.dancingsnow.neoecoae.compat.extendedaeplus.EAEPForcedCrafting.isForced(result)
                || ((Object) this instanceof cn.dancingsnow.neoecoae.api.me.menu.ECOForceCraftStartSync force
                    && force.neoecoae$isForceCraftStartActive())) {
            return;
        }

        IGrid grid = getGrid();
        IActionSource source = getActionSrc();
        CraftingPlanSummary current = plan != null
            ? plan
            : CraftingPlanSummary.fromJob(grid, source, result);
        CraftingPlanSummary refreshed = neoecoae$recheckStoredAmounts(grid, source, current);
        plan = refreshed;
        var missing = refreshed.getEntries().stream().filter(entry -> entry.getMissingAmount() > 0L)
            .findFirst().orElse(null);
        if (missing == null) {
            return;
        }

        var menu = (CraftConfirmMenu) (Object) this;
        // The ECO cycle table and graph retain the planning snapshot. Sending only a refreshed AE2
        // summary leaves those views unchanged and makes a cancelled submission look like a dead button.
        menu.setAutoStart(false);
        menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(
            appeng.crafting.execution.CraftingSubmitResult.missingIngredient(
                new appeng.api.stacks.GenericStack(missing.getWhat(), missing.getMissingAmount())));
        NEOECOAE_LOGGER.warn(
            "[craft-submit] Rejected: reason=INVENTORY_RECHECK, container={}, key={}, missing={}",
            menu.containerId, missing.getWhat(), missing.getMissingAmount());
        if (menu.getPlayer() instanceof ServerPlayer player) {
            player.connection.send(new CraftConfirmPlanPacket(refreshed));
        }
        ci.cancel();
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
        long startedNanos = System.nanoTime();
        if (NEConfig.ecoCraftSubmissionDebug) {
            ECOPlanningResult attached = submittedPlan instanceof ECOCraftingPlanDiagnostics diagnostics
                    ? diagnostics.neoecoae$getPlanningResult() : null;
            NEOECOAE_LOGGER.info(
                    "[ECO-CRAFT-SUBMIT] stage=menu-dispatch container={} plan={}@{} simulation={} bytes={} "
                            + "output={} diagnostics={} target={}",
                    ((CraftConfirmMenu) (Object) this).containerId,
                    submittedPlan.getClass().getName(),
                    Integer.toHexString(System.identityHashCode(submittedPlan)),
                    submittedPlan.simulation(), submittedPlan.bytes(), submittedPlan.finalOutput(),
                    attached == null ? "none"
                            : attached.planningId() + "/" + attached.status() + "/"
                            + attached.executionRequirement(),
                    target == null ? "<automatic>"
                            : target.getClass().getName() + "@"
                            + Integer.toHexString(System.identityHashCode(target)));
        }
        ECOPlanningResult planningResult = result instanceof ECOCraftingPlanDiagnostics diagnostics
            ? diagnostics.neoecoae$getPlanningResult()
            : null;
        if (planningResult == null) planningResult = neoecoae$confirmedPlanningResult;
        if (planningResult == null) planningResult = ECOPlanningResultRegistry.find(result);
        ECOPlanningResult boundResult = planningResult;
        ICraftingSubmitResult submitResult = ECOPlanningResultRegistry.withSubmissionAlias(submittedPlan, boundResult,
            () -> original.call(service, submittedPlan, requestingMachine, target, prioritizePower, source));
        if (NEConfig.ecoCraftSubmissionDebug) {
            NEOECOAE_LOGGER.info(
                "[ECO-CRAFT-SUBMIT] stage=service-submit-return elapsedMs={} successful={} error={} detail={} output={} amount={} target={}",
                Math.max(0L, System.nanoTime() - startedNanos) / 1_000_000.0D,
                submitResult.successful(), submitResult.errorCode(), submitResult.errorDetail(),
                submittedPlan.finalOutput().what(), submittedPlan.finalOutput().amount(), neoecoae$describeCpu(target));
        }
        if (!submitResult.successful() && (NEConfig.ecoCraftSubmissionDebug
                || cn.dancingsnow.neoecoae.compat.extendedaeplus.EAEPForcedCrafting.isForced(submittedPlan))) {
            String cpuDetails = service instanceof ECOCraftingServiceDiagnostics diagnostics
                ? diagnostics.neoecoae$describeCpuSelection(submittedPlan, source)
                : "crafting service diagnostics unavailable";
            NEOECOAE_LOGGER.warn(
                "[ECO-CRAFT-SUBMIT] Submission failed: output={}, amount={}, bytes={}, target={}, error={}, detail={}\n{}",
                submittedPlan.finalOutput().what(), submittedPlan.finalOutput().amount(), submittedPlan.bytes(),
                neoecoae$describeCpu(target), submitResult.errorCode(), submitResult.errorDetail(), cpuDetails);
        }
        return submitResult;
    }

    @Inject(method = "startJob", at = @At("HEAD"))
    private void neoecoae$logStartButtonClick(CallbackInfo ci) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (menu.isClientSide() || !NEConfig.ecoCraftSubmissionDebug || result == null) return;
        neoecoae$startButtonAttemptStartedNanos = System.nanoTime();
        NEOECOAE_LOGGER.info(
            "[ECO-CRAFT-SUBMIT] stage=start-button container={} player={} output={} amount={} bytes={} simulation={} noCPU={} selectedCpu={}",
            menu.containerId,
            getActionSrc().player().map(player -> player.getGameProfile().getName()).orElse("<machine>"),
            result.finalOutput().what(), result.finalOutput().amount(), result.bytes(), result.simulation(), noCPU,
            neoecoae$describeCpu(selectedCpu));
    }

    @Inject(method = "startJob", at = @At("RETURN"))
    private void neoecoae$logStartButtonReturn(CallbackInfo ci) {
        var menu = (CraftConfirmMenu) (Object) this;
        long startedNanos = neoecoae$startButtonAttemptStartedNanos;
        neoecoae$startButtonAttemptStartedNanos = 0L;
        if (menu.isClientSide() || !NEConfig.ecoCraftSubmissionDebug || startedNanos == 0L) return;
        NEOECOAE_LOGGER.info(
            "[ECO-CRAFT-SUBMIT] stage=start-button-return container={} elapsedMs={} error={}",
            menu.containerId, Math.max(0L, System.nanoTime() - startedNanos) / 1_000_000.0D,
            String.valueOf(menu.submitError));
    }

    @Unique
    private static long neoecoae$amountFor(List<CraftingGraphSnapshot.KeyAmount> values, AEKey key) {
        return values.stream().filter(value -> value.key().equals(key)).mapToLong(
            CraftingGraphSnapshot.KeyAmount::amount).findFirst().orElse(0L);
    }

    @Unique
    private static boolean neoecoae$hasAmountFor(List<CraftingGraphSnapshot.KeyAmount> values, AEKey key) {
        return values.stream().anyMatch(value -> value.key().equals(key));
    }

    @Unique
    private static java.math.BigInteger neoecoae$exactAmountFor(
            List<cn.dancingsnow.neoecoae.crafting.planner.snapshot.ExactKeyAmount> values, AEKey key) {
        return values.stream().filter(value -> value.key().equals(key)).map(value -> value.amount().value())
            .findFirst().orElse(java.math.BigInteger.ZERO);
    }

    @Unique
    private static @Nullable CraftingGraphSnapshot.MaterialNode neoecoae$materialFor(
            CraftingGraphSnapshot snapshot, AEKey key) {
        return snapshot.nodes().stream().filter(node -> node.key().equals(key)).findFirst().orElse(null);
    }

    @Override
    public boolean neoecoae$isEcoPlannerAvailable() {
        return neoecoae$ecoPlannerAvailable;
    }

    @Override
    public boolean neoecoae$isEcoReportReady() {
        return neoecoae$ecoReportReady;
    }

    @Override
    public boolean neoecoae$shouldShowFastPlannerReport() {
        return neoecoae$ecoReportReady && neoecoae$showFastPlannerReport;
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
    public BigInteger neoecoae$getTheoreticalBytes() {
        try {
            return new BigInteger(neoecoae$theoreticalBytes);
        } catch (NumberFormatException ignored) {
            return BigInteger.ZERO;
        }
    }

    @Override
    public @Nullable PlanningStatus neoecoae$getPlanningStatus() {
        int ordinal = neoecoae$planningStatusCode - 1;
        PlanningStatus[] values = PlanningStatus.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    @Override
    public List<ECOCycleItemList.Entry> neoecoae$getCycleItems() {
        return ((CraftConfirmMenu) (Object) this).isClientSide()
                && neoecoae$receivedDiagnosticsVersion != neoecoae$diagnosticsVersion
            ? List.of() : neoecoae$cycleItems.items();
    }

    @Override
    public CraftingGraphSnapshot neoecoae$getCraftingGraphSnapshot() {
        return ((CraftConfirmMenu) (Object) this).isClientSide()
                && neoecoae$receivedDiagnosticsVersion != neoecoae$diagnosticsVersion
            ? CraftingGraphSnapshot.EMPTY : neoecoae$craftingGraph;
    }
}
