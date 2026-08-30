package cn.dancingsnow.neoecoae.impl.crafting.planner;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.growth.SinglePatternGrowthCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ActiveRouteSelector;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOCraftingPlannerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOCraftingPlannerService.class);

    private final CraftingNetworkCompiler compiler = new CraftingNetworkCompiler();
    private final CraftingGraphBuilder graphBuilder = new CraftingGraphBuilder();
    private final TarjanSccAnalyzer sccAnalyzer = new TarjanSccAnalyzer();
    private final ComponentPlanner componentPlanner = new ComponentPlanner(
        new AcyclicCraftingSolver(), SinglePatternGrowthCycleSolver.overBoundedSolver());
    private final AE2CraftingPlanBridge bridge = new AE2CraftingPlanBridge();

    /** Per-calculation session: structural compilation is reused by all AE2 CRAFT_LESS probes. */
    public final class Session {
        private final ICraftingService craftingService;
        private final AEKey goal;
        private final KeyCounter inventory;
        private final boolean cyclePlanningEnabled;
        private CompiledNetwork compiled;
        private CondensationGraph condensation;
        private ActiveRouteSelector.Selection activeSelection;

        private Session(ICraftingService craftingService, AEKey goal, KeyCounter inventory,
                boolean cyclePlanningEnabled) {
            this.craftingService = craftingService;
            this.goal = goal;
            this.inventory = copy(inventory);
            this.cyclePlanningEnabled = cyclePlanningEnabled;
        }

        public ECOPlanningResult plan(long amount, boolean simulation, ECOCancellation cancellation)
                throws InterruptedException {
            long startedNanos = System.nanoTime();
            try {
                if (compiled == null) {
                    compiled = compiler.compile(craftingService, goal, cyclePlanningEnabled, cancellation);
                }
                if (condensation == null) {
                    var graph = graphBuilder.build(compiled, cancellation);
                    var sccs = sccAnalyzer.analyze(graph, cancellation);
                    condensation = CondensationGraph.build(graph, sccs, cancellation);
                }
                ComponentPlanner.Outcome solved;
                if (cyclePlanningEnabled) {
                    if (activeSelection == null) {
                        activeSelection = componentPlanner.selectRoutes(condensation, true, cancellation);
                    }
                    solved = componentPlanner.plan(compiled, activeSelection, inventory, amount, true, cancellation);
                } else {
                    solved = componentPlanner.plan(compiled, condensation, inventory, amount, false, cancellation);
                }
                boolean multiplePaths = compiled.producers().values().stream().anyMatch(list -> list.size() > 1);
                var plan = switch (solved.status()) {
                    case SUCCESS, MISSING_ITEMS -> bridge.success(goal, amount,
                        simulation || solved.status() != PlanningStatus.SUCCESS, multiplePaths, solved.state());
                    case PARTIAL, CYCLE_UNRESOLVED -> bridge.partial(goal, amount, multiplePaths, solved.state());
                    case CYCLE_UNSUPPORTED, CANCELLED, AMOUNT_OVERFLOW -> bridge.unsupported(goal, amount);
                    case PLANNED_BUT_AMOUNT_UNREPRESENTABLE -> null;
                    default -> null;
                };
                var result = new ECOPlanningResult(solved.status(), plan, solved.trace(), solved.cycles(),
                    solved.components(), solved.executionComponentOrder(),
                    elapsedSince(startedNanos));
                logPlanningFailure(result, amount, simulation);
                attach(result);
                return result;
            } catch (InterruptedException e) {
                throw e;
            } catch (RuntimeException e) {
                LOGGER.error(
                    "[ECO-PLANNER] reason=INTERNAL_ERROR description=planner 内部异常 goal={} amount={} "
                        + "cyclePlanningEnabled={} simulation={}",
                    goal, amount, cyclePlanningEnabled, simulation, e);
                ECOPlanTrace trace = new ECOPlanTrace();
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.INTERNAL_ERROR,
                    e.getClass().getSimpleName() + ": " + e.getMessage()));
                var result = new ECOPlanningResult(PlanningStatus.INTERNAL_ERROR, null, trace, List.of(), List.of(),
                    List.of(), elapsedSince(startedNanos));
                logPlanningFailure(result, amount, simulation);
                return result;
            }
        }

        private void logPlanningFailure(ECOPlanningResult result, long amount, boolean simulation) {
            if (result.status() == PlanningStatus.SUCCESS) {
                return;
            }

            Set<String> reasons = new LinkedHashSet<>();
            boolean rejectedCandidate = false;
            for (PlannerDiagnostic diagnostic : result.trace().diagnostics()) {
                switch (diagnostic.code()) {
                    case CYCLE_DISABLED -> reasons.add(
                        "CYCLE_PLANNING_DISABLED(关闭了循环规划)");
                    case CYCLE_SEED_REQUIRED -> reasons.add(
                        "CYCLE_SEED_REQUIRED(缺少启动种子)");
                    case CYCLE_BUDGET_EXHAUSTED -> reasons.add(
                        "CYCLE_SEARCH_BUDGET_EXHAUSTED(搜索预算耗尽)");
                    case CYCLE_TOO_COMPLEX -> reasons.add(
                        "CYCLE_TOO_COMPLEX(循环过于复杂)");
                    case CYCLE_EXTERNAL_DEMAND_MISSING -> reasons.add(
                        "CYCLE_EXTERNAL_INPUT_MISSING(外部输入缺失)");
                    case CYCLE_EXTERNAL_DEMAND_UNREPRESENTABLE -> reasons.add(
                        "CYCLE_EXTERNAL_AMOUNT_UNREPRESENTABLE(AE2 无法表示外部计划数量)");
                    case CANDIDATE_REJECTED, CANDIDATE_DEFERRED_CYCLE -> rejectedCandidate = true;
                    case EXECUTION_AMOUNT_UNREPRESENTABLE -> reasons.add(
                        "EXECUTION_AMOUNT_UNREPRESENTABLE(AE2 无法表示该理论计划的数量)");
                    case INTERNAL_ERROR -> reasons.add(
                        "INTERNAL_ERROR(planner 内部异常)");
                    default -> {
                    }
                }
            }
            if (rejectedCandidate) {
                reasons.add("NO_SUITABLE_ALTERNATE_PRODUCER(没有选择到合适的备用 producer)");
            }
            if (reasons.isEmpty()) {
                reasons.add("UNCLASSIFIED(未匹配到七类原因，请查看原始 diagnostic)");
            }

            LOGGER.warn(
                "[ECO-PLANNER] planning failed goal={} amount={} cyclePlanningEnabled={} simulation={} "
                    + "status={} reasons={} elapsedNanos={}",
                goal, amount, cyclePlanningEnabled, simulation, result.status(), reasons, result.calculationNanos());
            for (PlannerDiagnostic diagnostic : result.trace().diagnostics()) {
                LOGGER.warn("[ECO-PLANNER] diagnostic code={} message={}",
                    diagnostic.code(), diagnostic.message());
            }
            result.trace().nodes().stream()
                .filter(node -> node.exactMissing().signum() > 0)
                .forEach(node -> LOGGER.warn(
                    "[ECO-PLANNER] missing key={} amount={} requested={} fromInventory={} toCraft={}",
                    node.key(), node.exactMissing(), node.exactRequested(), node.exactFromInventory(),
                    node.exactToCraft()));
            for (var component : result.components()) {
                if (component.status() == ComponentPlanningResult.Status.PLANNED
                        || component.status() == ComponentPlanningResult.Status.NOT_REQUIRED) {
                    continue;
                }
                LOGGER.warn(
                    "[ECO-PLANNER] component id={} type={} status={} cycleStatus={} externalDemandStatus={} "
                        + "requiredOutputs={} externalMissingItems={} diagnostic={}",
                    component.componentId(), component.type(), component.status(), component.cycleStatus(),
                    component.externalDemandStatus(), component.requiredOutputs(), component.externalMissingItems(),
                    component.diagnostic());
            }
        }

        private void attach(ECOPlanningResult result) {
            if ((Object) result.plan() instanceof ECOCraftingPlanDiagnostics diagnostics) {
                diagnostics.neoecoae$setPlanningResult(result);
            }
        }

        private long elapsedSince(long startedNanos) {
            long now = System.nanoTime();
            return now >= startedNanos ? now - startedNanos : 0L;
        }
    }

    public Session createSession(ICraftingService service, AEKey goal, KeyCounter inventory) {
        return new Session(service, goal, inventory, false);
    }

    public Session createSession(ICraftingService service, AEKey goal, KeyCounter inventory,
            boolean cyclePlanningEnabled) {
        return new Session(service, goal, inventory, cyclePlanningEnabled);
    }

    private static KeyCounter copy(KeyCounter source) {
        KeyCounter result = new KeyCounter();
        for (var entry : source) if (entry.getLongValue() > 0) result.add(entry.getKey(), entry.getLongValue());
        return result;
    }
}
