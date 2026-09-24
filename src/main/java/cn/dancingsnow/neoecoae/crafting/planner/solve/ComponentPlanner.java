package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningStageLogger;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveLimits;
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
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
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
    private static final int MAX_ROUTE_ATTEMPTS = 256;

    public record Outcome(
            PlanningStatus status,
            SolveState state,
            ECOPlanTrace trace,
            List<CycleDiagnostic> cycles,
            List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder
    ) {
    }

    private final AcyclicCraftingSolver acyclicSolver;
    private final CycleSolver cycleSolver;
    private final ActiveRouteSelector activeRouteSelector;
    private final ExternalDemandPlanner externalDemandPlanner;
    private final int maxRouteAttempts;

    public ComponentPlanner(AcyclicCraftingSolver acyclicSolver, CycleSolver cycleSolver) {
        this(acyclicSolver, cycleSolver, MAX_ROUTE_ATTEMPTS);
    }

    /** Package-visible budget override for deterministic route-search tests. */
    ComponentPlanner(AcyclicCraftingSolver acyclicSolver, CycleSolver cycleSolver, int maxRouteAttempts) {
        if (maxRouteAttempts < 1) throw new IllegalArgumentException("Route attempt budget must be positive");
        this.acyclicSolver = acyclicSolver;
        this.cycleSolver = cycleSolver;
        this.activeRouteSelector = new ActiveRouteSelector();
        this.externalDemandPlanner = new ExternalDemandPlanner(acyclicSolver);
        this.maxRouteAttempts = maxRouteAttempts;
    }

    public Outcome plan(CompiledNetwork network, CondensationGraph condensation, KeyCounter inventory,
                        long amount, boolean cyclePlanningEnabled, ECOCancellation cancellation) throws InterruptedException {
        ActiveRouteSelector.Selection activeSelection = selectRoutes(condensation, cyclePlanningEnabled,
                cancellation);
        return plan(network, activeSelection, inventory, amount, cyclePlanningEnabled, false, cancellation);
    }

    public Outcome plan(CompiledNetwork network, CondensationGraph condensation, KeyCounter inventory,
                        long amount, boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
                        ECOCancellation cancellation) throws InterruptedException {
        ActiveRouteSelector.Selection activeSelection = selectRoutes(condensation, cyclePlanningEnabled,
                cancellation);
        return plan(network, activeSelection, inventory, amount, cyclePlanningEnabled,
                ignorePatternSubstitutions, cancellation);
    }

    public Outcome plan(CompiledNetwork network, CondensationGraph condensation, KeyCounter inventory,
                        PlannerInventorySnapshot snapshot, long amount, boolean cyclePlanningEnabled,
                        boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        ActiveRouteSelector.Selection activeSelection = selectRoutes(condensation, cyclePlanningEnabled,
                cancellation);
        return plan(network, activeSelection, inventory, snapshot, amount, cyclePlanningEnabled,
                ignorePatternSubstitutions, cancellation);
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
        return plan(network, activeSelection, inventory, snapshot, amount, cyclePlanningEnabled,
                ignorePatternSubstitutions, cancellation, new CycleFailureCache(cycleSolver));
    }

    private Outcome plan(CompiledNetwork network, ActiveRouteSelector.Selection activeSelection,
                         KeyCounter inventory, PlannerInventorySnapshot snapshot, long amount, boolean cyclePlanningEnabled,
                         boolean ignorePatternSubstitutions, ECOCancellation cancellation,
                         CycleFailureCache failures) throws InterruptedException {
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
        final CondensationGraph resolvedCondensation = activeCondensation;
        final Map<AEKey, Integer> resolvedChoices = activeSelection.choices();
        AcyclicCraftingSolver.Outcome acyclic = attempt;
        ECOPlanTrace trace = acyclic.trace();
        for (var deferred : activeSelection.deferredCyclicCandidates()) {
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, deferred.producedKey(), deferred.details(),
                    0, 0, 0, 0, 0, PlanTraceNode.Selection.REJECTED, "CYCLIC_CANDIDATE"));
        }
        if (!activeSelection.deferredCyclicCandidates().isEmpty()) {
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CANDIDATE_DEFERRED_CYCLE,
                    "Cyclic candidate deferred while trying an alternate producer"));
        }
        List<ComponentPlanningResult> componentResults = new ArrayList<>();
        List<CycleDiagnostic> cycleDiagnostics = new ArrayList<>();
        Map<Integer, Map<AEKey, PlannerAmount>> delegatedCycleDemands = new LinkedHashMap<>();
        Map<AEKey, Long> attributedCycleReservations = new LinkedHashMap<>();
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

        for (var component : activeCondensation.topologicalOrder()) {
            cancellation.checkpoint();
            trace.addComponent(new ComponentTrace(component.componentId(), component.cyclic()
                    ? ComponentTrace.Type.CYCLIC : ComponentTrace.Type.ACYCLIC, component.members()));
            if (component instanceof AcyclicComponent acyclicComponent) {
                PlannerAmount exactDemand = acyclic.state().demandAmountFor(acyclicComponent.key());
                long demand = exactDemand.fitsLong() ? exactDemand.longValueExact() : 0L;
                componentResults.add(new ComponentPlanningResult(component.componentId(),
                        ComponentPlanningResult.Type.ACYCLIC,
                        exactDemand.signum() > 0 ? ComponentPlanningResult.Status.PLANNED
                                : ComponentPlanningResult.Status.NOT_REQUIRED,
                        demand > 0 ? Map.of(acyclicComponent.key(), demand) : Map.of(),
                        acyclicComponent.patterns().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()),
                        selectedExecutionPatterns(acyclic.state(), acyclicComponent.key(), structuralKeys,
                                acyclicComponent.key().equals(network.goal())),
                        null, null, Map.of(), null, null, CycleExecutionDisposition.NOT_REQUIRED, Map.of()));
                continue;
            }

            CycleComponent cycle = (CycleComponent) component;
            long cycleStartedNanos = ECOPlanningStageLogger.start();
            try {
                Map<AEKey, PlannerAmount> exactRequiredOutputs = new LinkedHashMap<>();
                Map<AEKey, Long> requiredOutputs = new LinkedHashMap<>();
                for (AEKey member : cycle.members()) {
                    PlannerAmount exactDemand = acyclic.state().demandAmountFor(member);
                    if (exactDemand.signum() > 0) {
                        exactRequiredOutputs.put(member, exactDemand);
                        if (exactDemand.fitsLong()) requiredOutputs.put(member, exactDemand.longValueExact());
                    }
                }
                var ownedDetails = cycle.patterns().stream().map(CompiledPattern::details)
                        .collect(java.util.stream.Collectors.toSet());
                for (var selected : acyclic.state().selected.entrySet()) {
                    if (!ownedDetails.contains(selected.getValue().details())) continue;
                    PlannerAmount exactDemand = acyclic.state().demandAmountFor(selected.getKey());
                    if (exactDemand.signum() > 0) {
                        exactRequiredOutputs.put(selected.getKey(), exactDemand);
                        if (exactDemand.fitsLong())
                            requiredOutputs.put(selected.getKey(), exactDemand.longValueExact());
                    }
                }
                delegatedCycleDemands.getOrDefault(cycle.componentId(), Map.of()).forEach((key, demand) -> {
                    if (demand.signum() <= 0) return;
                    exactRequiredOutputs.merge(key, demand, PlannerAmount::add);
                    if (demand.fitsLong()) requiredOutputs.merge(key, demand.longValueExact(), Math::addExact);
                });
                // The DAG pass already reserves creative inputs. Structural self-growth recipes must not
                // turn that fulfilled demand back into work (or add Long.MAX_VALUE as a growth seed).
                // Explicit requests to craft the final output still require newly produced output.
                exactRequiredOutputs.entrySet().removeIf(entry -> {
                    AEKey key = entry.getKey();
                    if (key.equals(network.goal()) || !acyclic.state().stored.isUnbounded(key)
                            || acyclic.state().used.get(key).compareTo(entry.getValue()) < 0) return false;
                    requiredOutputs.remove(key);
                    return true;
                });
                CyclePlanningStatus cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                String diagnostic = null;
                CycleSolveResult cycleResult = null;
                CycleExternalDemandStatus externalDemandStatus = null;
                Map<AEKey, Long> externalMissingItems = Map.of();
                CycleExecutionDisposition disposition = exactRequiredOutputs.isEmpty()
                        ? CycleExecutionDisposition.NOT_REQUIRED : CycleExecutionDisposition.BLOCKED;
                Map<AEKey, Long> stockReservations = existingComponentReservations(
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
                    acyclic.state().markCycleMissing(requiredOutputs);
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_DISABLED, diagnostic));
                } else {
                    // The cycle solver is a plug-in: it receives a snapshot and never touches the DAG workspace.
                    Map<AEKey, Long> stock = relevantStock(cycle, exactRequiredOutputs.keySet(), inventory,
                            acyclic.state(), stockReservations);
                    Map<AEKey, PlannerAmount> solveTargets = additionalOutputTargets(exactRequiredOutputs, stock,
                            network.goal());
                    if (!solveTargets.isEmpty()) {
                        cycleResult = failures.solve(new CycleSolveRequest(cycle, representable(solveTargets),
                                        solveTargets, stock, cycle.outgoingDependencies(), cycleSolveOptions(cycle)),
                                cancellation);
                        cycleStatus = CyclePlanningStatus.of(cycleResult.status());
                        diagnostic = cycleResult.summary();
                        if (cycleResult.status() == CycleSolveStatus.SUCCESS) {
                            LOGGER.debug("[ECO-CYCLE] solve component={} status={} firings={} seed={} "
                                            + "externalDemand={} shortfall={} metrics={}",
                                    cycle.componentId(), cycleResult.status(), cycleResult.plannerTotalFirings(),
                                    cycleResult.requiredSeed(), cycleResult.externalDemand(),
                                    cycleResult.seedShortfall(), cycleResult.metrics());
                        } else {
                            LOGGER.warn("[ECO-CYCLE] solve component={} status={} targets={} stock={} "
                                            + "seed={} externalDemand={} shortfall={} diagnostics={}",
                                    cycle.componentId(), cycleResult.status(), solveTargets, stock,
                                    cycleResult.requiredSeed(), cycleResult.externalDemand(),
                                    cycleResult.seedShortfall(), cycleResult.diagnostics());
                        }
                        if (cycleStatus == CyclePlanningStatus.UNREPRESENTABLE) amountUnrepresentable = true;
                        trace.addDiagnostic(new PlannerDiagnostic(diagnosticCode(cycleStatus), diagnostic));
                        if (cycleResult.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT) {
                            trace.addDiagnostic(new PlannerDiagnostic(
                                    PlannerDiagnostic.Code.CYCLE_PROVEN_INFEASIBLE_AT_CURRENT_STOCK,
                                    "Component " + cycle.componentId() + " cannot satisfy " + solveTargets
                                            + " from current stock " + stock + "; seed shortfall="
                                            + cycleResult.seedShortfall()));
                        }
                    }
                    ExternalDemandPlanner.Outcome external = null;
                    boolean externalFailureHandled = false;
                    boolean startupRecoveryAttempted = false;
                    Map<AEKey, Long> plannedCycleInputs = Map.of();
                    if (cycleResult != null
                            && cycleResult.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT
                            && !cycleResult.seedShortfall().isEmpty()) {
                        startupRecoveryAttempted = true;
                        Map<AEKey, Long> recoveryDemands = mergeDemands(
                                cycleResult.positiveExternalDemand(), cycleResult.seedShortfall());
                        Map<AEKey, Long> recoveryReservations = cycleInitialReservations(
                                exactRequiredOutputs, stock, cycleResult);
                        Map<AEKey, Long> recoveryAdditionalReservations = reservationRemainder(
                                recoveryReservations, stockReservations);
                        Set<AEKey> delegatedInputs = delegatedCycleInputs(network, activeCondensation,
                                activeSelection.choices(), cycle, recoveryDemands.keySet());
                        external = externalDemandPlanner.solveDemands(network, cycle, recoveryDemands, inventory,
                                acyclic.state(), recoveryAdditionalReservations, delegatedInputs,
                                ignorePatternSubstitutions, cancellation);
                        externalDemandStatus = external.status();
                        externalMissingItems = external.missingLeaves();
                        LOGGER.warn("[ECO-CYCLE] startup recovery component={} externalStatus={} demands={} "
                                        + "missingLeaves={} diagnostic={}",
                                cycle.componentId(), external.status(), recoveryDemands,
                                externalMissingItems, external.diagnostic());
                        trace.addDiagnostic(new PlannerDiagnostic(externalDiagnosticCode(external.status()),
                                external.diagnostic()));
                        if (external.solved()) {
                            Map<AEKey, Long> projectedStock = mergeReservations(stock, cycleResult.seedShortfall());
                            solveTargets = additionalOutputTargets(exactRequiredOutputs, projectedStock,
                                    network.goal());
                            CycleSolveResult recovered = failures.solve(new CycleSolveRequest(cycle,
                                    representable(solveTargets), solveTargets, projectedStock, cycle.outgoingDependencies(),
                                    cycleSolveOptions(cycle)), cancellation);
                            if (recovered.status() == CycleSolveStatus.SUCCESS) {
                                plannedCycleInputs = cycleResult.seedShortfall();
                                cycleResult = recovered;
                                cycleStatus = CyclePlanningStatus.SOLVED;
                                // The seed probe was speculative. Replan the verified witness's complete
                                // boundary and reservations from the original stock, committing neither twice.
                                external = null;
                                diagnostic = "Cycle startup seed was planned through its external producer route";
                            } else {
                                if (recovered.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT) {
                                    // Projected startup inputs have not been committed. Include them when
                                    // reporting the remaining unfulfilled cycle seed requirement.
                                    acyclic.state().markMissing(mergeDemands(
                                            cycleResult.seedShortfall(), recovered.seedShortfall()));
                                }
                                cycleResult = recovered;
                                cycleStatus = CyclePlanningStatus.of(recovered.status());
                                if (cycleStatus == CyclePlanningStatus.UNREPRESENTABLE) amountUnrepresentable = true;
                                diagnostic = recovered.summary();
                            }
                            trace.addDiagnostic(new PlannerDiagnostic(diagnosticCode(cycleStatus), diagnostic));
                        }
                    }
                    if (cycleResult != null && cycleStatus == CyclePlanningStatus.SOLVED) {
                        Map<AEKey, Long> initialReservations = cycleInitialReservations(
                                exactRequiredOutputs, stock, cycleResult);
                        Map<AEKey, Long> additionalReservations = reservationRemainder(
                                initialReservations, stockReservations);
                        if (external == null) {
                            Map<AEKey, Long> boundaryDemands = mergeDemands(
                                    cycleResult.positiveExternalDemand(), plannedCycleInputs);
                            Set<AEKey> delegatedInputs = delegatedCycleInputs(network, activeCondensation,
                                    activeSelection.choices(), cycle, boundaryDemands.keySet());
                            external = externalDemandPlanner.solveDemands(network, cycle, boundaryDemands, inventory,
                                    acyclic.state(), additionalReservations, delegatedInputs,
                                    ignorePatternSubstitutions, cancellation);
                            externalDemandStatus = external.status();
                            externalMissingItems = external.missingLeaves();
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
                        }
                        if (!external.solved()) {
                            // Keep the failed cycle explanatory only, but surface its concrete leaf deficits through
                            // the AE2 plan. Without this propagation a PARTIAL plan contains a large task vector with
                            // an empty missing-items counter, so confirmation reports a generic "missing materials"
                            // error and the ECO executor has no actionable schedule.
                            if (!externalMissingItems.isEmpty()) {
                                acyclic.state().markMissing(externalMissingItems);
                            } else if (cycleResult != null && !cycleResult.seedShortfall().isEmpty()) {
                                acyclic.state().markMissing(cycleResult.seedShortfall());
                            } else if (!requiredOutputs.isEmpty()) {
                                acyclic.state().markCycleMissing(requiredOutputs);
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
                            Map<AEKey, PlannerAmount> usedBefore = Map.copyOf(acyclic.state().usedAmounts());
                            boolean hasFirings = hasPositiveFirings(cycleResult);
                            if (!hasFirings && !stockCoversRequiredOutputs(requiredOutputs, initialReservations)) {
                                cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                                diagnostic = "Zero-firing cycle solve did not reserve its required outputs";
                                unresolvedCycle = true;
                            } else if (!acyclic.state().applyCycleTransaction(cycle.componentId(), requiredOutputs,
                                    cycleResult, initialReservations,
                                    plannedCycleInputs, additionalReservations, inventory, external.directReservations(),
                                    external.states())) {
                                cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                                diagnostic = "Cycle/external-DAG transaction validation failed";
                                unresolvedCycle = true;
                            } else {
                                external.delegatedCycleDemands().forEach((key, demand) -> {
                                    CycleComponent supplier = cyclicSupplier(network, resolvedCondensation,
                                            resolvedChoices, key, cycle.componentId());
                                    if (supplier == null) {
                                        throw new IllegalStateException("Delegated cycle input lost its supplier: " + key);
                                    }
                                    delegatedCycleDemands
                                            .computeIfAbsent(supplier.componentId(), ignored -> new LinkedHashMap<>())
                                            .merge(key, PlannerAmount.of(demand), PlannerAmount::add);
                                    var boundary = cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand
                                            .boundary(cycle.componentId(), key, PlannerAmount.of(demand));
                                    acyclic.state().provenance.register(boundary);
                                    acyclic.state().provenance.allocate(boundary, key,
                                            new cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource.CycleOutput(
                                                    supplier.componentId()), PlannerAmount.of(demand));
                                });
                                stockReservations = mergeReservations(stockReservations,
                                        reservationDelta(usedBefore, acyclic.state().usedAmounts()));
                                disposition = hasFirings ? cycleExecutionDisposition(cycle, cycleResult)
                                        : stockCoversRequiredOutputs(requiredOutputs, stockReservations)
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
                                    acyclic.state().markMissing(externalMissingItems);
                                } else if (!startupRecoveryAttempted && !cycleResult.seedShortfall().isEmpty()) {
                                    acyclic.state().markMissing(cycleResult.seedShortfall());
                                } else if (!startupRecoveryAttempted && !requiredOutputs.isEmpty()) {
                                    acyclic.state().markCycleMissing(requiredOutputs);
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
                componentResults.add(new ComponentPlanningResult(cycle.componentId(),
                        ComponentPlanningResult.Type.CYCLIC,
                        componentStatus(exactRequiredOutputs, cycleStatus, disposition),
                        requiredOutputs, cycle.patterns().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()),
                        selectedCycleExecutionPatterns(cycleResult),
                        cycleStatus, externalDemandStatus, externalMissingItems, diagnostic, cycleResult,
                        disposition, stockReservations));
                if (disposition != CycleExecutionDisposition.BLOCKED
                        && disposition != CycleExecutionDisposition.NOT_REQUIRED) {
                    stockReservations.forEach((key, reserved) ->
                            attributedCycleReservations.merge(key, reserved, Math::addExact));
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
                cycleDiagnostics.add(diagnostic(cycle, inventory, cycleResult, trace));
                ECOPlanningStageLogger.finish("cycle_calculation[" + cycle.componentId() + "]", cycleStartedNanos,
                        cycleStatus == CyclePlanningStatus.SOLVED || cycleStatus == CyclePlanningStatus.NOT_REQUIRED,
                        diagnostic == null ? cycleStatus.name() : cycleStatus + ":" + diagnostic);
            } catch (InterruptedException | RuntimeException e) {
                ECOPlanningStageLogger.finish("cycle_calculation[" + cycle.componentId() + "]", cycleStartedNanos,
                        false, ECOPlanningStageLogger.exceptionReason(e));
                throw e;
            }
        }

        PlanningStatus status = acyclic.status();
        if (amountUnrepresentable || !acyclic.state().executionAmountIssues().isEmpty()) {
            status = PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE;
        }
        if (unresolvedCycle && (status == PlanningStatus.SUCCESS || status == PlanningStatus.MISSING_ITEMS)) {
            status = acyclic.state().hasPlannedCrafting() || status == PlanningStatus.MISSING_ITEMS
                    ? PlanningStatus.PARTIAL : PlanningStatus.CYCLE_UNRESOLVED;
        }
        validateProvenanceCoverage(network, acyclic.state(), componentResults, trace);
        return new Outcome(status, acyclic.state(), trace, List.copyOf(cycleDiagnostics),
                List.copyOf(componentResults), activeCondensation.executionOrder().stream()
                .map(c -> c.componentId()).toList());
    }

    /** Retry producer routes, including cyclic alternatives, when the preferred route cannot be supplied. */
    public Outcome planWithCycleFallback(CompiledNetwork network, CondensationGraph universe,
                        ActiveRouteSelector.Selection activeSelection, KeyCounter inventory,
                        PlannerInventorySnapshot snapshot, long amount, boolean ignorePatternSubstitutions,
                        ECOCancellation cancellation) throws InterruptedException {
        CycleFailureCache failures = new CycleFailureCache(cycleSolver);
        Outcome preferred = plan(network, activeSelection, inventory, snapshot, amount, true,
            ignorePatternSubstitutions, cancellation, failures);
        if (preferred.status() == PlanningStatus.SUCCESS) return preferred;
        // A local seed shortfall does not prove that a different route cannot bypass the cycle.
        // Stop globally only when even an optimistic closure over ALL producers cannot reach the goal.
        if (preferred.trace().cycles().stream().anyMatch(cycle -> cycle.solveResult() != null
                    && cycle.solveResult().status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT)
                && RouteAvailabilityProof.goalUnreachable(network, snapshot, cancellation)) {
            preferred.trace().addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.ROUTE_PROVEN_UNREACHABLE,
                    "No producer route can start from the current stock, even with quantities and consumption ignored; "
                            + "retaining the current route's material deficits"));
            return preferred;
        }
        // Normalize every key so semantically identical choices share one invocation-local cache entry.
        Map<AEKey, Integer> baseline = new Object2ObjectLinkedOpenHashMap<>();
        var preferredChoices = new IntArrayList();
        List<AEKey> variableKeys = new ArrayList<>();
        var radices = new IntArrayList();
        for (AEKey key : universe.source().nodes().keySet()) {
            cancellation.checkpoint();
            List<CompiledPattern> candidates = network.fastProducersOf(key);
            int selected = Math.max(0, Math.min(activeSelection.choices().getOrDefault(key, 0),
                Math.max(0, candidates.size() - 1)));
            int resolved = candidates.indexOf(preferred.state().selected.get(key));
            baseline.put(key, resolved >= 0 ? resolved : selected);
            if (candidates.size() > 1) {
                variableKeys.add(key);
                radices.add(candidates.size());
                preferredChoices.add(selected);
            }
        }
        // Cache compact immutable vectors, not a boxed map of every node for every attempt.
        Set<int[]> attempted = new ObjectOpenCustomHashSet<>(IntArrays.HASH_STRATEGY);
        attempted.add(preferredChoices.toIntArray());
        int attempts = 1;
        var combinations = new RouteCombinations(radices);
        while (combinations.hasNext()) {
            cancellation.checkpoint();
            int[] offsets = combinations.next();
            int[] vector = new int[offsets.length];
            for (int i = 0; i < offsets.length; i++) {
                AEKey key = variableKeys.get(i);
                vector[i] = (baseline.get(key) + offsets[i]) % radices.getInt(i);
            }
            if (!attempted.add(vector)) continue;
            if (attempts >= maxRouteAttempts) {
                preferred.trace().addDiagnostic(new PlannerDiagnostic(
                    PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED,
                    "Producer route search stopped after " + attempts
                        + " attempts; unvisited combinations may still be feasible"));
                return new Outcome(PlanningStatus.CYCLE_UNRESOLVED, preferred.state(), preferred.trace(),
                    preferred.cycles(), preferred.components(), preferred.executionComponentOrder());
            }
            attempts++;
            Map<AEKey, Integer> choices = new Object2ObjectLinkedOpenHashMap<>(baseline);
            for (int i = 0; i < vector.length; i++) choices.put(variableKeys.get(i), vector[i]);
            ActiveRouteSelector.Selection candidate = activeRouteSelector.selectWithChoices(
                universe.source(), choices, cancellation);
            // An upstream change may remove or reduce a cycle demand, or free reserved seed.
            // Deduplicate actual cycle requests after numeric planning, never just changed key names.
            Outcome alternative = plan(network, candidate, inventory, snapshot, amount, true,
                ignorePatternSubstitutions, cancellation, failures);
            if (alternative.status() == PlanningStatus.SUCCESS) return alternative;
        }
        return preferred;
    }

    /** Try cheap single changes before joint changes, retaining only one mixed-radix cursor in memory. */
    private static final class RouteCombinations {
        private final IntList radices;
        private final int[] offsets;
        private boolean baselinePending = true;
        private int singleKey;
        private int singleOffset = 1;
        private boolean combinationsRemain = true;

        private RouteCombinations(IntList radices) {
            this.radices = radices;
            this.offsets = new int[radices.size()];
        }

        private boolean hasNext() {
            return baselinePending || singleKey < offsets.length || combinationsRemain;
        }

        private int[] next() {
            if (baselinePending) {
                baselinePending = false;
                return offsets.clone();
            }
            if (singleKey < offsets.length) {
                int[] single = new int[offsets.length];
                single[singleKey] = singleOffset++;
                if (singleOffset >= radices.getInt(singleKey)) {
                    singleKey++;
                    singleOffset = 1;
                }
                return single;
            }
            int[] result = offsets.clone();
            combinationsRemain = advanceRouteCombination(offsets, radices);
            return result;
        }
    }

    private static boolean advanceRouteCombination(int[] offsets, IntList radices) {
        for (int i = offsets.length - 1; i >= 0; i--) {
            if (++offsets[i] < radices.getInt(i)) return true;
            offsets[i] = 0;
        }
        return false;
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

    private static CycleSolveRequest.PlannerOptions cycleSolveOptions(CycleComponent cycle) {
        Set<AEKey> keys = new ObjectOpenHashSet<>(cycle.members());
        for (CompiledPattern pattern : cycle.patterns()) {
            pattern.inputs().forEach(input -> keys.add(input.key()));
            pattern.grossOutputs().forEach(output -> keys.add(output.what()));
        }
        boolean large = keys.size() > CycleSolveLimits.DEFAULT.maxKeys()
                || cycle.patterns().size() > CycleSolveLimits.DEFAULT.maxPatterns();
        return new CycleSolveRequest.PlannerOptions(large ? CycleSolveLimits.LARGE : CycleSolveLimits.DEFAULT);
    }

    private static CycleExecutionDisposition cycleExecutionDisposition(CycleComponent cycle, CycleSolveResult result) {
        // A firing vector alone does not preserve liveness when transitions compete for one seed.
        // Keep the solver's verified witness for every cycle size.
        return !result.executionPlan().isEmpty()
                ? CycleExecutionDisposition.ORDERED_EXECUTION
                : CycleExecutionDisposition.DYNAMIC_EXECUTION;
    }

    private static Map<AEKey, Long> relevantStock(CycleComponent cycle, java.util.Set<AEKey> requiredOutputs,
                                                  KeyCounter inventory, SolveState state, Map<AEKey, Long> componentReservations) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (AEKey member : cycle.members()) result.put(member, remaining(inventory, state, member));
        for (AEKey required : requiredOutputs) result.putIfAbsent(required, remaining(inventory, state, required));
        for (var dependency : cycle.outgoingDependencies()) {
            for (var relationship : dependency.relationships()) {
                AEKey key = relationship.requiredInput();
                result.putIfAbsent(key, remaining(inventory, state, key));
            }
        }
        componentReservations.forEach((key, reserved) -> {
            if (state.stored.isUnbounded(key)) result.put(key, Long.MAX_VALUE);
            else result.merge(key, reserved, Math::addExact);
        });
        return Map.copyOf(result);
    }

    private static Set<AEKey> delegatedCycleInputs(CompiledNetwork network, CondensationGraph condensation,
                                                   Map<AEKey, Integer> choices, CycleComponent consumer, Set<AEKey> inputs) {
        Set<AEKey> delegated = new ObjectOpenHashSet<>();
        inputs.forEach(key -> {
            CycleComponent supplier = cyclicSupplier(network, condensation, choices, key, consumer.componentId());
            if (supplier != null) {
                delegated.add(key);
            }
        });
        return Set.copyOf(delegated);
    }

    private static Map<AEKey, Long> mergeDemands(Map<AEKey, Long> first, Map<AEKey, Long> second) {
        Map<AEKey, Long> result = new LinkedHashMap<>(first);
        second.forEach((key, amount) -> {
            if (amount != null && amount > 0L) result.merge(key, amount, Math::addExact);
        });
        return Map.copyOf(result);
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
        return Set.copyOf(result);
    }

    private static Set<IPatternDetails> selectedCycleExecutionPatterns(@Nullable CycleSolveResult result) {
        if (result == null || result.status() != CycleSolveStatus.SUCCESS) return Set.of();
        return result.patternTimes().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * Stock the DAG pass has not already spent. The cycle solver must never double-spend an item.
     */
    private static long remaining(KeyCounter inventory, SolveState state, AEKey key) {
        if (state.stored.isUnbounded(key)) return Long.MAX_VALUE;
        PlannerAmount available = PlannerAmount.of(inventory.get(key));
        PlannerAmount used = state.used.get(key);
        return available.compareTo(used) > 0 ? available.subtract(used).longValueExact() : 0L;
    }

    /**
     * Cycle stock may enable the witness, but AE2 crafting amounts mean newly requested output. Without this
     * translation, stored copies of the final output satisfy the solver target and produce an empty CPU job.
     */
    private static Map<AEKey, PlannerAmount> additionalOutputTargets(Map<AEKey, PlannerAmount> requiredOutputs,
                                                                     Map<AEKey, Long> relevantStock, AEKey finalGoal) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        requiredOutputs.forEach((key, amount) -> {
            // Intermediate demand already includes the stock reserved by the DAG pass. Adding it again
            // makes a 23-stock / 51-demand growth recipe run 51 times instead of the required 28.
            if (!key.equals(finalGoal)) {
                result.put(key, amount);
                return;
            }
            // AE2 asks the CPU to craft the final output anew, even when copies are already stored.
            result.put(key, amount.add(Math.max(0L, relevantStock.getOrDefault(key, 0L))));
        });
        return Map.copyOf(result);
    }

    /**
     * Computes the inventory that must be owned before the cycle transaction starts.
     */
    private static Map<AEKey, Long> cycleInitialReservations(Map<AEKey, PlannerAmount> requiredOutputs,
                                                             Map<AEKey, Long> startingStock, CycleSolveResult result) {
        Map<AEKey, Long> reservations = new LinkedHashMap<>();
        result.requiredSeed().forEach((key, amount) -> {
            if (amount != null && amount > 0L) {
                long stockBacked = Math.min(amount, Math.max(0L, startingStock.getOrDefault(key, 0L)));
                if (stockBacked > 0L) reservations.put(key, stockBacked);
            }
        });
        requiredOutputs.forEach((key, exactDemand) -> {
            if (!exactDemand.fitsLong() || exactDemand.signum() <= 0) return;
            long demand = exactDemand.longValueExact();
            long initial = Math.max(0L, startingStock.getOrDefault(key, 0L));
            long deliverable = Math.max(0L, result.deliverableOutputs().getOrDefault(key, 0L));
            // deliverable is the ending balance after the verified witness runs from initial stock. Preserve enough
            // of that starting stock to cover both any net cycle consumption and the balance owed to downstream.
            long endingSurplus = deliverable > demand ? deliverable - demand : 0L;
            long stockBacked = initial - Math.min(initial, endingSurplus);
            if (stockBacked > 0L) reservations.merge(key, stockBacked, Math::max);
        });
        return Map.copyOf(reservations);
    }

    private static Map<AEKey, Long> reservationDelta(Map<AEKey, PlannerAmount> before,
                                                     Map<AEKey, PlannerAmount> after) {
        Map<AEKey, Long> delta = new LinkedHashMap<>();
        after.forEach((key, amount) -> {
            PlannerAmount difference = amount.subtract(before.getOrDefault(key, PlannerAmount.ZERO));
            if (difference.signum() > 0) delta.put(key, difference.longValueExact());
        });
        return Map.copyOf(delta);
    }

    /**
     * Stock already consumed by the acyclic pass for a deferred cycle output. This is a projection, not a write.
     */
    private static Map<AEKey, Long> existingComponentReservations(Map<AEKey, PlannerAmount> required,
                                                                  SolveState state, Map<AEKey, Long> alreadyAttributed) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        required.forEach((key, amount) -> {
            if (amount == null || amount.signum() <= 0) return;
            PlannerAmount used = state.usedAmounts().getOrDefault(key, PlannerAmount.ZERO);
            if (used.signum() <= 0) return;
            PlannerAmount available = used.subtract(
                    PlannerAmount.of(alreadyAttributed.getOrDefault(key, 0L))).max(PlannerAmount.ZERO);
            PlannerAmount reserved = amount.min(available);
            if (reserved.signum() > 0) {
                result.put(key, reserved.fitsLong() ? reserved.longValueExact() : Long.MAX_VALUE);
            }
        });
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> reservationRemainder(Map<AEKey, Long> required,
                                                         Map<AEKey, Long> alreadyOwned) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        required.forEach((key, amount) -> {
            long remainder = amount - Math.min(amount, alreadyOwned.getOrDefault(key, 0L));
            if (remainder > 0L) result.put(key, remainder);
        });
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> mergeReservations(Map<AEKey, Long> first, Map<AEKey, Long> second) {
        Map<AEKey, Long> result = new LinkedHashMap<>(first);
        second.forEach((key, amount) -> result.merge(key, amount, Math::addExact));
        return Map.copyOf(result);
    }

    private static boolean stockCoversRequiredOutputs(Map<AEKey, Long> required,
                                                      Map<AEKey, Long> reservations) {
        if (required.isEmpty()) return false;
        return required.entrySet().stream().allMatch(entry -> entry.getValue() != null && entry.getValue() > 0L
                && reservations.getOrDefault(entry.getKey(), 0L) >= entry.getValue());
    }

    private static boolean hasPositiveFirings(CycleSolveResult result) {
        return result != null && result.patternTimes().values().stream()
                .anyMatch(count -> count != null && count > 0L);
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
        return new CycleDiagnostic(List.copyOf(diagnosticKeys),
                cycle.patterns().stream().map(p -> p.details()).toList(),
                exactNet, exactTotal, Map.of(),
                cycleResult == null ? cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge.UNKNOWN
                        : cycleResult.executionCountKnowledge(),
                cycleResult == null ? CycleSolveStatus.NOT_IMPLEMENTED : cycleResult.status())
                .withAvailableAmounts(inventory);
    }

    private static Map<AEKey, Long> representable(Map<AEKey, PlannerAmount> exact) {
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
