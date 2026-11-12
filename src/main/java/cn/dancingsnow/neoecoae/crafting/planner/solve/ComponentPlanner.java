package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningStageLogger;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleDiagnostic;
import cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ComponentTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.CycleTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Numeric planner whose only traversal input is the SCC condensation DAG.
 */
public final class ComponentPlanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentPlanner.class);

    public record Outcome(
            PlanningStatus status,
            SolveState state,
            ECOPlanTrace trace,
            List<CycleDiagnostic> cycles,
            List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder
    ) {
        /** Numeric completeness only. Publication must additionally validate material closure and the execution contract. */
        public boolean completeCandidate() {
            return (status == PlanningStatus.SUCCESS || status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE)
                && state.missingAmounts().isEmpty() && state.unsupported.isEmpty()
                && components.stream().allMatch(component -> component.status() == ComponentPlanningResult.Status.PLANNED
                    || component.status() == ComponentPlanningResult.Status.NOT_REQUIRED);
        }
    }

    @FunctionalInterface
    public interface CandidateListener {
        /** May also receive the preferred material-shortage report; return true only for a validated executable. */
        boolean accept(Outcome candidate) throws InterruptedException;
    }

    private final AcyclicCraftingSolver acyclicSolver;
    private final CycleSolver cycleSolver;
    private final ActiveRouteSelector activeRouteSelector;
    private final int maxRouteAttempts;
    /** Alternative producer routes get at most 1/N of the session's configured work and time. */
    private static final long ALTERNATIVE_ROUTE_SHARE = 5L;

    public ComponentPlanner(AcyclicCraftingSolver acyclicSolver, CycleSolver cycleSolver) {
        this(acyclicSolver, cycleSolver, 0);
    }

    /** Package-visible budget override for deterministic route-search tests. */
    ComponentPlanner(AcyclicCraftingSolver acyclicSolver, CycleSolver cycleSolver, int maxRouteAttempts) {
        if (maxRouteAttempts < 0) throw new IllegalArgumentException("Route attempt budget must not be negative");
        this.acyclicSolver = acyclicSolver;
        this.cycleSolver = cycleSolver;
        this.activeRouteSelector = new ActiveRouteSelector();
        this.maxRouteAttempts = maxRouteAttempts;
    }

    /** Lite compilation fixes producers; enabled cycles use the existing solver without route search. */
    public Outcome planLite(CompiledNetwork network, CondensationGraph condensation,
            KeyCounter inventory, PlannerInventorySnapshot snapshot, long amount, boolean cyclePlanningEnabled,
            ECOCancellation cancellation) throws InterruptedException {
        if (cyclePlanningEnabled && !condensation.cycles().isEmpty()) {
            var fixedSelection = activeRouteSelector.select(condensation.source(), false, cancellation);
            return plan(network, fixedSelection, inventory, snapshot, amount, true, true, cancellation);
        }
        var solved = acyclicSolver.solveLite(network, condensation, snapshot, amount, cancellation);
        var physical = Set.copyOf(solved.state().plannerPatternTimes().keySet());
        var component = new ComponentPlanningResult(Map.of(network.goal(), PlannerAmount.of(Math.max(0L, amount))),
            Map.of(), 0, ComponentPlanningResult.Type.ACYCLIC, ComponentPlanningResult.Status.PLANNED,
            physical, physical, null, null, Map.of(), null, null, CycleExecutionDisposition.NOT_REQUIRED);
        solved.trace().addComponent(new ComponentTrace(0, ComponentTrace.Type.ACYCLIC, List.copyOf(network.keys())));
        List<CycleDiagnostic> cycles = new ArrayList<>();
        for (CycleComponent cycle : condensation.cycles()) {
            cancellation.checkpoint();
            solved.trace().addCycle(new CycleTrace(cycle.componentId(), cycle.members(), cycle.internalEdges(),
                Map.of(), CyclePlanningStatus.NOT_REQUIRED));
            cycles.add(new CycleDiagnostic(cycle.members(), cycle.patterns().stream().map(CompiledPattern::details).toList(),
                Map.of(), Map.of()));
        }
        return new Outcome(solved.status(), solved.state(), solved.trace(), cycles, List.of(component), List.of(0));
    }

    public Outcome plan(CompiledNetwork network, CondensationGraph condensation, KeyCounter inventory,
                        long amount, boolean cyclePlanningEnabled, ECOCancellation cancellation) throws InterruptedException {
        return plan(network, condensation, inventory, PlannerInventorySnapshot.of(inventory), amount,
                cyclePlanningEnabled, false, cancellation);
    }

    public Outcome plan(CompiledNetwork network, CondensationGraph condensation, KeyCounter inventory,
                        long amount, boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                        ECOCancellation cancellation) throws InterruptedException {
        return plan(network, condensation, inventory, PlannerInventorySnapshot.of(inventory), amount,
                cyclePlanningEnabled, ignorePatternSubstitutions, cancellation);
    }

    public Outcome plan(CompiledNetwork network, CondensationGraph condensation, KeyCounter inventory,
                        PlannerInventorySnapshot snapshot, long amount, boolean cyclePlanningEnabled,
                        boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        ActiveRouteSelector.Selection activeSelection = selectRoutes(condensation, cyclePlanningEnabled,
                cancellation);
        return planWithSeedScope(network, activeSelection, condensation.cycles(), inventory, snapshot, amount,
                cyclePlanningEnabled, ignorePatternSubstitutions, cancellation);
    }

    public ActiveRouteSelector.Selection selectRoutes(CondensationGraph condensation,
                                                      boolean cyclePlanningEnabled, ECOCancellation cancellation) throws InterruptedException {
        // Route selection and cycle avoidance are structural planning steps, not cycle solving. The toggle only
        // controls what happens after the selected active graph still contains an unavoidable cyclic SCC.
        long startedNanos = ECOPlanningStageLogger.start();
        try {
            ActiveRouteSelector.Selection selection = activeRouteSelector.select(
                    condensation.source(), true, cancellation);
            ECOPlanningStageLogger.finish("route_selection", startedNanos, true,
                    selection.acyclic() ? "ACYCLIC_ROUTE" : "CYCLIC_COMPONENTS=" + selection.cyclicComponents().size());
            return selection;
        } catch (InterruptedException | RuntimeException e) {
            ECOPlanningStageLogger.finish("route_selection", startedNanos, false,
                    ECOPlanningStageLogger.exceptionReason(e));
            throw e;
        }
    }

    public Outcome plan(CompiledNetwork network, ActiveRouteSelector.Selection activeSelection,
                        KeyCounter inventory, long amount, boolean cyclePlanningEnabled,
                        ECOCancellation cancellation) throws InterruptedException {
        return plan(network, activeSelection, inventory, amount, cyclePlanningEnabled, false, cancellation);
    }

    public Outcome plan(CompiledNetwork network, ActiveRouteSelector.Selection activeSelection,
                        KeyCounter inventory, long amount, boolean cyclePlanningEnabled,
                        boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        return plan(network, activeSelection, inventory, PlannerInventorySnapshot.of(inventory), amount,
                cyclePlanningEnabled, ignorePatternSubstitutions, cancellation);
    }

    public Outcome plan(CompiledNetwork network, ActiveRouteSelector.Selection activeSelection,
                        KeyCounter inventory, PlannerInventorySnapshot snapshot, long amount, boolean cyclePlanningEnabled,
                        boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        return planWithSeedScope(network, activeSelection, activeSelection.cyclicComponents(), inventory, snapshot,
                amount, cyclePlanningEnabled, ignorePatternSubstitutions, cancellation);
    }

    private Outcome planWithSeedScope(CompiledNetwork network, ActiveRouteSelector.Selection activeSelection,
                        List<CycleComponent> seedScope, KeyCounter inventory, PlannerInventorySnapshot snapshot,
                        long amount, boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                        ECOCancellation cancellation) throws InterruptedException {
        if (!(cancellation instanceof ECOPlanningBudget)) cancellation = new ECOPlanningBudget(cancellation);
        ExternalDemandPlanner externalDemandPlanner = new ExternalDemandPlanner(acyclicSolver);
        SeedFloors seeds = seedFloors(network, seedScope, snapshot, cyclePlanningEnabled, cancellation);
        Outcome outcome = plan(network, activeSelection, seeds.reduce(snapshot), seeds.reduce(snapshot), amount,
                cyclePlanningEnabled, ignorePatternSubstitutions, cancellation, new CycleFailureCache(cycleSolver),
                externalDemandPlanner, seeds, false);
        seeds.report(outcome.trace());
        return outcome;
    }

    /** Strict seed retention applies only when the planner may run cycles at all. */
    private SeedFloors seedFloors(CompiledNetwork network, List<CycleComponent> scope, PlannerInventorySnapshot snapshot,
                        boolean cyclePlanningEnabled, ECOCancellation cancellation) throws InterruptedException {
        return cyclePlanningEnabled ? SeedFloors.compute(network, scope, snapshot, cycleSolver, cancellation)
                : SeedFloors.NONE;
    }

    /**
     * {@code inventory} and {@code snapshot} already exclude the seeds reserved by {@code seeds}. Retention is not an
     * optional improvement: the first published plan keeps every reserved seed, or reports what is missing.
     */
    private Outcome plan(CompiledNetwork network, ActiveRouteSelector.Selection activeSelection,
                         PlannerInventorySnapshot inventory, PlannerInventorySnapshot snapshot, long amount, boolean cyclePlanningEnabled,
                         boolean ignorePatternSubstitutions, ECOCancellation cancellation,
                         CycleFailureCache failures, ExternalDemandPlanner externalDemandPlanner, SeedFloors seeds,
                         boolean stopAtFirstFailure) throws InterruptedException {
        return planRoute(network, activeSelection, inventory, snapshot, amount, cyclePlanningEnabled,
                ignorePatternSubstitutions, cancellation, failures, externalDemandPlanner, seeds, stopAtFirstFailure);
    }

    private Outcome planRoute(CompiledNetwork network, ActiveRouteSelector.Selection activeSelection,
                              PlannerInventorySnapshot inventory, PlannerInventorySnapshot snapshot, long amount, boolean cyclePlanningEnabled,
                              boolean ignorePatternSubstitutions, ECOCancellation cancellation,
                              CycleFailureCache failures, ExternalDemandPlanner externalDemandPlanner,
                              SeedFloors seeds, boolean stopAtFirstFailure) throws InterruptedException {
        AcyclicRouteResult route = solveAcyclicRoute(network, activeSelection, snapshot, amount,
                ignorePatternSubstitutions, cancellation);
        activeSelection = route.selection();
        CondensationGraph activeCondensation = activeSelection.condensation();
        final CondensationGraph resolvedCondensation = activeCondensation;
        final Map<AEKey, Integer> resolvedChoices = activeSelection.choices();
        AcyclicCraftingSolver.Outcome acyclic = route.outcome();
        ECOPlanTrace trace = acyclic.trace();
        for (var deferred : activeSelection.deferredCyclicCandidates()) {
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, deferred.producedKey(), deferred.details(),
                    0, 0, 0, 0, 0, PlanTraceNode.Selection.REJECTED, "CYCLIC_CANDIDATE"));
        }
        if (!activeSelection.deferredCyclicCandidates().isEmpty()) {
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CANDIDATE_DEFERRED_CYCLE,
                    "Cyclic candidate deferred while trying an alternate producer"));
        }
        // A fallback probe only needs the verdict, and the epilogue never upgrades a failed DAG pass to SUCCESS.
        if (stopAtFirstFailure && acyclic.status() != PlanningStatus.SUCCESS
                && !(acyclic.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                    && acyclic.state().missingAmounts().isEmpty() && acyclic.state().unsupported.isEmpty())) {
            return new Outcome(acyclic.status(), acyclic.state(), trace, List.of(), List.of(), List.of());
        }
        List<ComponentPlanningResult> componentResults = new ArrayList<>();
        List<CycleDiagnostic> cycleDiagnostics = new ArrayList<>();
        Map<Integer, Map<AEKey, PlannerAmount>> delegatedCycleDemands = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> attributedCycleReservations = new LinkedHashMap<>();
        boolean unresolvedCycle = false;

        if (!activeSelection.acyclic()) {
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CANDIDATE_DEFERRED_CYCLE,
                    "All currently available producer candidates for this route contain a cycle"));
            for (var cycle : activeSelection.cyclicComponents()) {
                for (var edge : cycle.internalEdges()) {
                    trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, edge.producer(), edge.pattern().details(),
                            0, 0, 0, 0, 0, PlanTraceNode.Selection.REJECTED, "CYCLIC_CANDIDATE"));
                }
            }
        }

        // The acyclic pass has already completed the theoretical arithmetic. A value that cannot be put into an
        // AE2 long-valued execution field is an explicit representability result, not a cycle or missing verdict.
        boolean amountUnrepresentable = acyclic.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE;
        Set<AEKey> structuralKeys = activeCondensation.source().nodes().keySet();
        // Reserved seeds that committed cycles actually borrowed. Their use is already in the shared ledger, so every
        // later inventory view must include them, but never the untouched seed of another cycle.
        Map<AEKey, PlannerAmount> borrowedSeeds = new LinkedHashMap<>();

        for (var component : activeCondensation.topologicalOrder()) {
            cancellation.checkpoint();
            trace.addComponent(new ComponentTrace(component.componentId(), component.cyclic()
                    ? ComponentTrace.Type.CYCLIC : ComponentTrace.Type.ACYCLIC, component.members()));
            if (component instanceof AcyclicComponent acyclicComponent) {
                PlannerAmount exactDemand = acyclic.state().demandAmountFor(acyclicComponent.key());
                componentResults.add(new ComponentPlanningResult(
                        exactDemand.signum() > 0 ? Map.of(acyclicComponent.key(), exactDemand) : Map.of(), Map.of(), component.componentId(),
                        ComponentPlanningResult.Type.ACYCLIC,
                        exactDemand.signum() > 0 ? ComponentPlanningResult.Status.PLANNED
                                : ComponentPlanningResult.Status.NOT_REQUIRED,
                        acyclicComponent.patterns().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()),
                        selectedExecutionPatterns(acyclic.state(), acyclicComponent.key(), structuralKeys,
                                acyclicComponent.key().equals(network.goal())),
                        null, null, Map.of(), null, null, CycleExecutionDisposition.NOT_REQUIRED));
                continue;
            }

            CycleComponent cycle = (CycleComponent) component;
            long cycleStartedNanos = ECOPlanningStageLogger.start();
            try {
                Map<AEKey, PlannerAmount> exactRequiredOutputs = cycleRequiredOutputs(network, cycle,
                        acyclic.state(), delegatedCycleDemands.getOrDefault(cycle.componentId(), Map.of()));
                Map<AEKey, Long> requiredOutputs = representable(exactRequiredOutputs);
                // Strict seed retention: a cycle that has to work for this order must also end with its minimal seed.
                // The DAG pass never saw the reserved copies. This component may borrow them as working stock (the
                // solver, its external supply and the transaction see them), and every solver target below asks the
                // witness to end with the seed again. The seed is not a delivered output: when the order does not need
                // to borrow it, the transaction reserves none of it and it simply stays in the network.
                List<SeedFloors.Floor> floors = cyclePlanningEnabled && !exactRequiredOutputs.isEmpty()
                        ? seeds.within(cycle) : List.of();
                PlannerInventorySnapshot cycleInventory = floors.isEmpty() && borrowedSeeds.isEmpty() ? inventory
                        : withReservedSeeds(inventory, floors, borrowedSeeds);
                CyclePlanningStatus cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                String diagnostic = null;
                CycleSolveResult cycleResult = null;
                CycleExternalDemandStatus externalDemandStatus = null;
                Map<AEKey, PlannerAmount> externalMissingItems = Map.of();
                CycleExecutionDisposition disposition = exactRequiredOutputs.isEmpty()
                        ? CycleExecutionDisposition.NOT_REQUIRED : CycleExecutionDisposition.BLOCKED;
                Map<AEKey, PlannerAmount> stockReservations = existingComponentReservations(
                        exactRequiredOutputs, acyclic.state(), attributedCycleReservations);
                LOGGER.debug("[ECO-CYCLE] begin component={} members={} patterns={} requiredOutputs={} "
                                + "cyclePlanningEnabled={} stockReservations={}",
                        cycle.componentId(), cycle.members().size(), cycle.patterns().size(), exactRequiredOutputs,
                        cyclePlanningEnabled, stockReservations);
                if (exactRequiredOutputs.isEmpty()) {
                    cycleStatus = CyclePlanningStatus.NOT_REQUIRED;
                } else if (!cyclePlanningEnabled) {
                    cycleStatus = CyclePlanningStatus.DISABLED;
                    diagnostic = "Cycle planning is disabled";
                    unresolvedCycle = true;
                    // The structural cycle remains in CycleDiagnostic for the right-hand list. Mirror only the
                    // currently required cycle outputs into AE2's missing pool so a disabled cycle cannot look like
                    // a valid empty plan or be submitted without enabling cycle planning on the bound host.
                    acyclic.state().markExactCycleMissing(exactRequiredOutputs);
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_DISABLED, diagnostic));
                } else {
                    // The cycle solver is a plug-in: it receives a snapshot and never touches the DAG workspace.
                    Map<AEKey, PlannerAmount> stock = relevantStock(cycle, exactRequiredOutputs.keySet(), cycleInventory,
                            acyclic.state(), stockReservations);
                    Map<AEKey, PlannerAmount> solveStock = stock;
                    Map<AEKey, PlannerAmount> solveTargets = withSeedTargets(additionalOutputTargets(exactRequiredOutputs,
                            stock, network.goal()), floors);
                    if (!solveTargets.isEmpty()) {
                        cycleResult = failures.solve(new CycleSolveRequest(cycle, solveTargets, stock,
                                        cycleInventory.unboundedKeys(), cycle.outgoingDependencies(), cycleSolveOptions(cycle), Map.of()),
                                cancellation);
                        cycleStatus = CyclePlanningStatus.of(cycleResult.status());
                        diagnostic = cycleResult.summary();
                        if (cycleResult.status() == CycleSolveStatus.SUCCESS) {
                            LOGGER.debug("[ECO-CYCLE] solve component={} status={} firings={} seed={} "
                                            + "externalDemand={} shortfall={} metrics={}",
                                    cycle.componentId(), cycleResult.status(), cycleResult.plannerTotalFirings(),
                                    cycleResult.exactRequiredSeed(), cycleResult.exactExternalDemand(),
                                    cycleResult.exactSeedShortfall(), cycleResult.metrics());
                        } else {
                            LOGGER.warn("[ECO-CYCLE] solve component={} status={} targets={} stock={} "
                                            + "seed={} externalDemand={} shortfall={} diagnostics={}",
                                    cycle.componentId(), cycleResult.status(), solveTargets, stock,
                                    cycleResult.exactRequiredSeed(), cycleResult.exactExternalDemand(),
                                    cycleResult.exactSeedShortfall(), cycleResult.diagnostics());
                        }
                        if (cycleStatus == CyclePlanningStatus.UNREPRESENTABLE) amountUnrepresentable = true;
                        trace.addDiagnostic(new PlannerDiagnostic(diagnosticCode(cycleStatus), diagnostic));
                        if (cycleResult.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT) {
                            trace.addDiagnostic(new PlannerDiagnostic(
                                    PlannerDiagnostic.Code.CYCLE_PROVEN_INFEASIBLE_AT_CURRENT_STOCK,
                                    "Component " + cycle.componentId() + " cannot satisfy " + solveTargets
                                            + " from current stock " + stock + "; seed shortfall="
                                            + cycleResult.exactSeedShortfall()));
                        }
                    }
                    ExternalDemandPlanner.Outcome external = null;
                    boolean externalFailureHandled = false;
                    boolean startupRecoveryAttempted = false;
                    Map<AEKey, PlannerAmount> plannedCycleInputs = Map.of();
                    if (cycleResult != null
                            && cycleResult.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT
                            && (!cycleResult.exactSeedShortfall().isEmpty() || !cycleResult.exactStartupCandidates().isEmpty())) {
                        startupRecoveryAttempted = true;
                        StartupRecovery recovery = recoverCycleStartup(network, activeCondensation, activeSelection.choices(),
                                cycle, cycleResult, exactRequiredOutputs, floors, stock, stockReservations,
                                cycleInventory, acyclic.state(), ignorePatternSubstitutions, failures,
                                externalDemandPlanner, cancellation);
                        cycleResult = recovery.result();
                        cycleStatus = CyclePlanningStatus.of(cycleResult.status());
                        external = recovery.external();
                        if (external != null) {
                            externalDemandStatus = external.status();
                            externalMissingItems = external.missingLeaves();
                        }
                        if (cycleStatus == CyclePlanningStatus.SOLVED) {
                            solveTargets = recovery.targets();
                            solveStock = recovery.stock();
                            plannedCycleInputs = recovery.plannedInputs();
                            diagnostic = "Verified cycle startup through an available concrete seed route";
                            trace.addDiagnostic(new PlannerDiagnostic(diagnosticCode(cycleStatus), diagnostic));
                        } else {
                            if (cycleStatus == CyclePlanningStatus.UNREPRESENTABLE) amountUnrepresentable = true;
                            if (!recovery.missingInputs().isEmpty()) acyclic.state().markExactMissing(recovery.missingInputs());
                            diagnostic = cycleResult.summary();
                            if (cycleStatus == CyclePlanningStatus.UNREPRESENTABLE) {
                                trace.addDiagnostic(new PlannerDiagnostic(diagnosticCode(cycleStatus), diagnostic));
                            }
                            if (external != null) {
                                trace.addDiagnostic(new PlannerDiagnostic(externalDiagnosticCode(external.status()), external.diagnostic()));
                            }
                        }
                    }
                    if (cycleResult != null && cycleStatus == CyclePlanningStatus.SOLVED) {
                        var search = new CycleCandidateSearch(network, activeCondensation, activeSelection.choices(),
                                cycle, exactRequiredOutputs, stock, solveStock, stockReservations, plannedCycleInputs,
                                cycleInventory, acyclic.state(), ignorePatternSubstitutions, failures,
                                externalDemandPlanner, cancellation);
                        var original = new CycleCandidateSearch.Candidate(cycleResult, solveTargets);
                        CycleCandidateSearch.Supplied supplied = search.supply(original);
                        cycleResult = supplied.result();
                        solveTargets = supplied.targets();
                        Map<AEKey, PlannerAmount> initialReservations = supplied.initialReservations();
                        Map<AEKey, PlannerAmount> additionalReservations = supplied.additionalReservations();
                        external = supplied.external();
                        externalDemandStatus = external.status();
                        externalMissingItems = external.missingLeaves();
                        if (!floors.isEmpty()) {
                            trace.addDiagnostic(external.solved()
                                    ? new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_SEED_RETAINED,
                                            "Cycle seed " + describeFloors(floors) + " stays in the network after delivery")
                                    : new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_SEED_REQUIRED,
                                            "Strict seed retention: the order must leave " + describeFloors(floors)
                                                    + "; growing it needs the missing materials"));
                        }
                        if (supplied.searched()) {
                            trace.addDiagnostic(external.solved()
                                    ? new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_SUPPLY_ALTERNATIVE,
                                            "Boundary supply failed for the first firing vector; verified alternative "
                                                    + "with import caps " + supplied.caps())
                                    : supplied.complete()
                                    ? new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_SUPPLY_SEARCH_EXHAUSTED,
                                            "Every capped alternative was closed by the cycle solver; missing items "
                                                    + "refer to the first firing vector")
                                    : new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_SUPPLY_SEARCH_INCOMPLETE,
                                            "Alternative firing vectors were not exhausted (budget/round limit/unknown "
                                                    + "verifier); missing items refer to the first firing vector and "
                                                    + "are not a proof"));
                        }
                        if (!external.solved()) {
                            LOGGER.warn("[ECO-CYCLE] external demand failed component={} status={} "
                                            + "missingLeaves={} diagnostic={}",
                                    cycle.componentId(), external.status(), externalMissingItems, external.diagnostic());
                        } else {
                            LOGGER.debug("[ECO-CYCLE] external demand solved component={} status={} "
                                            + "delegated={} directReservations={}",
                                    cycle.componentId(), external.status(), external.delegatedCycleDemands(),
                                    external.directReservations());
                        }
                        trace.addDiagnostic(new PlannerDiagnostic(externalDiagnosticCode(external.status()),
                                external.diagnostic()));
                        if (!external.solved()) {
                            // Keep the failed cycle explanatory only, but surface its concrete leaf deficits through
                            // the AE2 plan. Without this propagation a PARTIAL plan contains a large task vector with
                            // an empty missing-items counter, so confirmation reports a generic "missing materials"
                            // error and the ECO executor has no actionable schedule.
                            if (!externalMissingItems.isEmpty()) {
                                acyclic.state().markExactMissing(externalMissingItems);
                            } else if (cycleResult != null && !cycleResult.exactSeedShortfall().isEmpty()) {
                                acyclic.state().markExactMissing(cycleResult.exactSeedShortfall());
                            } else if (!exactRequiredOutputs.isEmpty()) {
                                acyclic.state().markExactCycleMissing(exactRequiredOutputs);
                            }
                            if (external.status() == CycleExternalDemandStatus.UNSUPPORTED) {
                                cycleStatus = CyclePlanningStatus.UNSUPPORTED;
                            } else if (external.status() == CycleExternalDemandStatus.UNREPRESENTABLE) {
                                cycleStatus = CyclePlanningStatus.UNREPRESENTABLE;
                                amountUnrepresentable = true;
                            } else {
                                cycleStatus = CyclePlanningStatus.INSUFFICIENT_EXTERNAL_INPUT;
                            }
                            diagnostic = external.diagnostic();
                            unresolvedCycle = true;
                            externalFailureHandled = true;
                        } else {
                            // PlannerCounter exposes an immutable view, not an immutable snapshot. Freeze it before
                            // replaceWith() so the component projection observes the committed delta exactly once.
                            Map<AEKey, PlannerAmount> usedBefore = new LinkedHashMap<>(acyclic.state().usedAmounts());
                            boolean hasFirings = hasPositiveFirings(cycleResult);
                            if (!hasFirings && !stockCoversRequiredOutputs(exactRequiredOutputs, initialReservations)) {
                                cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                                diagnostic = "Zero-firing cycle solve did not reserve its required outputs";
                                unresolvedCycle = true;
                            } else if (!acyclic.state().applyExactCycleTransaction(cycle.componentId(), exactRequiredOutputs,
                                    cycleResult, initialReservations,
                                    plannedCycleInputs, additionalReservations, cycleInventory, external.directReservations(),
                                    external.states())) {
                                cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                                diagnostic = "Cycle/external-DAG transaction validation failed";
                                unresolvedCycle = true;
                            } else {
                                recordBorrowedSeeds(floors, inventory, borrowedSeeds, acyclic.state());
                                external.delegatedCycleDemands().forEach((key, demand) -> {
                                    CycleComponent supplier = cyclicSupplier(network, resolvedCondensation,
                                            resolvedChoices, key, cycle.componentId());
                                    if (supplier == null) {
                                        throw new IllegalStateException("Delegated cycle input lost its supplier: " + key);
                                    }
                                    delegatedCycleDemands
                                            .computeIfAbsent(supplier.componentId(), ignored -> new LinkedHashMap<>())
                                            .merge(key, demand, PlannerAmount::add);
                                    var boundary = cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand
                                            .boundary(cycle.componentId(), key, demand);
                                    acyclic.state().provenance.register(boundary);
                                    acyclic.state().provenance.allocate(boundary, key,
                                            new cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource.CycleOutput(
                                                    supplier.componentId()), demand);
                                });
                                stockReservations = mergeDemands(stockReservations,
                                        reservationDelta(usedBefore, acyclic.state().usedAmounts()));
                                disposition = hasFirings ? cycleExecutionDisposition(cycle, cycleResult)
                                        : stockCoversRequiredOutputs(exactRequiredOutputs, stockReservations)
                                        ? CycleExecutionDisposition.STOCK_SATISFIED
                                        : CycleExecutionDisposition.BLOCKED;
                                if (disposition == CycleExecutionDisposition.BLOCKED) {
                                    cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                                    diagnostic = "Zero-firing cycle solve did not reserve its required outputs";
                                    unresolvedCycle = true;
                                } else {
                                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_SOLVED,
                                            "Cycle and external DAG merged with " + cycleResult.plannerTotalFirings()
                                                    + " cycle firing(s)"));
                                }
                            }
                        }
                    }
                    if (cycleResult != null && cycleStatus != CyclePlanningStatus.SOLVED
                            && cycleStatus != CyclePlanningStatus.NOT_REQUIRED) {
                        unresolvedCycle = true;
                        if (external != null && !external.solved()) {
                            if (!externalFailureHandled) {
                                // An unproducible startup seed is still a concrete material deficit. The graph
                                // already exposes seedShortfall; keep AE2's missing pool consistent with it.
                                if (!externalMissingItems.isEmpty()) {
                                    acyclic.state().markExactMissing(externalMissingItems);
                                } else if (!startupRecoveryAttempted && !cycleResult.exactSeedShortfall().isEmpty()) {
                                    acyclic.state().markExactMissing(cycleResult.exactSeedShortfall());
                                } else if (!startupRecoveryAttempted && !exactRequiredOutputs.isEmpty()) {
                                    acyclic.state().markExactCycleMissing(exactRequiredOutputs);
                                }
                                if (external.status() == CycleExternalDemandStatus.UNSUPPORTED) {
                                    cycleStatus = CyclePlanningStatus.UNSUPPORTED;
                                } else if (external.status() == CycleExternalDemandStatus.UNREPRESENTABLE) {
                                    cycleStatus = CyclePlanningStatus.UNREPRESENTABLE;
                                    amountUnrepresentable = true;
                                } else {
                                    cycleStatus = CyclePlanningStatus.INSUFFICIENT_EXTERNAL_INPUT;
                                }
                                diagnostic = external.diagnostic();
                            }
                        }
                    }
                    if (external != null && !external.solved()) external.addMaterialPreview(acyclic.state(), trace);
                }
                List<cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge> externalEdges = cycle
                        .outgoingDependencies().stream().flatMap(dependency -> dependency.relationships().stream()).toList();
                trace.addCycle(new CycleTrace(cycle.componentId(), cycle.members(), cycle.internalEdges(), externalEdges,
                        requiredOutputs, cycleStatus, cycleResult));
                trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.CYCLE_GROUP, null, null, 0, 0, 0, 0,
                        cycleResult == null ? 0 : traceLong(cycleResult.plannerTotalFirings()),
                        switch (cycleStatus) {
                            case NOT_REQUIRED -> PlanTraceNode.Selection.NOT_APPLICABLE;
                            case SOLVED -> PlanTraceNode.Selection.SELECTED;
                            default -> PlanTraceNode.Selection.UNSUPPORTED;
                        },
                        cycleStatus.name()));
                if (disposition == CycleExecutionDisposition.STOCK_SATISFIED
                        || (disposition == CycleExecutionDisposition.NOT_REQUIRED
                                && cycleStatus == CyclePlanningStatus.NOT_REQUIRED)) {
                    acyclic.state().provenance.resolveStockSatisfiedCycle(cycle.componentId());
                }
                componentResults.add(new ComponentPlanningResult(exactRequiredOutputs, stockReservations, cycle.componentId(),
                        ComponentPlanningResult.Type.CYCLIC,
                        componentStatus(exactRequiredOutputs, cycleStatus, disposition),
                        cycle.patterns().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()),
                        selectedCycleExecutionPatterns(cycleResult),
                        cycleStatus, externalDemandStatus, projectReservations(externalMissingItems), diagnostic, cycleResult,
                        disposition));
                if (disposition != CycleExecutionDisposition.BLOCKED
                        && disposition != CycleExecutionDisposition.NOT_REQUIRED) {
                    stockReservations.forEach((key, reserved) ->
                            attributedCycleReservations.merge(key, reserved, PlannerAmount::add));
                }
                if (cycleStatus != CyclePlanningStatus.SOLVED && cycleStatus != CyclePlanningStatus.NOT_REQUIRED) {
                    LOGGER.warn("[ECO-CYCLE] final component={} status={} disposition={} requiredOutputs={} "
                                    + "externalStatus={} missingLeaves={} diagnostic={}",
                            cycle.componentId(), cycleStatus, disposition, requiredOutputs,
                            externalDemandStatus, externalMissingItems, diagnostic);
                } else {
                    LOGGER.debug("[ECO-CYCLE] final component={} status={} disposition={} requiredOutputs={} "
                                    + "reservations={}",
                            cycle.componentId(), cycleStatus, disposition, requiredOutputs, stockReservations);
                }
                cycleDiagnostics.add(diagnostic(cycle, cycleInventory.toKeyCounter(), cycleResult, trace));
                ECOPlanningStageLogger.finish("cycle_calculation[" + cycle.componentId() + "]", cycleStartedNanos,
                        cycleStatus == CyclePlanningStatus.SOLVED || cycleStatus == CyclePlanningStatus.NOT_REQUIRED,
                        diagnostic == null ? cycleStatus.name() : cycleStatus + ":" + diagnostic);
            } catch (InterruptedException | RuntimeException e) {
                ECOPlanningStageLogger.finish("cycle_calculation[" + cycle.componentId() + "]", cycleStartedNanos,
                        false, ECOPlanningStageLogger.exceptionReason(e));
                throw e;
            }
            // An unresolved cycle always ends in a non-SUCCESS status; later components cannot change that.
            if (stopAtFirstFailure && unresolvedCycle) break;
        }

        PlanningStatus status = routeStatus(acyclic, amountUnrepresentable, unresolvedCycle, componentResults);
        validateProvenanceCoverage(network, acyclic.state(), componentResults, trace);
        return new Outcome(status, acyclic.state(), trace, cycleDiagnostics,
                componentResults, activeCondensation.executionOrder().stream()
                .map(c -> c.componentId()).toList());
    }

    private record StartupRecovery(CycleSolveResult result, Map<AEKey, PlannerAmount> targets,
            Map<AEKey, PlannerAmount> stock, Map<AEKey, PlannerAmount> plannedInputs,
            @Nullable ExternalDemandPlanner.Outcome external, Map<AEKey, PlannerAmount> missingInputs) {}

    /** Speculative only: neither a rejected witness nor cancellation may commit external work to the DAG state. */
    private StartupRecovery recoverCycleStartup(CompiledNetwork network, CondensationGraph condensation,
            Map<AEKey, Integer> choices, CycleComponent cycle, CycleSolveResult proposal,
            Map<AEKey, PlannerAmount> requiredOutputs, List<SeedFloors.Floor> floors, Map<AEKey, PlannerAmount> stock,
            Map<AEKey, PlannerAmount> stockReservations, PlannerInventorySnapshot inventory, SolveState state,
            boolean ignorePatternSubstitutions, CycleFailureCache failures, ExternalDemandPlanner externalPlanner,
            ECOCancellation cancellation) throws InterruptedException {
        ExternalDemandPlanner.Outcome external = null;
        CycleSolveResult recoveryFailure = null;
        StartupRecovery seededDeficit = null;
        Map<AEKey, PlannerAmount> unresolvedSeed = Map.of();
        List<Map<AEKey, PlannerAmount>> candidates = new ArrayList<>();
        if (!proposal.exactSeedShortfall().isEmpty()) candidates.add(proposal.exactSeedShortfall());
        for (Map<AEKey, PlannerAmount> candidate : proposal.exactStartupCandidates()) {
            if (!candidate.isEmpty() && !candidates.contains(candidate)) candidates.add(candidate);
        }
        for (Map<AEKey, PlannerAmount> initialCandidate : candidates) {
            Map<AEKey, PlannerAmount> candidate = initialCandidate;
            Set<Map<AEKey, PlannerAmount>> attemptedSeeds = new LinkedHashSet<>();
            while (attemptedSeeds.add(Map.copyOf(candidate))) {
                cancellation.checkpoint();
                // Prove that the concrete seed exists or can be made before probing its hypothetical circuit.
                Set<AEKey> seedDelegates = delegatedCycleInputs(network, condensation, choices, cycle, candidate.keySet());
                Map<AEKey, PlannerAmount> heldSeed = new LinkedHashMap<>();
                for (AEKey key : candidate.keySet()) {
                    PlannerAmount present = stock.getOrDefault(key, PlannerAmount.ZERO);
                    if (present.signum() > 0) heldSeed.put(key, present);
                }
                // First distinguish an unavailable startup seed from a seed that can be made but whose
                // recurring ingredients cannot cover the order. Both deficits remain visible in the report.
                var seedAttempt = externalPlanner.solveExactDemands(network, cycle, candidate, inventory, state,
                        reservationRemainder(heldSeed, stockReservations), seedDelegates,
                        ignorePatternSubstitutions, cancellation);
                if (!seedAttempt.solved()) {
                    if (external == null || recoveryFailure != null && !candidate.equals(initialCandidate)) {
                        external = seedAttempt;
                        if (completeMaterialDeficit(proposal) && candidate.equals(proposal.exactSeedShortfall())) {
                            // The witness keeps the startup amount separate from the full-order fuel demand.
                            // Reuse the shared external DAG so seed and fuel do not double-count raw stock.
                            var demands = mergeDemands(candidate, proposal.positiveExactExternalDemand());
                            external = externalPlanner.solveExactDemands(network, cycle, demands, inventory, state,
                                reservationRemainder(cycleInitialReservations(requiredOutputs, stock, proposal),
                                    stockReservations),
                                delegatedCycleInputs(network, condensation, choices, cycle, demands.keySet()),
                                ignorePatternSubstitutions, cancellation);
                        }
                    }
                    break;
                }
                Map<AEKey, PlannerAmount> projectedStock = mergeDemands(stock, candidate);
                var targets = withSeedTargets(additionalOutputTargets(requiredOutputs, projectedStock, network.goal()), floors);
                CycleSolveResult recovered = failures.solve(new CycleSolveRequest(cycle, targets, projectedStock, inventory.unboundedKeys(),
                        cycle.outgoingDependencies(), cycleSolveOptions(cycle), Map.of()), cancellation);
                CycleSolveResult reservationSource = recovered.status() == CycleSolveStatus.SUCCESS ? recovered : proposal;
                Map<AEKey, PlannerAmount> demands = mergeDemands(reservationSource.positiveExactExternalDemand(), candidate);
                Map<AEKey, PlannerAmount> reservations = cycleInitialReservations(requiredOutputs, stock, reservationSource);
                Set<AEKey> delegated = delegatedCycleInputs(network, condensation, choices, cycle, demands.keySet());
                var recoveryAttempt = externalPlanner.solveExactDemands(network, cycle, demands, inventory, state,
                        reservationRemainder(reservations, stockReservations), delegated,
                        ignorePatternSubstitutions, cancellation);
                if (external == null) external = recoveryAttempt;
                if (recovered.status() == CycleSolveStatus.SUCCESS) {
                    // Keep the verified startup even when its fuel is missing. Other startup choices may
                    // still succeed, but the old seed-shortfall proposal must not replace this material report.
                    var recoveredStartup = new StartupRecovery(recovered, targets, projectedStock, candidate, null, Map.of());
                    if (recoveryAttempt.solved()) return recoveredStartup;
                    if (seededDeficit == null) seededDeficit = recoveredStartup;
                }
                if (!recoveryAttempt.solved()) break;
                if (recoveryFailure == null && (!completeMaterialDeficit(proposal) || completeMaterialDeficit(recovered))) {
                    recoveryFailure = recovered;
                    unresolvedSeed = candidate;
                    external = recoveryAttempt;
                }
                // An order-sized prefix deficit may need a larger seed; retry from the same original inventory.
                if (recovered.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT
                        && completeMaterialDeficit(recovered) && !recovered.exactSeedShortfall().isEmpty()) {
                    candidate = mergeDemands(candidate, recovered.exactSeedShortfall());
                    continue;
                }
                break;
            }
        }
        if (seededDeficit != null) return seededDeficit;
        CycleSolveResult result = recoveryFailure == null ? proposal : recoveryFailure;
        Map<AEKey, PlannerAmount> missing = recoveryFailure != null
                && recoveryFailure.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT
                && (external == null || external.solved())
                ? mergeDemands(unresolvedSeed, recoveryFailure.exactSeedShortfall()) : Map.of();
        return new StartupRecovery(result, Map.of(), Map.of(), Map.of(), external, missing);
    }

    private record AcyclicRouteResult(ActiveRouteSelector.Selection selection,
                                      AcyclicCraftingSolver.Outcome outcome) {}

    /** Reclassify numeric producer fallbacks from the unchanged snapshot before any cycle can commit. */
    private AcyclicRouteResult solveAcyclicRoute(CompiledNetwork network, ActiveRouteSelector.Selection activeSelection,
            PlannerInventorySnapshot snapshot, long amount, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        CondensationGraph activeCondensation;
        AcyclicCraftingSolver.Outcome attempt;
        Set<Map<AEKey, Integer>> classifiedRoutes = new ObjectOpenHashSet<>();
        while (true) {
            activeCondensation = activeSelection.condensation();
            List<AEKey> dagOrder = activeCondensation.topologicalOrder().stream()
                    .filter(AcyclicComponent.class::isInstance)
                    .map(AcyclicComponent.class::cast).map(AcyclicComponent::key).toList();
            var cycleOwnedPatterns = activeSelection.cyclicComponents().stream()
                    .flatMap(cycle -> cycle.patterns().stream()).map(CompiledPattern::details)
                    .collect(java.util.stream.Collectors.toSet());
            long acyclicStartedNanos = ECOPlanningStageLogger.start();
            try {
                attempt = acyclicSolver.solve(network, new AcyclicRoutePlan(dagOrder), snapshot, amount,
                        activeSelection.choices(), cycleOwnedPatterns, ignorePatternSubstitutions, cancellation);
                ECOPlanningStageLogger.finish("non_cycle_calculation", acyclicStartedNanos,
                        attempt.status() == PlanningStatus.SUCCESS,
                        ECOPlanningStageLogger.resultReason(attempt.status(), attempt.trace()));
            } catch (InterruptedException | RuntimeException e) {
                ECOPlanningStageLogger.finish("non_cycle_calculation", acyclicStartedNanos, false,
                        ECOPlanningStageLogger.exceptionReason(e));
                throw e;
            }
            if (attempt.cyclicRouteChoices().isEmpty()
                    || !classifiedRoutes.add(attempt.cyclicRouteChoices())) break;
            // Numeric fallback may introduce a multi-recipe SCC. Reclassify the exact attempted route
            // and restart from the untouched inventory; do not hand it back to the native DAG planner.
            var universe = new cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder()
                    .build(network, cancellation);
            activeSelection = activeRouteSelector.selectWithChoices(universe,
                    attempt.cyclicRouteChoices(), cancellation);
        }
        return new AcyclicRouteResult(activeSelection, attempt);
    }

    /** Merge every owner in the exact domain before deriving the solver's bounded compatibility view. */
    private static Map<AEKey, PlannerAmount> cycleRequiredOutputs(CompiledNetwork network, CycleComponent cycle,
            SolveState state, Map<AEKey, PlannerAmount> delegatedDemands) {
        Map<AEKey, PlannerAmount> required = new LinkedHashMap<>();
        for (AEKey member : cycle.members()) {
            PlannerAmount demand = state.demandAmountFor(member);
            if (demand.signum() > 0) required.put(member, demand);
        }
        var ownedDetails = cycle.patterns().stream().map(CompiledPattern::details)
                .collect(java.util.stream.Collectors.toSet());
        for (var selected : state.selected.entrySet()) {
            if (!ownedDetails.contains(selected.getValue().details())) continue;
            PlannerAmount demand = state.demandAmountFor(selected.getKey());
            if (demand.signum() > 0) required.put(selected.getKey(), demand);
        }
        delegatedDemands.forEach((key, demand) -> {
            if (demand.signum() > 0) required.merge(key, demand, PlannerAmount::add);
        });
        // A creative input already reserved by the DAG is fulfilled. Explicit final outputs must still be crafted.
        required.entrySet().removeIf(entry -> !entry.getKey().equals(network.goal())
                && state.stored.isUnbounded(entry.getKey())
                && state.used.get(entry.getKey()).compareTo(entry.getValue()) >= 0);
        return required;
    }

    private static PlanningStatus routeStatus(AcyclicCraftingSolver.Outcome acyclic, boolean amountUnrepresentable,
            boolean unresolvedCycle, List<ComponentPlanningResult> componentResults) {
        PlanningStatus status = acyclic.status();
        if (amountUnrepresentable || !acyclic.state().executionAmountIssues().isEmpty()) {
            status = PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE;
        }
        if (unresolvedCycle && (status == PlanningStatus.SUCCESS || status == PlanningStatus.MISSING_ITEMS)) {
            status = acyclic.state().hasPlannedCrafting() || status == PlanningStatus.MISSING_ITEMS
                    ? PlanningStatus.PARTIAL : PlanningStatus.CYCLE_UNRESOLVED;
            if (!acyclic.state().missingAmounts().isEmpty() && componentResults.stream()
                    .filter(c -> c.type() == ComponentPlanningResult.Type.CYCLIC)
                    .filter(c -> c.cycleDisposition() == CycleExecutionDisposition.BLOCKED)
                    // A verified full-order witness can be blocked solely by its external ingredients.
                    // It proves a material shortage just as a full-order deficit witness does.
                    .allMatch(c -> c.cycleStatus() == CyclePlanningStatus.INSUFFICIENT_EXTERNAL_INPUT
                        && (c.cycleResult() != null && c.cycleResult().status() == CycleSolveStatus.SUCCESS
                            || completeMaterialDeficit(c.cycleResult()))
                        && (c.externalDemandStatus() == CycleExternalDemandStatus.MISSING
                            || c.externalDemandStatus() == CycleExternalDemandStatus.FORBIDDEN_ROUTE))) {
                status = PlanningStatus.MISSING_ITEMS;
            }
        }
        return status;
    }

    private static boolean completeMaterialDeficit(CycleSolveResult result) {
        return result != null && result.diagnostics().stream().anyMatch(d -> d.code()
            == cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveDiagnostic.Code.FULL_ORDER_MATERIAL_DEFICIT);
    }

    /** Retry producer routes, including cyclic alternatives, when the preferred route cannot be supplied. */
    public Outcome planWithCycleFallback(CompiledNetwork network, CondensationGraph universe,
                        ActiveRouteSelector.Selection activeSelection, KeyCounter inventory,
                        PlannerInventorySnapshot snapshot, long amount, boolean ignorePatternSubstitutions,
                        ECOCancellation cancellation) throws InterruptedException {
        return planWithRouteFallback(network, universe, activeSelection, inventory, snapshot, amount,
                true, ignorePatternSubstitutions, cancellation);
    }

    /** Preserve supplied producer choices; recover unavailable combinations independently of cycle support. */
    public Outcome planWithRouteFallback(CompiledNetwork network, CondensationGraph universe,
                        ActiveRouteSelector.Selection activeSelection, KeyCounter inventory,
                        PlannerInventorySnapshot snapshot, long amount, boolean cyclePlanningEnabled,
                        boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        return planWithRouteFallback(network, universe, activeSelection, inventory, snapshot, amount,
            cyclePlanningEnabled, ignorePatternSubstitutions, cancellation, null, false);
    }

    public Outcome planWithRouteFallback(CompiledNetwork network, CondensationGraph universe,
                        ActiveRouteSelector.Selection activeSelection, KeyCounter inventory,
                        PlannerInventorySnapshot snapshot, long amount, boolean cyclePlanningEnabled,
                        boolean ignorePatternSubstitutions, ECOCancellation cancellation,
                        @Nullable CandidateListener listener, boolean improvePublishedRoute) throws InterruptedException {
        if (!(cancellation instanceof ECOPlanningBudget)) cancellation = new ECOPlanningBudget(cancellation);
        // Seeds of self-growing cycles are structural: route choice must not decide whether they survive. Every
        // route and joint model below plans with the stock without them; planned cycles borrow them back.
        SeedFloors seeds = seedFloors(network, universe.cycles(), snapshot, cyclePlanningEnabled, cancellation);
        CandidateListener reporting = listener == null ? null : candidate -> {
            Outcome report = new Outcome(candidate.status(), candidate.state(), candidate.trace().copy(),
                candidate.cycles(), candidate.components(), candidate.executionComponentOrder());
            seeds.report(report.trace());
            return listener.accept(report);
        };
        Outcome outcome = planWithRouteFallback(network, universe, activeSelection, seeds.reduce(snapshot),
                seeds.reduce(snapshot), snapshot, amount, cyclePlanningEnabled, ignorePatternSubstitutions,
                cancellation, seeds, reporting, improvePublishedRoute);
        seeds.report(outcome.trace());
        return outcome;
    }

    /**
     * {@code snapshot} excludes reserved seeds and drives every plan. {@code owned} is the full stock: startup and
     * optimistic-quantity proofs must see the seeds, because a planned cycle may borrow them as working stock, and
     * a proof over less stock than the planner may use would declare feasible orders impossible.
     */
    private Outcome planWithRouteFallback(CompiledNetwork network, CondensationGraph universe,
                        ActiveRouteSelector.Selection activeSelection, PlannerInventorySnapshot inventory,
                        PlannerInventorySnapshot snapshot, PlannerInventorySnapshot owned, long amount,
                        boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions, ECOCancellation cancellation,
                        SeedFloors seeds, @Nullable CandidateListener listener, boolean improvePublishedRoute)
            throws InterruptedException {
        RouteQuantityProof.Prepared supply = null;
        // A quantity bound can prune alternatives, but cannot identify a startup seed or the missing leaves.
        // Always expand the preferred route at the requested amount before returning any supply proof.
        // The ordinary route follows AE's producer order. Optional optimization begins only after it can publish.
        CycleFailureCache failures = new CycleFailureCache(cycleSolver);
        ExternalDemandPlanner externalDemandPlanner = new ExternalDemandPlanner(acyclicSolver);
        Outcome preferred = plan(network, activeSelection, inventory, snapshot, amount, cyclePlanningEnabled,
            ignorePatternSubstitutions, cancellation, failures, externalDemandPlanner, seeds, false);
        boolean executable = offerCandidate(preferred, listener);
        // The full preferred walk owns the material table even when it cannot execute. Speculative route
        // probes below may stop at their first failure and must never be offered as complete shortage reports.
        if (!preferred.completeCandidate() && improvePublishedRoute && listener != null
                && !preferred.state().missingAmounts().isEmpty()
                && (preferred.status() == PlanningStatus.MISSING_ITEMS
                    || preferred.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE)) {
            listener.accept(preferred);
        }
        // No optional work is needed when there is no competing producer route.
        if (executable && (!improvePublishedRoute || !network.multiplePaths())) return preferred;
        try {
            cancellation.checkpoint();
            // Preserve the completed route's material report if this proof itself exhausts the session.
            // It runs before the heavyweight joint model and whole-route alternatives.
            if (!executable
                    && RouteAvailabilityProof.goalUnreachable(network, owned, cancellation)) {
                preferred.trace().addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.ROUTE_PROVEN_UNREACHABLE,
                    "No producer route can start from the current stock, even with quantities and consumption ignored"));
                return preferred;
            }
            if (!executable) {
                if (supply == null) supply = RouteQuantityProof.prepareBounded(network, owned, cancellation);
                var bound = supply == null ? null : supply.insufficientOutput(amount, Map.of(), Set.of(), cancellation);
                if (bound != null) {
                    preferred.trace().addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.ROUTE_QUANTITY_BOUND,
                        "Even optimistic supply across all producers yields at most " + bound
                            + " of the goal, below requested " + amount
                            + "; " + supply.explanation() + "; retaining the completed route's material report"));
                    return preferred;
                }
                // The per-key bound above counts every output of a byproduct-rich firing separately. One column per
                // physical pattern does not, and its infeasibility covers every route, cycle and seed choice.
                if (MaterialBalanceProof.infeasible(network, snapshot, amount, seeds.shortfallTargets(),
                        ignorePatternSubstitutions, cancellation)) {
                    preferred.trace().addDiagnostic(new PlannerDiagnostic(
                        PlannerDiagnostic.Code.MATERIAL_BALANCE_INFEASIBLE,
                        "Exact material balance over every compiled pattern cannot cover " + amount
                            + " of the goal from current stock" + (seeds.isEmpty() ? ""
                                : " while keeping cycle seeds " + seeds)
                            + "; alternative routes are not searched"));
                    return preferred;
                }
            }
            if (maxRouteAttempts == 0) {
                Outcome optimized = new JointRouteOptimizer().optimize(network, snapshot, amount, preferred,
                    cyclePlanningEnabled, cancellation);
                if (optimized != preferred && (!executable || betterRoute(optimized, preferred))
                        && offerCandidate(optimized, listener)) {
                    preferred = optimized;
                    executable = true;
                }
            }
            if (executable && !improvePublishedRoute) return preferred;
            return searchAlternativeRoutes(network, universe, activeSelection, inventory, snapshot, amount,
                cyclePlanningEnabled, ignorePatternSubstitutions, cancellation, failures, preferred,
                externalDemandPlanner, supply, seeds, listener, improvePublishedRoute, executable);
        } catch (cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget.Exhausted exhausted) {
            // The interrupted alternative is speculative. The completed route still owns a valid
            // material report and cycle diagnostics; never replace those with an empty shell.
            preferred.trace().addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED,
                "stage=alternative_routes " + exhausted.getMessage()
                    + "; retaining the completed route's material report"));
            return incompleteSearch(preferred);
        }
    }

    private Outcome searchAlternativeRoutes(CompiledNetwork network, CondensationGraph universe,
                        ActiveRouteSelector.Selection activeSelection, PlannerInventorySnapshot inventory,
                        PlannerInventorySnapshot snapshot, long amount, boolean cyclePlanningEnabled,
                        boolean ignorePatternSubstitutions,
                        ECOCancellation cancellation, CycleFailureCache failures, Outcome preferred,
                        ExternalDemandPlanner externalDemandPlanner, RouteQuantityProof.Prepared supply,
                        SeedFloors seeds, @Nullable CandidateListener listener, boolean improvePublishedRoute,
                        boolean executable)
            throws InterruptedException {
        ECOPlanningBudget sessionBudget = (ECOPlanningBudget) cancellation;
        // Only a validated executable incumbent starts the short optimization window.
        // Failed-route recovery keeps its original allowance until it finds one.
        if (!executable || !improvePublishedRoute) cancellation = sessionBudget.slice(
            Math.max(1L, NEConfig.ecoPlanningMaxWork / ALTERNATIVE_ROUTE_SHARE),
            Math.max(1L, NEConfig.ecoPlanningMaxMillis) * 1_000_000L / ALTERNATIVE_ROUTE_SHARE);
        Map<AEKey, Integer> baseline = new Object2ObjectLinkedOpenHashMap<>();
        // The numeric pass can retry an acyclic producer, while cycle-owned keys are deliberately
        // absent from SolveState.selected because their work is deferred to the cycle transaction.
        // Start from the structural route and overlay only the concrete choices discovered by the
        // numeric pass. This gives every graph key a real producer index before routes are
        // searched, including keys owned by a deferred cycle.
        for (AEKey key : universe.source().nodes().keySet()) {
            cancellation.checkpoint();
            List<CompiledPattern> candidates = network.fastProducersOf(key);
            if (candidates.isEmpty()) continue;
            int routeChoice = normalizeChoice(activeSelection.choices().getOrDefault(key, 0), candidates.size());
            CompiledPattern selectedPattern = preferred.state().selected.get(key);
            int numericChoice = producerIndex(candidates, selectedPattern);
            int selected = numericChoice >= 0 ? numericChoice : routeChoice;
            baseline.put(key, selected);
        }
        long startedNanos = ECOPlanningStageLogger.start();
        var routes = new RouteSearch(network, baseline, (choices, fixed, check) -> supply != null
            && supply.rejectsBranch(amount, choices, fixed, check));
        int attempts = 1;
        boolean success = executable;
        Outcome best = preferred;
        ECOPlanTrace resultTrace = preferred.trace();
        long graphNanos = 0L;
        long candidateNanos = 0L;
        int limitedCandidates = 0;
        try {
            Map<AEKey, Integer> choices;
            while ((choices = routes.next(cancellation)) != null) {
                if (maxRouteAttempts > 0 && attempts >= maxRouteAttempts) {
                    best.trace().addDiagnostic(new PlannerDiagnostic(
                        PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED,
                        "Producer route search stopped after " + attempts
                            + " attempts; unvisited combinations may still be feasible"));
                    return incompleteSearch(best);
                }
                attempts++;
                long graphStarted = ECOPlanningStageLogger.start();
                ActiveRouteSelector.Selection candidate = activeRouteSelector.selectWithChoices(
                    universe.source(), choices, cancellation);
                graphNanos += System.nanoTime() - graphStarted;
                // An upstream change may remove or reduce a cycle demand, or free reserved seed.
                // Deduplicate actual cycle requests after numeric planning, never just changed key names.
                long candidateStarted = ECOPlanningStageLogger.start();
                ECOCancellation candidateBudget = success && improvePublishedRoute
                    ? ((ECOPlanningBudget) cancellation).slice(Math.max(1L, NEConfig.ecoPlanningMaxWork / ALTERNATIVE_ROUTE_SHARE),
                        Math.max(1L, NEConfig.ecoPlanningMaxMillis) * 1_000_000L / ALTERNATIVE_ROUTE_SHARE)
                    : cancellation;
                Outcome alternative;
                try {
                    alternative = plan(network, candidate, inventory, snapshot, amount, cyclePlanningEnabled,
                        ignorePatternSubstitutions, candidateBudget, failures, externalDemandPlanner, seeds, true);
                } catch (ECOPlanningBudget.Exhausted unknown) {
                    if (!success || !improvePublishedRoute) throw unknown;
                    limitedCandidates++;
                    candidateNanos += System.nanoTime() - candidateStarted;
                    continue;
                }
                candidateNanos += System.nanoTime() - candidateStarted;
                ECOPlanningStageLogger.finish("route_candidate", candidateStarted, alternative.completeCandidate(),
                    "attempt=" + attempts + " changed=" + routes.changedChoices() + " status=" + alternative.status());
                if ((!success || betterRoute(alternative, best)) && offerCandidate(alternative, listener)) {
                    success = true;
                    best = alternative;
                    resultTrace = alternative.trace();
                    if (!improvePublishedRoute) return alternative;
                    // Recovery found an executable route. Stop using its small allowance before optimizing it.
                    cancellation = sessionBudget;
                }
            }
            if (limitedCandidates > 0) {
                best.trace().addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED,
                    "Remaining producer combinations visited; " + limitedCandidates
                        + " candidate calculations remain unknown within their individual allowance"));
                return incompleteSearch(best);
            }
            return best;
        } catch (ECOPlanningBudget.Exhausted exhausted) {
            best.trace().addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED,
                "stage=alternative_routes " + exhausted.getMessage() + "; attempts=" + attempts
                    + " expansions=" + routes.expansions() + "; retaining the completed route"));
            return incompleteSearch(best);
        } finally {
            if (routes.prunedBranches() > 0) resultTrace.addDiagnostic(new PlannerDiagnostic(
                PlannerDiagnostic.Code.ROUTE_BRANCH_PRUNED,
                "Optimistic quantity proofs excluded " + routes.prunedBranches()
                    + " infeasible producer prefixes before full route planning"));
            ECOPlanningStageLogger.finish("alternative_routes", startedNanos, success,
                "attempts=" + attempts + " pass=" + routes.pass() + " expansions=" + routes.expansions()
                    + " pruned=" + routes.prunedBranches()
                    + " cycleSolves=" + failures.solverCalls() + " cycleReuses=" + failures.reusedFailures()
                    + " activeGraphMs=" + graphNanos / 1_000_000.0D
                    + " candidatePlanMs=" + candidateNanos / 1_000_000.0D + " candidateLimited=" + limitedCandidates
                    + (supply == null ? "" : " " + supply.metrics()));
        }
    }

    private static boolean offerCandidate(Outcome candidate, @Nullable CandidateListener listener)
            throws InterruptedException {
        if (!candidate.completeCandidate()) return false;
        return listener == null ? candidate.status() == PlanningStatus.SUCCESS : listener.accept(candidate);
    }

    private static boolean betterRoute(Outcome candidate, Outcome incumbent) {
        if (!candidate.completeCandidate()) return false;
        // An improvement must not silently require a wider backend than an already executable AE2 plan.
        if (incumbent.status() == PlanningStatus.SUCCESS && candidate.status() != PlanningStatus.SUCCESS) return false;
        return JointRouteOptimizer.cost(candidate.state()).compareTo(JointRouteOptimizer.cost(incumbent.state())) < 0;
    }

    private static Outcome incompleteSearch(Outcome preferred) {
        PlanningStatus status = preferred.status() == PlanningStatus.SUCCESS ? PlanningStatus.SUCCESS
            : preferred.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                || !preferred.state().executionAmountIssues().isEmpty()
                    ? PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE : PlanningStatus.CYCLE_UNRESOLVED;
        return new Outcome(status, preferred.state(), preferred.trace(), preferred.cycles(),
            preferred.components(), preferred.executionComponentOrder());
    }

    private static int normalizeChoice(int choice, int candidateCount) {
        return Math.max(0, Math.min(choice, candidateCount - 1));
    }

    private static int producerIndex(List<CompiledPattern> candidates, CompiledPattern selected) {
        if (selected == null) return -1;
        for (int index = 0; index < candidates.size(); index++) {
            CompiledPattern candidate = candidates.get(index);
            if (candidate == selected || candidate.equals(selected)) return index;
        }
        return -1;
    }

    private static void validateProvenanceCoverage(CompiledNetwork network, SolveState state,
                                                   List<ComponentPlanningResult> components, ECOPlanTrace trace) {
        Set<String> reported = new LinkedHashSet<>();
        Set<IPatternDetails> cyclePatterns = components.stream()
                .filter(component -> component.type() == ComponentPlanningResult.Type.CYCLIC)
                .flatMap(component -> component.executionPatterns().stream())
                .collect(java.util.stream.Collectors.toSet());
        var provenance = state.executionProvenance();
        for (List<CompiledPattern> candidates : network.producers().values()) {
            for (CompiledPattern pattern : candidates) {
                if (state.patternTimes.getOrDefault(pattern.details(), PlannerAmount.ZERO).signum() <= 0
                        || cyclePatterns.contains(pattern.details())) continue;
                for (CompiledInput input : pattern.inputs()) {
                    if (pattern.specialAnalysis().excludesFromCycleGraph(input)
                            || provenance.covers(input.key())) continue;
                    String message = "Unattributed key=" + input.key()
                            + " consumer=" + pattern.details();
                    if (!reported.add(message)) continue;
                    trace.addDiagnostic(new PlannerDiagnostic(
                            PlannerDiagnostic.Code.PROVENANCE_UNATTRIBUTED, message));
                }
            }
        }
    }

    static CycleSolveRequest.PlannerOptions cycleSolveOptions(CycleComponent cycle) {
        return new CycleSolveRequest.PlannerOptions();
    }

    private static CycleExecutionDisposition cycleExecutionDisposition(CycleComponent cycle, CycleSolveResult result) {
        // A firing vector alone does not preserve liveness when transitions compete for one seed.
        // Keep the solver's verified witness for every cycle size.
        return !result.executionPlan().isEmpty()
                ? CycleExecutionDisposition.ORDERED_EXECUTION
                : CycleExecutionDisposition.DYNAMIC_EXECUTION;
    }

    private static Map<AEKey, PlannerAmount> relevantStock(CycleComponent cycle, java.util.Set<AEKey> requiredOutputs,
            PlannerInventorySnapshot inventory, SolveState state, Map<AEKey, PlannerAmount> componentReservations) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        for (AEKey member : cycle.members()) result.put(member, remaining(inventory, state, member));
        for (AEKey required : requiredOutputs) result.putIfAbsent(required, remaining(inventory, state, required));
        for (var dependency : cycle.outgoingDependencies()) {
            for (var relationship : dependency.relationships()) {
                AEKey key = relationship.requiredInput();
                result.putIfAbsent(key, remaining(inventory, state, key));
            }
        }
        componentReservations.forEach((key, reserved) -> {
            if (!state.stored.isUnbounded(key)) result.merge(key, reserved, PlannerAmount::add);
        });
        return result;
    }

    static Set<AEKey> delegatedCycleInputs(CompiledNetwork network, CondensationGraph condensation,
                                            Map<AEKey, Integer> choices, CycleComponent consumer, Set<AEKey> inputs) {
        Set<AEKey> delegated = new ObjectOpenHashSet<>();
        inputs.forEach(key -> {
            CycleComponent supplier = cyclicSupplier(network, condensation, choices, key, consumer.componentId());
            if (supplier != null) {
                delegated.add(key);
            }
        });
        return delegated;
    }

    static Map<AEKey, PlannerAmount> mergeDemands(Map<AEKey, PlannerAmount> first, Map<AEKey, PlannerAmount> second) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>(first);
        second.forEach((key, amount) -> result.merge(key, amount, PlannerAmount::add));
        return result;
    }

    private static @Nullable CycleComponent cyclicSupplier(CompiledNetwork network, CondensationGraph condensation,
                                                           Map<AEKey, Integer> choices, AEKey key, int excludedComponentId) {
        if (condensation.componentFor(key) instanceof CycleComponent direct
                && direct.componentId() != excludedComponentId) return direct;
        List<CompiledPattern> candidates = network.fastProducersOf(key);
        if (!candidates.isEmpty()) {
            int choice = Math.max(0, Math.min(choices.getOrDefault(key, 0), candidates.size() - 1));
            IPatternDetails selected = candidates.get(choice).details();
            CycleComponent selectedOwner = condensation.cycles().stream()
                    .filter(cycle -> cycle.componentId() != excludedComponentId)
                    .filter(cycle -> cycle.patterns().stream().anyMatch(pattern -> pattern.details() == selected))
                    .findFirst().orElse(null);
            if (selectedOwner != null) return selectedOwner;
        }

        List<CycleComponent> byproductOwners = condensation.cycles().stream()
                .filter(cycle -> cycle.componentId() != excludedComponentId)
                .filter(cycle -> cycle.patterns().stream().anyMatch(pattern -> pattern.grossOutputs().stream()
                        .anyMatch(output -> output.what().equals(key))))
                .distinct().toList();
        return byproductOwners.size() == 1 ? byproductOwners.getFirst() : null;
    }

    private static Set<IPatternDetails> selectedExecutionPatterns(SolveState state, AEKey key,
                                                                  Set<AEKey> structuralKeys, boolean includeLocalSpecialProducers) {
        CompiledPattern selected = state.selected.get(key);
        Set<IPatternDetails> result = new LinkedHashSet<>();
        if (selected != null && state.patternTimes
                .getOrDefault(selected.details(), PlannerAmount.ZERO).signum() > 0) {
            result.add(selected.details());
        }
        if (includeLocalSpecialProducers) {
            for (var entry : state.selected.entrySet()) {
                if (structuralKeys.contains(entry.getKey())) continue;
                CompiledPattern local = entry.getValue();
                if (state.patternTimes.getOrDefault(local.details(), PlannerAmount.ZERO).signum() > 0) {
                    result.add(local.details());
                }
            }
        }
        return result;
    }

    private static Set<IPatternDetails> selectedCycleExecutionPatterns(@Nullable CycleSolveResult result) {
        if (result == null || result.status() != CycleSolveStatus.SUCCESS) return Set.of();
        return result.exactPatternTimes().entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * Stock the DAG pass has not already spent. The cycle solver must never double-spend an item.
     */
    private static PlannerAmount remaining(PlannerInventorySnapshot inventory, SolveState state, AEKey key) {
        if (state.stored.isUnbounded(key)) return PlannerAmount.ZERO;
        return inventory.amountOf(key).subtract(state.used.get(key)).max(PlannerAmount.ZERO);
    }

    /**
     * Cycle stock may enable the witness, but AE2 crafting amounts mean newly requested output. Without this
     * translation, stored copies of the final output satisfy the solver target and produce an empty CPU job.
     */
    private static Map<AEKey, PlannerAmount> additionalOutputTargets(Map<AEKey, PlannerAmount> requiredOutputs,
                                                                     Map<AEKey, PlannerAmount> relevantStock, AEKey finalGoal) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        requiredOutputs.forEach((key, amount) -> {
            // Intermediate demand already includes the stock reserved by the DAG pass. Adding it again
            // makes a 23-stock / 51-demand growth recipe run 51 times instead of the required 28.
            if (!key.equals(finalGoal)) {
                result.put(key, amount);
                return;
            }
            // AE2 asks the CPU to craft the final output anew, even when copies are already stored.
            result.put(key, amount.add(relevantStock.getOrDefault(key, PlannerAmount.ZERO)));
        });
        return result;
    }

    /**
     * Computes the inventory that must be owned before the cycle transaction starts.
     */
    static Map<AEKey, PlannerAmount> cycleInitialReservations(Map<AEKey, PlannerAmount> requiredOutputs,
            Map<AEKey, PlannerAmount> startingStock, CycleSolveResult result) {
        Map<AEKey, PlannerAmount> reservations = new LinkedHashMap<>();
        result.exactRequiredSeed().forEach((key, amount) -> {
            PlannerAmount stockBacked = amount.min(startingStock.getOrDefault(key, PlannerAmount.ZERO));
            if (stockBacked.signum() > 0) reservations.put(key, stockBacked);
        });
        requiredOutputs.forEach((key, exactDemand) -> {
            PlannerAmount initial = startingStock.getOrDefault(key, PlannerAmount.ZERO);
            PlannerAmount deliverable = result.exactDeliverableOutputs().getOrDefault(key, PlannerAmount.ZERO);
            // deliverable is the ending balance after the verified witness runs from initial stock. Preserve enough
            // of that starting stock to cover both any net cycle consumption and the balance owed to downstream.
            PlannerAmount endingSurplus = deliverable.subtract(exactDemand).max(PlannerAmount.ZERO);
            PlannerAmount stockBacked = initial.subtract(initial.min(endingSurplus));
            if (stockBacked.signum() > 0) reservations.merge(key, stockBacked, PlannerAmount::max);
        });
        return reservations;
    }

    /** Exact committed deltas; these include cumulative creative inputs and may exceed both long and U128. */
    private static Map<AEKey, PlannerAmount> reservationDelta(Map<AEKey, PlannerAmount> before,
                                                             Map<AEKey, PlannerAmount> after) {
        Map<AEKey, PlannerAmount> delta = new LinkedHashMap<>();
        after.forEach((key, amount) -> {
            PlannerAmount difference = amount.subtract(before.getOrDefault(key, PlannerAmount.ZERO));
            if (difference.signum() > 0) delta.put(key, difference);
        });
        return delta;
    }

    /** Stock already consumed by the acyclic pass, less the exact amounts owned by previous components. */
    private static Map<AEKey, PlannerAmount> existingComponentReservations(Map<AEKey, PlannerAmount> required,
            SolveState state, Map<AEKey, PlannerAmount> alreadyAttributed) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        required.forEach((key, amount) -> {
            if (amount == null || amount.signum() <= 0) return;
            PlannerAmount used = state.usedAmounts().getOrDefault(key, PlannerAmount.ZERO);
            PlannerAmount available = used.subtract(alreadyAttributed.getOrDefault(key, PlannerAmount.ZERO))
                .max(PlannerAmount.ZERO);
            PlannerAmount reserved = amount.min(available);
            if (reserved.signum() > 0) result.put(key, reserved);
        });
        return result;
    }

    static Map<AEKey, PlannerAmount> reservationRemainder(Map<AEKey, PlannerAmount> required,
                                                Map<AEKey, PlannerAmount> alreadyOwned) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        required.forEach((key, amount) -> {
            PlannerAmount remainder = amount
                .subtract(alreadyOwned.getOrDefault(key, PlannerAmount.ZERO)).max(PlannerAmount.ZERO);
            if (remainder.signum() > 0) result.put(key, remainder);
        });
        return result;
    }

    /** Bounded solver/AE2 compatibility view only; never feed it back into the exact reservation ledger. */
    private static Map<AEKey, Long> projectReservations(Map<AEKey, PlannerAmount> exact) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        exact.forEach((key, amount) -> result.put(key, boundedReservation(amount)));
        return result;
    }

    private static long boundedReservation(PlannerAmount amount) {
        return amount.longValueSaturated();
    }

    private static boolean stockCoversRequiredOutputs(Map<AEKey, PlannerAmount> required,
                                                      Map<AEKey, PlannerAmount> reservations) {
        if (required.isEmpty()) return false;
        return required.entrySet().stream().allMatch(entry -> entry.getValue().signum() > 0
            && reservations.getOrDefault(entry.getKey(), PlannerAmount.ZERO).compareTo(entry.getValue()) >= 0);
    }

    /** The witness must end with the order's outputs and, on top of any demand for the same key, the kept seed. */
    private static Map<AEKey, PlannerAmount> withSeedTargets(Map<AEKey, PlannerAmount> targets,
                                                             List<SeedFloors.Floor> floors) {
        if (floors.isEmpty()) return targets;
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>(targets);
        for (SeedFloors.Floor floor : floors) result.merge(floor.key(), floor.minimum(), PlannerAmount::add);
        return result;
    }

    private static PlannerInventorySnapshot withReservedSeeds(PlannerInventorySnapshot inventory,
            List<SeedFloors.Floor> floors, Map<AEKey, PlannerAmount> borrowedSeeds) {
        Map<AEKey, PlannerAmount> result = mergeDemands(inventory.amounts(), borrowedSeeds);
        for (SeedFloors.Floor floor : floors) {
            if (floor.reserved().signum() > 0) result.merge(floor.key(), floor.reserved(), PlannerAmount::add);
        }
        return PlannerInventorySnapshot.exact(result, inventory.unboundedKeys());
    }

    /** After a committed cycle transaction, remember how much of its reserved seed the ledger now uses. */
    private static void recordBorrowedSeeds(List<SeedFloors.Floor> floors, PlannerInventorySnapshot inventory,
                                            Map<AEKey, PlannerAmount> borrowedSeeds, SolveState state) {
        for (SeedFloors.Floor floor : floors) {
            AEKey key = floor.key();
            PlannerAmount visible = inventory.amountOf(key).add(borrowedSeeds.getOrDefault(key, PlannerAmount.ZERO));
            PlannerAmount borrowed = state.used.get(key).subtract(visible).max(PlannerAmount.ZERO).min(floor.reserved());
            if (borrowed.signum() > 0) borrowedSeeds.merge(key, borrowed, PlannerAmount::add);
        }
    }

    private static String describeFloors(List<SeedFloors.Floor> floors) {
        StringBuilder sb = new StringBuilder("{");
        for (SeedFloors.Floor floor : floors) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(floor.key()).append('=').append(floor.minimum());
        }
        return sb.append('}').toString();
    }

    static boolean hasPositiveFirings(CycleSolveResult result) {
        return result != null && result.patternTimes().values().stream()
                .anyMatch(count -> count > 0L);
    }

    private static ComponentPlanningResult.Status componentStatus(Map<AEKey, PlannerAmount> requiredOutputs,
                                                                  CyclePlanningStatus status, CycleExecutionDisposition disposition) {
        if (status == CyclePlanningStatus.UNREPRESENTABLE) return ComponentPlanningResult.Status.UNREPRESENTABLE;
        if (requiredOutputs.isEmpty()) return ComponentPlanningResult.Status.NOT_REQUIRED;
        return switch (status) {
            // A successful transaction already merged the cycle and its external DAG into SolveState.
            case SOLVED -> switch (disposition) {
                case STOCK_SATISFIED, ORDERED_EXECUTION, DYNAMIC_EXECUTION -> ComponentPlanningResult.Status.PLANNED;
                default -> ComponentPlanningResult.Status.SOLVED_NOT_EMITTED;
            };
            case UNSUPPORTED -> ComponentPlanningResult.Status.UNSUPPORTED;
            default -> ComponentPlanningResult.Status.UNRESOLVED;
        };
    }

    private static PlannerDiagnostic.Code diagnosticCode(CyclePlanningStatus status) {
        return switch (status) {
            case SOLVED -> PlannerDiagnostic.Code.CYCLE_SOLVED;
            case INSUFFICIENT_EXTERNAL_INPUT -> PlannerDiagnostic.Code.CYCLE_SEED_REQUIRED;
            case UNKNOWN_BUDGET -> PlannerDiagnostic.Code.CYCLE_BUDGET_EXHAUSTED;
            case TOO_COMPLEX -> PlannerDiagnostic.Code.CYCLE_TOO_COMPLEX;
            case UNSUPPORTED -> PlannerDiagnostic.Code.CYCLE_UNSUPPORTED;
            case UNREPRESENTABLE -> PlannerDiagnostic.Code.EXECUTION_AMOUNT_UNREPRESENTABLE;
            case CANCELLED -> PlannerDiagnostic.Code.CANCELLED;
            default -> PlannerDiagnostic.Code.CYCLE_NOT_IMPLEMENTED;
        };
    }

    private static void addAmountDiagnostic(ECOPlanTrace trace, AEKey key, IPatternDetails producer,
                                            PlannerAmount amount, String stage) {
        String pattern = producer == null ? "<counter>" : producer.toString();
        trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.EXECUTION_AMOUNT_UNREPRESENTABLE,
                "Execution amount exceeds AE2 long range: key=" + key + " producer=" + pattern
                        + " pattern=" + pattern + " amount=" + amount + " max=" + Long.MAX_VALUE
                        + " stage=" + stage));
    }

    private static PlannerDiagnostic.Code externalDiagnosticCode(CycleExternalDemandStatus status) {
        return switch (status) {
            case SOLVED -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_DEMAND_SOLVED;
            case MISSING -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_DEMAND_MISSING;
            case FORBIDDEN_ROUTE -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_ROUTE_FORBIDDEN;
            case UNSUPPORTED -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_DEMAND_UNSUPPORTED;
            case OVERFLOW -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_DEMAND_OVERFLOW;
            case UNREPRESENTABLE -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_DEMAND_UNREPRESENTABLE;
        };
    }

    private static CycleDiagnostic diagnostic(CycleComponent cycle, KeyCounter inventory,
                                              CycleSolveResult cycleResult, ECOPlanTrace trace) {
        Map<AEKey, PlannerAmount> exactNet = new LinkedHashMap<>();
        java.util.LinkedHashSet<AEKey> diagnosticKeys = new java.util.LinkedHashSet<>(cycle.members());
        for (var pattern : cycle.patterns()) {
            for (var output : pattern.grossOutputs()) diagnosticKeys.add(output.what());
            for (CompiledInput input : pattern.inputs()) diagnosticKeys.add(input.key());
        }
        for (AEKey key : diagnosticKeys) exactNet.put(key, PlannerAmount.ZERO);
        Set<IPatternDetails> countedPatterns = new ObjectOpenHashSet<>();
        for (var pattern : cycle.patterns()) {
            if (!countedPatterns.add(pattern.details())) continue;
            for (var output : pattern.grossOutputs())
                if (exactNet.containsKey(output.what())) {
                    exactNet.put(output.what(), exactNet.get(output.what()).add(output.amount()));
                }
            for (CompiledInput input : pattern.inputs())
                if (exactNet.containsKey(input.key())) {
                    exactNet.put(input.key(), exactNet.get(input.key()).subtract(input.amountPerPattern()));
                }
        }
        Map<AEKey, PlannerAmount> exactTotal = new LinkedHashMap<>();
        if (cycleResult != null && cycleResult.hasExactExecutionCounts()) {
            for (AEKey key : diagnosticKeys) exactTotal.put(key, PlannerAmount.ZERO);
            countedPatterns.clear();
            for (var pattern : cycle.patterns()) {
                if (!countedPatterns.add(pattern.details())) continue;
                PlannerAmount times = cycleResult.exactPatternTimes().getOrDefault(
                        pattern.details(), PlannerAmount.ZERO);
                if (times.isZero()) continue;
                for (var output : pattern.grossOutputs())
                    if (exactTotal.containsKey(output.what())) {
                        exactTotal.put(output.what(), exactTotal.get(output.what()).add(
                                PlannerAmount.of(output.amount()).multiply(times)));
                    }
                for (CompiledInput input : pattern.inputs())
                    if (exactTotal.containsKey(input.key())) {
                        exactTotal.put(input.key(), exactTotal.get(input.key()).subtract(
                                input.amountPerPattern().multiply(times)));
                    }
            }
        }
        addWideCycleDiagnostics(trace, exactNet, "cycle net output");
        addWideCycleDiagnostics(trace, exactTotal, "cycle total net output");
        return new CycleDiagnostic(new ArrayList<>(diagnosticKeys),
                cycle.patterns().stream().map(p -> p.details()).toList(),
                exactNet, exactTotal, Map.of(),
                cycleResult == null ? cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge.UNKNOWN
                        : cycleResult.executionCountKnowledge(),
                cycleResult == null ? CycleSolveStatus.NOT_IMPLEMENTED : cycleResult.status())
                .withAvailableAmounts(inventory);
    }

    static Map<AEKey, Long> representable(Map<AEKey, PlannerAmount> exact) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var entry : exact.entrySet()) {
            if (entry.getValue().fitsLong()) result.put(entry.getKey(), entry.getValue().longValueExact());
        }
        return result;
    }

    private static long traceLong(PlannerAmount amount) {
        return amount.fitsLong() ? amount.longValueExact() : 0L;
    }

    private static void addWideCycleDiagnostics(ECOPlanTrace trace, Map<AEKey, PlannerAmount> exact,
                                                String stage) {
        for (var entry : exact.entrySet()) {
            if (!entry.getValue().fitsLong()) {
                addAmountDiagnostic(trace, entry.getKey(), null, entry.getValue(), stage);
            }
        }
    }
}
