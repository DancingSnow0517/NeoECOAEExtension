package cn.dancingsnow.neoecoae.impl.crafting.planner;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
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
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerInventorySnapshot;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
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
        private final PlannerInventorySnapshot inventorySnapshot;
        private final boolean cyclePlanningEnabled;
        private final boolean ignorePatternSubstitutions;
        private final Set<ResourceLocation> fuzzyPlanningItemIds;
        private volatile CompiledNetwork compiled;
        private volatile CondensationGraph condensation;
        private volatile ActiveRouteSelector.Selection activeSelection;
        private final Object initializationLock = new Object();

        private Session(ICraftingService craftingService, AEKey goal, KeyCounter inventory,
                boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                Set<ResourceLocation> fuzzyPlanningItemIds) {
            this.craftingService = craftingService;
            this.goal = goal;
            this.inventorySnapshot = PlannerInventorySnapshot.of(inventory);
            this.inventory = inventorySnapshot.toKeyCounter();
            this.cyclePlanningEnabled = cyclePlanningEnabled;
            this.ignorePatternSubstitutions = ignorePatternSubstitutions;
            this.fuzzyPlanningItemIds = fuzzyPlanningItemIds == null ? Set.of() : Set.copyOf(fuzzyPlanningItemIds);
        }

        public ECOPlanningResult plan(long amount, boolean simulation, ECOCancellation cancellation)
                throws InterruptedException {
            long startedNanos = System.nanoTime();
            try {
                ensureCompiled(cancellation);
                ComponentPlanner.Outcome solved;
                if (cyclePlanningEnabled) {
                    if (activeSelection == null) synchronized (initializationLock) {
                        if (activeSelection == null) activeSelection = componentPlanner.selectRoutes(condensation, true, cancellation);
                    }
                    solved = componentPlanner.plan(compiled, activeSelection, inventory, inventorySnapshot, amount, true,
                        ignorePatternSubstitutions, cancellation);
                } else {
                    solved = componentPlanner.plan(compiled, condensation, inventory, inventorySnapshot, amount, false,
                        ignorePatternSubstitutions, cancellation);
                }
                solved = rejectUnclosedSuccess(solved, amount);
                boolean multiplePaths = compiled.multiplePaths();
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
                    elapsedSince(startedNanos), solved.state().executionProvenance());
                result.setTheoreticalBytes(solved.state().plannerBytes());
                result.setFuzzyPlanningItemIds(fuzzyPlanningItemIds);
                if (result.status() == PlanningStatus.SUCCESS
                        && ECOPlanningResultRegistry.cycleExpected(result)
                        && result.executionPlanError() != null) {
                    // Phase generation failed or produced an empty schedule while the plan is cycle-expected.
                    // Publishing this would create the illegal combination cycleExpected=true + phaseCount=0.
                    // Convert to an explicit FAILED result instead and let the native fallback take over.
                    return rejectExecutionPlanFailure(result, amount, simulation, startedNanos);
                }
                attach(result);
                return result;
            } catch (InterruptedException e) {
                ECOPlanTrace trace = new ECOPlanTrace();
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CANCELLED,
                    "Crafting candidate calculation was cancelled"));
                return new ECOPlanningResult(PlanningStatus.CANCELLED, null, trace, List.of(), List.of(),
                    List.of(), elapsedSince(startedNanos));
            } catch (RuntimeException e) {
                ECOPlanTrace trace = new ECOPlanTrace();
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.INTERNAL_ERROR,
                    e.getClass().getSimpleName() + ": " + e.getMessage()));
                return new ECOPlanningResult(PlanningStatus.INTERNAL_ERROR, null, trace, List.of(), List.of(),
                    List.of(), elapsedSince(startedNanos));
            }
        }

        private void ensureCompiled(ECOCancellation cancellation) throws InterruptedException {
            if (compiled != null && condensation != null) return;
            synchronized (initializationLock) {
                if (compiled == null) compiled = compiler.compile(craftingService, goal, cyclePlanningEnabled,
                    fuzzyPlanningItemIds, cancellation);
                if (condensation == null) {
                    var graph = graphBuilder.build(compiled, cancellation);
                    var sccs = sccAnalyzer.analyze(graph, cancellation);
                    condensation = CondensationGraph.build(graph, sccs, cancellation);
                }
            }
        }

        /**
         * A SUCCESS result that claims cycle expectation but has no executable plan must never reach
         * submission: it would publish the illegal combination cycleExpected=true + phaseCount=0. Convert it
         * to an explicit FAILED (INTERNAL_ERROR) result so the runner selects the native fallback instead.
         */
        private ECOPlanningResult rejectExecutionPlanFailure(ECOPlanningResult result, long amount,
                boolean simulation, long startedNanos) {
            String reason = result.executionPlanError();
            ECOPlanTrace trace = result.trace();
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.INTERNAL_ERROR,
                "EXECUTION_PLAN_FAILED:" + reason));
            return new ECOPlanningResult(PlanningStatus.INTERNAL_ERROR, null, trace, result.cycles(),
                result.components(), result.executionComponentOrder(), elapsedSince(startedNanos));
        }

        private ComponentPlanner.Outcome rejectUnclosedSuccess(ComponentPlanner.Outcome solved, long amount) {
            if (solved.status() != PlanningStatus.SUCCESS) return solved;
            var issue = ECOPlanMaterialValidator.firstDeficit(solved.state(), goal, amount, inventory, compiled);
            if (issue == null) return solved;

            String key = issue.key() == null ? "<plan>" : issue.key().toString();
            String message = "Raw AE2 task vector is not materially closed: key=" + key
                + " required=" + issue.required() + " supplied=" + issue.supplied()
                + " reason=" + issue.reason();
            solved.trace().addDiagnostic(new PlannerDiagnostic(
                PlannerDiagnostic.Code.PLAN_MATERIAL_CLOSURE_INVALID, message));
            return new ComponentPlanner.Outcome(PlanningStatus.PARTIAL_UNSUPPORTED, solved.state(), solved.trace(),
                solved.cycles(), solved.components(), solved.executionComponentOrder());
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
        return new Session(service, goal, inventory, false, false, Set.of());
    }

    public Session createSession(ICraftingService service, AEKey goal, KeyCounter inventory,
            boolean cyclePlanningEnabled) {
        return new Session(service, goal, inventory, cyclePlanningEnabled, false, Set.of());
    }

    public Session createSession(ICraftingService service, AEKey goal, KeyCounter inventory,
            boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions) {
        return new Session(service, goal, inventory, cyclePlanningEnabled, ignorePatternSubstitutions, Set.of());
    }

    public Session createSession(ICraftingService service, AEKey goal, KeyCounter inventory,
            boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
            Set<ResourceLocation> fuzzyPlanningItemIds) {
        return new Session(service, goal, inventory, cyclePlanningEnabled, ignorePatternSubstitutions,
            fuzzyPlanningItemIds);
    }

}
