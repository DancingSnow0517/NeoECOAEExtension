package cn.dancingsnow.neoecoae.impl.crafting.planner;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.growth.SinglePatternGrowthCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ActiveRouteSelector;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ECOPlanMaterialValidator;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic;
import java.util.List;
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
                solved = rejectUnclosedSuccess(solved, amount);
                boolean multiplePaths = compiled.producers().values().stream().anyMatch(list -> list.size() > 1);
                var plan = switch (solved.status()) {
                    case SUCCESS, MISSING_ITEMS -> bridge.success(goal, amount,
                        simulation || solved.status() != PlanningStatus.SUCCESS, multiplePaths, solved.state());
                    case PARTIAL, CYCLE_UNRESOLVED -> bridge.partial(goal, amount, multiplePaths, solved.state());
                    case CYCLE_UNSUPPORTED, CANCELLED, AMOUNT_OVERFLOW -> bridge.unsupported(goal, amount);
                    // Keep the AE2 menu lifecycle alive with a simulation-only shell. The exact
                    // planner result is attached to this shell and rendered by the ECO screen.
                    case PLANNED_BUT_AMOUNT_UNREPRESENTABLE -> bridge.unsupported(goal, amount);
                    default -> null;
                };
                var result = new ECOPlanningResult(solved.status(), plan, solved.trace(), solved.cycles(),
                    solved.components(), solved.executionComponentOrder(),
                    elapsedSince(startedNanos));
                logPlanningSummary(result, amount, simulation);
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
                logPlanningSummary(result, amount, simulation);
                logPlanningFailure(result, amount, simulation);
                return result;
            }
        }

        private ComponentPlanner.Outcome rejectUnclosedSuccess(ComponentPlanner.Outcome solved, long amount) {
            if (solved.status() != PlanningStatus.SUCCESS) return solved;
            var issue = ECOPlanMaterialValidator.firstDeficit(solved.state(), goal, amount);
            if (issue == null) return solved;

            String key = issue.key() == null ? "<plan>" : issue.key().toString();
            String message = "Raw AE2 task vector is not materially closed: key=" + key
                + " required=" + issue.required() + " supplied=" + issue.supplied()
                + " reason=" + issue.reason();
            solved.trace().addDiagnostic(new PlannerDiagnostic(
                PlannerDiagnostic.Code.PLAN_MATERIAL_CLOSURE_INVALID, message));
            LOGGER.warn("[ECO-PLANNER] declining false SUCCESS goal={} amount={} {}",
                goal, amount, message);
            return new ComponentPlanner.Outcome(PlanningStatus.PARTIAL_UNSUPPORTED, solved.state(), solved.trace(),
                solved.cycles(), solved.components(), solved.executionComponentOrder());
        }

        private void logPlanningFailure(ECOPlanningResult result, long amount, boolean simulation) {
            if (result.status() == PlanningStatus.SUCCESS) return;

            // Ordinary missing-item / alternate-route results are expected during AE2 probing. Keep them out of
            // the warning log; an unresolved bounded cycle is the diagnostic the current investigation needs.
            for (var component : result.components()) {
                CycleSolveResult cycle = component.cycleResult();
                if (component.type() != ComponentPlanningResult.Type.CYCLIC
                        || cycle == null || cycle.status() != CycleSolveStatus.UNKNOWN_BUDGET) {
                    continue;
                }
                var metrics = cycle.metrics();
                LOGGER.warn(
                    "[ECO-PLANNER] cycle solve unknown goal={} amount={} simulation={} componentId={} "
                        + "patterns={} requiredOutputs={} cycleStatus={} cycleResultStatus={} "
                        + "requiredSeed={} seedShortfall={} externalDemand={} "
                        + "keys={} transitions={} statesVisited={} statesExpanded={} witnessLength={} "
                        + "seedLadderSteps={} stateBudgetExhausted={} firingDepthTruncated={} "
                        + "amountOverflowTruncated={} elapsedNanos={}",
                    goal, amount, simulation, component.componentId(),
                    component.patterns(),
                    component.requiredOutputs(), component.cycleStatus(), cycle.status(), cycle.requiredSeed(),
                    cycle.seedShortfall(), cycle.externalDemand(), metrics.relevantKeys(), metrics.transitions(),
                    metrics.statesVisited(), metrics.statesExpanded(), metrics.witnessLength(),
                    metrics.seedLadderSteps(), metrics.stateBudgetExhausted(), metrics.firingDepthTruncated(),
                    metrics.amountOverflowTruncated(), result.calculationNanos());
                for (var diagnostic : cycle.diagnostics()) {
                    LOGGER.warn("[ECO-PLANNER] cycle diagnostic componentId={} code={} message={}",
                        component.componentId(), diagnostic.code(), diagnostic.message());
                }
            }
        }

        private void logPlanningSummary(ECOPlanningResult result, long amount, boolean simulation) {
            if (simulation && result.status() == PlanningStatus.SUCCESS) {
                LOGGER.debug("[ECO-PLAN] planningId={} planner=ECO status={} patternKinds={} taskExecutions={} "
                        + "cycleExpected={} amount={} simulation={}", result.planningId(), result.status(),
                    result.plan() == null ? 0 : result.plan().patternTimes().size(),
                    result.plan() == null ? 0 : PlanIdentity.executionCount(result.plan().patternTimes()),
                    ECOPlanningResultRegistry.cycleExpected(result), amount, true);
                return;
            }
            LOGGER.info("[ECO-PLAN] planningId={} planner=ECO status={} patternKinds={} taskExecutions={} "
                    + "cycleExpected={} amount={} simulation={}", result.planningId(), result.status(),
                result.plan() == null ? 0 : result.plan().patternTimes().size(),
                result.plan() == null ? 0 : PlanIdentity.executionCount(result.plan().patternTimes()),
                ECOPlanningResultRegistry.cycleExpected(result), amount, simulation);
        }

        private void attach(ECOPlanningResult result) {
            if ((Object) result.plan() instanceof ECOCraftingPlanDiagnostics diagnostics) {
                diagnostics.neoecoae$setPlanningResult(result);
            }
            if (result.plan() != null) {
                ECOPlanningResultRegistry.register(result.plan(), result);
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
