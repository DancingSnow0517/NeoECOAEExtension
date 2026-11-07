package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledStructureCache;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.crafting.planner.solve.ActiveRouteSelector;
import cn.dancingsnow.neoecoae.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.crafting.planner.solve.ECOPlanMaterialValidator;
import cn.dancingsnow.neoecoae.crafting.planner.solve.PlannerInventorySnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;

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
            new AcyclicCraftingSolver(), new BoundedCycleSolver());
    private final AE2CraftingPlanBridge bridge = new AE2CraftingPlanBridge();

    /**
     * Per-calculation session: structural compilation is reused by all AE2 CRAFT_LESS probes.
     */
    public final class Session {
        private final ICraftingService craftingService;
        private final AEKey goal;
        private final KeyCounter inventory;
        private final PlannerInventorySnapshot inventorySnapshot;
        private final boolean cyclePlanningEnabled;
        private final boolean ignorePatternSubstitutions;
        private final Set<ResourceLocation> fuzzyPlanningItemIds;
        private final long planningRequestId = ECOPlanningStageLogger.nextRequestId();
        private ECOPlanningBudget planningBudget;
        private volatile CompiledNetwork compiled;
        private volatile CondensationGraph condensation;
        private volatile ActiveRouteSelector.Selection activeSelection;
        private volatile long compiledContractGeneration = -1L;
        private final Object initializationLock = new Object();

        private Session(ICraftingService craftingService, AEKey goal, KeyCounter inventory,
                        boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                        Set<ResourceLocation> fuzzyPlanningItemIds) {
            this(craftingService, goal, PlannerInventorySnapshot.of(inventory), cyclePlanningEnabled,
                    ignorePatternSubstitutions, fuzzyPlanningItemIds);
        }

        private Session(ICraftingService craftingService, AEKey goal, PlannerInventorySnapshot inventory,
                        boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                        Set<ResourceLocation> fuzzyPlanningItemIds) {
            this.craftingService = craftingService;
            this.goal = goal;
            this.inventorySnapshot = inventory;
            this.inventory = inventorySnapshot.toKeyCounter();
            this.cyclePlanningEnabled = cyclePlanningEnabled;
            this.ignorePatternSubstitutions = ignorePatternSubstitutions;
            this.fuzzyPlanningItemIds = fuzzyPlanningItemIds == null ? Set.of() : Set.copyOf(fuzzyPlanningItemIds);
        }

        public ECOPlanningResult plan(long amount, boolean simulation, ECOCancellation cancellation)
                throws InterruptedException {
            return plan(amount, simulation, cancellation, null);
        }

        /** Publish validated incumbents while the same worker continues the remaining route search. */
        public ECOPlanningResult plan(long amount, boolean simulation, ECOCancellation cancellation,
                @org.jetbrains.annotations.Nullable java.util.function.Consumer<ECOPlanningResult> onReady)
                throws InterruptedException {
            ECOCancellation callerCancellation = cancellation;
            synchronized (initializationLock) {
                if (planningBudget == null) planningBudget = new ECOPlanningBudget(ECOCancellation.NONE);
            }
            ECOPlanningBudget sharedBudget = planningBudget;
            cancellation = () -> { callerCancellation.checkpoint(); sharedBudget.checkpoint(); };
            long startedNanos = System.nanoTime();
            ECOPlanningResult[] accepted = {null};
            String budgetStage = "structural_initialization";
            try (var ignored = ECOPlanningStageLogger.open(
                    NEConfig.ecoPlanningStageDebug, planningRequestId, goal, amount, simulation)) {
                try {
                    ensureCompiled(cancellation);
                    ComponentPlanner.Outcome solved;
                    long componentStarted = ECOPlanningStageLogger.start();
                    try {
                        if (activeSelection == null) synchronized (initializationLock) {
                            if (activeSelection == null) {
                                budgetStage = "route_selection";
                                activeSelection = componentPlanner.selectRoutes(condensation, cyclePlanningEnabled, cancellation);
                            }
                        }
                        budgetStage = "component_planning";
                        solved = componentPlanner.planWithRouteFallback(compiled, condensation, activeSelection,
                                inventory, inventorySnapshot, amount, cyclePlanningEnabled, ignorePatternSubstitutions, cancellation,
                                candidate -> {
                                    ECOPlanningResult ready = buildResult(candidate, amount, simulation, startedNanos,
                                        callerCancellation);
                                    if (!ready.executable()) return false;
                                    callerCancellation.checkpoint();
                                    attach(ready);
                                    boolean first = accepted[0] == null;
                                    accepted[0] = ready;
                                    if (onReady != null) {
                                        sharedBudget.continueUntilCancelled();
                                        onReady.accept(ready);
                                    }
                                    ECOPlanningStageLogger.finish(first ? "first_executable" : "improved_executable",
                                        startedNanos, true, "backend=" + (ready.status() == PlanningStatus.SUCCESS
                                            ? "AE2_LONG" : "ECO_EXACT"));
                                    return true;
                                }, onReady != null);
                    } catch (InterruptedException | RuntimeException e) {
                        ECOPlanningStageLogger.finish("component_planning", componentStarted, false,
                                ECOPlanningStageLogger.exceptionReason(e));
                        throw e;
                    }
                    ECOPlanningStageLogger.finish("component_planning", componentStarted,
                            solved.status() == PlanningStatus.SUCCESS,
                            ECOPlanningStageLogger.resultReason(solved.status(), solved.trace()));
                    var result = onReady == null && accepted[0] != null ? accepted[0]
                        : buildResult(solved, amount, simulation, startedNanos, callerCancellation);
                    callerCancellation.checkpoint();
                    attach(result);
                    logTotal(startedNanos, result);
                    return result;
                } catch (InterruptedException e) {
                    if (accepted[0] != null) {
                        logTotal(startedNanos, accepted[0]);
                        return accepted[0];
                    }
                    ECOPlanTrace trace = new ECOPlanTrace();
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CANCELLED,
                            "Crafting candidate calculation was cancelled"));
                    var result = new ECOPlanningResult(PlanningStatus.CANCELLED, bridge.unsupported(goal, amount), trace,
                            List.of(), List.of(),
                            List.of(), elapsedSince(startedNanos));
                    attach(result);
                    logTotal(startedNanos, result);
                    return result;
                } catch (ECOPlanningBudget.Exhausted exhausted) {
                    ECOPlanTrace trace = new ECOPlanTrace();
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_BUDGET_EXHAUSTED,
                        "stage=" + budgetStage + " " + exhausted.getMessage()));
                    var result = new ECOPlanningResult(PlanningStatus.CYCLE_UNRESOLVED,
                        bridge.unsupported(goal, amount), trace, List.of(), List.of(), List.of(), elapsedSince(startedNanos));
                    attach(result);
                    logTotal(startedNanos, result);
                    return result;
                } catch (RuntimeException e) {
                    LOGGER.error("[ECO-PLANNER] calculation failed goal={} amount={} simulation={} "
                                    + "cyclePlanningEnabled={} ignorePatternSubstitutions={} fuzzyItems={}",
                            goal, amount, simulation, cyclePlanningEnabled, ignorePatternSubstitutions,
                            fuzzyPlanningItemIds, e);
                    ECOPlanTrace trace = new ECOPlanTrace();
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.INTERNAL_ERROR,
                            e.getClass().getSimpleName() + ": " + e.getMessage()));
                    var result = new ECOPlanningResult(PlanningStatus.INTERNAL_ERROR, bridge.unsupported(goal, amount), trace,
                            List.of(), List.of(),
                            List.of(), elapsedSince(startedNanos));
                    attach(result);
                    logTotal(startedNanos, result);
                    return result;
                }
            }
        }

        private ECOPlanningResult buildResult(ComponentPlanner.Outcome solved, long amount, boolean simulation,
                long startedNanos, ECOCancellation cancellation) throws InterruptedException {
            // Completed reports can be published after a search slice expires, but never after cancellation/reload.
            cancellation.checkpoint();
            if (compiledContractGeneration != AE2PatternIntrospection.reloadGeneration()) {
                throw new IllegalStateException("PATTERN_CONTRACT_CHANGED_DURING_PLANNING");
            }
            long validationStarted = ECOPlanningStageLogger.start();
            solved = rejectUnclosedSuccess(solved, amount);
            ECOPlanningStageLogger.finish("material_closure_validation", validationStarted, solved.completeCandidate(),
                ECOPlanningStageLogger.resultReason(solved.status(), solved.trace()));
            boolean multiplePaths = compiled.multiplePaths();
            long bridgeStarted = ECOPlanningStageLogger.start();
            var plan = switch (solved.status()) {
                case SUCCESS, MISSING_ITEMS -> bridge.success(goal, amount,
                    simulation || solved.status() != PlanningStatus.SUCCESS, multiplePaths, solved.state());
                case PARTIAL, CYCLE_UNRESOLVED -> bridge.partial(goal, amount, multiplePaths, solved.state());
                default -> bridge.unsupported(goal, amount);
            };
            ECOPlanningStageLogger.finish("ae2_plan_bridge", bridgeStarted, plan != null, solved.status().name());
            long provenanceStarted = ECOPlanningStageLogger.start();
            var provenance = solved.state().executionProvenance();
            ECOPlanningStageLogger.finish("provenance_freeze", provenanceStarted, true, null);
            var result = new ECOPlanningResult(solved.status(), plan, solved.trace().copy(), solved.cycles(),
                solved.components(), solved.executionComponentOrder(), elapsedSince(startedNanos), provenance);
            result.setTheoreticalBytes(solved.state().plannerBytes());
            result.setExactPatternTimes(solved.state().plannerPatternTimes());
            result.setExactMaterials(solved.state().usedAmounts(), solved.state().emittedAmounts(),
                solved.state().missingAmounts());
            result.setFuzzyPlanningItemIds(fuzzyPlanningItemIds);
            long executionStarted = ECOPlanningStageLogger.start();
            String error = result.status() == PlanningStatus.SUCCESS ? result.executionPlanError()
                : solved.completeCandidate() ? result.exactPlanError() : null;
            ECOPlanningStageLogger.finish("execution_plan_validation", executionStarted, error == null, error);
            if (result.status() == PlanningStatus.SUCCESS && error != null) {
                return rejectExecutionPlanFailure(result, amount, simulation, startedNanos);
            }
            if (error != null) result.trace().addDiagnostic(new PlannerDiagnostic(
                PlannerDiagnostic.Code.EXACT_EXECUTION_INVALID, error));
            return result;
        }

        private void ensureCompiled(ECOCancellation cancellation) throws InterruptedException {
            if (compiled != null && condensation != null
                    && compiledContractGeneration == AE2PatternIntrospection.reloadGeneration()) {
                long reused = ECOPlanningStageLogger.start();
                ECOPlanningStageLogger.finish("structural_initialization", reused, true, "REUSED");
                return;
            }
            synchronized (initializationLock) {
                long contractGeneration = AE2PatternIntrospection.reloadGeneration();
                if (compiledContractGeneration != contractGeneration) {
                    compiled = null;
                    condensation = null;
                    activeSelection = null;
                }
                long providerRevision = CompiledStructureCache.revision(craftingService);
                var cached = CompiledStructureCache.get(craftingService, providerRevision, contractGeneration, goal,
                        cyclePlanningEnabled, fuzzyPlanningItemIds);
                if (cached != null) {
                    compiled = cached.network();
                    condensation = cached.condensation();
                    compiledContractGeneration = contractGeneration;
                    ECOPlanningStageLogger.finish("structural_initialization", ECOPlanningStageLogger.start(),
                            true, "CACHED_PROVIDER_REVISION=" + providerRevision);
                    return;
                }
                if (compiled == null) {
                    long stageStarted = ECOPlanningStageLogger.start();
                    try {
                        compiled = compiler.compile(craftingService, goal, cyclePlanningEnabled,
                                fuzzyPlanningItemIds, cancellation);
                        ECOPlanningStageLogger.finish("network_compile", stageStarted, true, null);
                    } catch (InterruptedException | RuntimeException e) {
                        ECOPlanningStageLogger.finish("network_compile", stageStarted, false,
                                ECOPlanningStageLogger.exceptionReason(e));
                        throw e;
                    }
                }
                if (condensation == null) {
                    long graphStarted = ECOPlanningStageLogger.start();
                    var graph = graphBuilder.build(compiled, cancellation);
                    ECOPlanningStageLogger.finish("graph_build", graphStarted, true, null);
                    long sccStarted = ECOPlanningStageLogger.start();
                    var sccs = sccAnalyzer.analyze(graph, cancellation);
                    ECOPlanningStageLogger.finish("scc_analysis", sccStarted, true, null);
                    long condensationStarted = ECOPlanningStageLogger.start();
                    condensation = CondensationGraph.build(graph, sccs, cancellation);
                    ECOPlanningStageLogger.finish("condensation_graph", condensationStarted, true, null);
                }
                if (contractGeneration != AE2PatternIntrospection.reloadGeneration()) {
                    compiled = null;
                    condensation = null;
                    throw new IllegalStateException("PATTERN_CONTRACT_CHANGED_DURING_COMPILATION");
                }
                compiledContractGeneration = contractGeneration;
                CompiledStructureCache.put(craftingService, providerRevision, contractGeneration, goal, cyclePlanningEnabled,
                        fuzzyPlanningItemIds, new CompiledStructureCache.Structure(compiled, condensation));
            }
        }

        private void logTotal(long startedNanos, ECOPlanningResult result) {
            result.trace().diagnostics().stream()
                .filter(d -> d.code() == PlannerDiagnostic.Code.CYCLE_BUDGET_EXHAUSTED)
                .findFirst().ifPresent(d -> LOGGER.warn(
                    "[ECO-PLANNER] CYCLE_BUDGET_EXHAUSTED request={} goal={} amount={} elapsedMs={} detail={}",
                    planningRequestId, goal, result.plan().finalOutput().amount(),
                    elapsedSince(startedNanos) / 1_000_000.0D, d.message()));
            ECOPlanningStageLogger.finish("total", startedNanos, result.status() == PlanningStatus.SUCCESS,
                    ECOPlanningStageLogger.resultReason(result.status(), result.trace()));
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
            var rejected = new ECOPlanningResult(PlanningStatus.INTERNAL_ERROR, bridge.unsupported(
                    result.plan().finalOutput().what(), amount), trace, result.cycles(),
                    result.components(), result.executionComponentOrder(), elapsedSince(startedNanos));
            rejected.setFuzzyPlanningItemIds(result.fuzzyPlanningItemIds());
            attach(rejected);
            return rejected;
        }

        private ComponentPlanner.Outcome rejectUnclosedSuccess(ComponentPlanner.Outcome solved, long amount) {
            if (solved.status() != PlanningStatus.SUCCESS && !solved.completeCandidate()) return solved;
            var issue = ECOPlanMaterialValidator.firstDeficit(solved.state(), goal, amount, inventory, compiled);
            if (issue == null) return solved;

            String key = issue.key() == null ? "<plan>" : issue.key().toString();
            String message = "Raw AE2 task vector is not materially closed: key=" + key
                    + " required=" + issue.required() + " supplied=" + issue.supplied()
                    + " reason=" + issue.reason();
            var rejectedTrace = solved.trace().copy();
            rejectedTrace.addDiagnostic(new PlannerDiagnostic(
                    PlannerDiagnostic.Code.PLAN_MATERIAL_CLOSURE_INVALID, message));
            // Preserve AE2's normal missing-item contract. The first calculation can therefore render the
            // deficit as a red row and disable Start, while the exact closure diagnostic remains available in
            // the ECO report. Treating this as PARTIAL_UNSUPPORTED would invoke the native fallback, whose
            // virtual/emittable producers can incorrectly make the same material look available.
            var rejectedState = solved.state().copy();
            if (issue.key() != null) rejectedState.markMissing(issue.key(), issue.required().subtract(issue.supplied()));
            return new ComponentPlanner.Outcome(rejectedState.executionAmountIssues().isEmpty()
                    ? PlanningStatus.MISSING_ITEMS : PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
                    rejectedState, rejectedTrace,
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

    public Session createSession(ICraftingService service, AEKey goal, PlannerInventorySnapshot inventory,
                                 boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                                 Set<ResourceLocation> fuzzyPlanningItemIds) {
        return new Session(service, goal, inventory, cyclePlanningEnabled, ignorePatternSubstitutions,
                fuzzyPlanningItemIds);
    }

    /** @deprecated Planning logs are controlled by the global debug configuration. */
    @Deprecated
    public Session createSession(ICraftingService service, AEKey goal, PlannerInventorySnapshot inventory,
                                 boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                                 Set<ResourceLocation> fuzzyPlanningItemIds, boolean planningLogEnabled) {
        return createSession(service, goal, inventory, cyclePlanningEnabled, ignorePatternSubstitutions,
                fuzzyPlanningItemIds);
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

    /** @deprecated Planning logs are controlled by the global debug configuration. */
    @Deprecated
    public Session createSession(ICraftingService service, AEKey goal, KeyCounter inventory,
                                 boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                                 Set<ResourceLocation> fuzzyPlanningItemIds, boolean planningLogEnabled) {
        return new Session(service, goal, inventory, cyclePlanningEnabled, ignorePatternSubstitutions,
                fuzzyPlanningItemIds);
    }

}
