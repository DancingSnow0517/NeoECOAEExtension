package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleDiagnostic;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ComponentTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.CycleTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Numeric planner whose only traversal input is the SCC condensation DAG. */
public final class ComponentPlanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentPlanner.class);

    public record Outcome(
        PlanningStatus status,
        SolveState state,
        ECOPlanTrace trace,
        List<CycleDiagnostic> cycles,
        List<ComponentPlanningResult> components,
        List<Integer> executionComponentOrder
    ) {}

    private final AcyclicCraftingSolver acyclicSolver;
    private final CycleSolver cycleSolver;
    private final ActiveRouteSelector activeRouteSelector;
    private final ExternalDemandPlanner externalDemandPlanner;

    public ComponentPlanner(AcyclicCraftingSolver acyclicSolver, CycleSolver cycleSolver) {
        this.acyclicSolver = acyclicSolver;
        this.cycleSolver = cycleSolver;
        this.activeRouteSelector = new ActiveRouteSelector();
        this.externalDemandPlanner = new ExternalDemandPlanner(acyclicSolver);
    }

    public Outcome plan(CompiledNetwork network, CondensationGraph condensation, KeyCounter inventory,
            long amount, boolean cyclePlanningEnabled, ECOCancellation cancellation) throws InterruptedException {
        ActiveRouteSelector.Selection activeSelection = selectRoutes(condensation, cyclePlanningEnabled,
            cancellation);
        return plan(network, activeSelection, inventory, amount, cyclePlanningEnabled, cancellation);
    }

    public ActiveRouteSelector.Selection selectRoutes(CondensationGraph condensation,
            boolean cyclePlanningEnabled, ECOCancellation cancellation) throws InterruptedException {
        // Route selection and cycle avoidance are structural planning steps, not cycle solving. The toggle only
        // controls what happens after the selected active graph still contains an unavoidable cyclic SCC.
        return activeRouteSelector.select(condensation.source(), true, cancellation);
    }

    public Outcome plan(CompiledNetwork network, ActiveRouteSelector.Selection activeSelection,
            KeyCounter inventory, long amount, boolean cyclePlanningEnabled,
            ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        LOGGER.debug(
            "[ECO-CYCLE-DEBUG] enabled={} acyclic={} components={} cyclicComponents={} deferredCandidates={} "
                + "cycleMembers={}",
            cyclePlanningEnabled, activeSelection.acyclic(), activeSelection.condensation().components().size(),
            activeSelection.cyclicComponents().size(), activeSelection.deferredCyclicCandidates().size(),
            activeSelection.cyclicComponents().stream().map(CycleComponent::members).toList());
        CondensationGraph activeCondensation = activeSelection.condensation();
        List<AEKey> dagOrder = activeCondensation.topologicalOrder().stream()
            .filter(AcyclicComponent.class::isInstance)
            .map(AcyclicComponent.class::cast)
            .map(AcyclicComponent::key)
            .toList();
        var cycleOwnedPatterns = activeSelection.cyclicComponents().stream()
            .flatMap(cycle -> cycle.patterns().stream())
            .map(CompiledPattern::details)
            .collect(java.util.stream.Collectors.toSet());
        var acyclic = acyclicSolver.solve(network, new AcyclicRoutePlan(dagOrder), inventory, amount,
            activeSelection.choices(), cycleOwnedPatterns, cancellation);
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
                    selectedExecutionPattern(acyclic.state(), acyclicComponent.key()),
                    null, null, Map.of(), null, null, CycleExecutionDisposition.NOT_REQUIRED, Map.of()));
                continue;
            }

            CycleComponent cycle = (CycleComponent) component;
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
                    if (exactDemand.fitsLong()) requiredOutputs.put(selected.getKey(), exactDemand.longValueExact());
                }
            }
            CyclePlanningStatus cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
            String diagnostic = null;
            CycleSolveResult cycleResult = null;
            CycleExternalDemandStatus externalDemandStatus = null;
            Map<AEKey, Long> externalMissingItems = Map.of();
            CycleExecutionDisposition disposition = exactRequiredOutputs.isEmpty()
                ? CycleExecutionDisposition.NOT_REQUIRED : CycleExecutionDisposition.BLOCKED;
            Map<AEKey, Long> stockReservations = existingComponentReservations(requiredOutputs, acyclic.state());
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
                    cycleResult = cycleSolver.solve(new CycleSolveRequest(cycle, representable(solveTargets),
                        solveTargets, stock, cycle.outgoingDependencies(), new CycleSolveRequest.PlannerOptions()),
                        cancellation);
                    cycleStatus = CyclePlanningStatus.of(cycleResult.status());
                    diagnostic = cycleResult.summary();
                    if (cycleStatus == CyclePlanningStatus.UNREPRESENTABLE) amountUnrepresentable = true;
                    unresolvedCycle |= cycleStatus != CyclePlanningStatus.SOLVED;
                    trace.addDiagnostic(new PlannerDiagnostic(diagnosticCode(cycleStatus), diagnostic));
                }
                if (cycleResult != null && cycleStatus == CyclePlanningStatus.SOLVED) {
                    Map<AEKey, Long> initialReservations = cycleInitialReservations(
                        exactRequiredOutputs, stock, cycleResult);
                    Map<AEKey, Long> additionalReservations = reservationRemainder(
                        initialReservations, stockReservations);
                    var external = externalDemandPlanner.solve(network, cycle, cycleResult, inventory,
                        acyclic.state(), additionalReservations, cancellation);
                    externalDemandStatus = external.status();
                    externalMissingItems = external.missingLeaves();
                    trace.addDiagnostic(new PlannerDiagnostic(externalDiagnosticCode(external.status()),
                        external.diagnostic()));
                    if (!external.solved()) {
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
                    } else {
                        // PlannerCounter exposes an immutable view, not an immutable snapshot. Freeze it before
                        // replaceWith() so the component projection observes the committed delta exactly once.
                        Map<AEKey, PlannerAmount> usedBefore = Map.copyOf(acyclic.state().usedAmounts());
                        boolean hasFirings = hasPositiveFirings(cycleResult);
                        if (!hasFirings && !stockCoversRequiredOutputs(requiredOutputs, initialReservations)) {
                            cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                            diagnostic = "Zero-firing cycle solve did not reserve its required outputs";
                            unresolvedCycle = true;
                        } else if (!acyclic.state().applyCycleTransaction(cycleResult, initialReservations,
                            additionalReservations, inventory, external.directReservations(), external.states())) {
                            cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                            diagnostic = "Cycle/external-DAG transaction validation failed";
                            unresolvedCycle = true;
                        } else {
                            stockReservations = mergeReservations(stockReservations,
                                reservationDelta(usedBefore, acyclic.state().usedAmounts()));
                            disposition = hasFirings ? CycleExecutionDisposition.ORDERED_EXECUTION
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
            }
            if (cyclePlanningEnabled) {
                LOGGER.debug(
                    "[ECO-CYCLE-DEBUG] componentId={} members={} requiredOutputs={} cycleStatus={} "
                        + "cycleResultStatus={} cyclePatternTimes={} externalDemandStatus={} disposition={} "
                        + "stockReservations={} ",
                    cycle.componentId(), cycle.members(), exactRequiredOutputs, cycleStatus,
                    cycleResult == null ? null : cycleResult.status(),
                    cycleResult == null ? Map.of() : cycleResult.patternTimes(), externalDemandStatus,
                    disposition, stockReservations);
            }
            List<cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphEdge> externalEdges = cycle
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
            componentResults.add(new ComponentPlanningResult(cycle.componentId(),
                ComponentPlanningResult.Type.CYCLIC,
                componentStatus(exactRequiredOutputs, cycleStatus),
                requiredOutputs, cycle.patterns().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()),
                selectedCycleExecutionPatterns(cycleResult),
                cycleStatus, externalDemandStatus, externalMissingItems, diagnostic, cycleResult,
                disposition, stockReservations));
            cycleDiagnostics.add(diagnostic(cycle, inventory, cycleResult, trace));
        }

        PlanningStatus status = acyclic.status();
        if (amountUnrepresentable || !acyclic.state().executionAmountIssues().isEmpty()) {
            status = PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE;
        }
        if (unresolvedCycle && (status == PlanningStatus.SUCCESS || status == PlanningStatus.MISSING_ITEMS)) {
            status = acyclic.state().hasPlannedCrafting() || status == PlanningStatus.MISSING_ITEMS
                ? PlanningStatus.PARTIAL : PlanningStatus.CYCLE_UNRESOLVED;
        }
        if (cyclePlanningEnabled) {
            LOGGER.debug(
                "[ECO-CYCLE-DEBUG] final status={} unresolvedCycle={} amountUnrepresentable={} "
                    + "statePatternTimes={} stateUsed={} stateEmitted={} stateMissing={}",
                status, unresolvedCycle, amountUnrepresentable, acyclic.state().plannerPatternTimes(),
                acyclic.state().usedAmounts(), acyclic.state().emittedAmounts(), acyclic.state().missingAmounts());
        }
        return new Outcome(status, acyclic.state(), trace, List.copyOf(cycleDiagnostics),
            List.copyOf(componentResults), activeCondensation.executionOrder().stream()
                .map(c -> c.componentId()).toList());
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
        componentReservations.forEach((key, reserved) -> result.merge(key, reserved, Math::addExact));
        return Map.copyOf(result);
    }

    private static Set<IPatternDetails> selectedExecutionPattern(SolveState state, AEKey key) {
        CompiledPattern selected = state.selected.get(key);
        if (selected == null || state.patternTimes
                .getOrDefault(selected.details(), PlannerAmount.ZERO).signum() <= 0) {
            return Set.of();
        }
        return Set.of(selected.details());
    }

    private static Set<IPatternDetails> selectedCycleExecutionPatterns(@Nullable CycleSolveResult result) {
        if (result == null || result.status() != CycleSolveStatus.SUCCESS) return Set.of();
        return result.patternTimes().entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** Stock the DAG pass has not already spent. The cycle solver must never double-spend an item. */
    private static long remaining(KeyCounter inventory, SolveState state, AEKey key) {
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
            if (!key.equals(finalGoal)) {
                result.put(key, amount);
                return;
            }
            result.put(key, amount.add(Math.max(0L, relevantStock.getOrDefault(key, 0L))));
        });
        return Map.copyOf(result);
    }

    /**
     * Computes the inventory that must be owned before the cycle transaction starts. A key that is both startup
     * seed and stock-backed output is reserved once, at the larger requirement.
     */
    private static Map<AEKey, Long> cycleInitialReservations(Map<AEKey, PlannerAmount> requiredOutputs,
            Map<AEKey, Long> startingStock, CycleSolveResult result) {
        Map<AEKey, Long> reservations = new LinkedHashMap<>();
        result.requiredSeed().forEach((key, amount) -> {
            if (amount != null && amount > 0L) reservations.put(key, amount);
        });
        requiredOutputs.forEach((key, exactDemand) -> {
            if (!exactDemand.fitsLong() || exactDemand.signum() <= 0) return;
            long demand = exactDemand.longValueExact();
            long initial = Math.max(0L, startingStock.getOrDefault(key, 0L));
            long deliverable = Math.max(0L, result.deliverableOutputs().getOrDefault(key, 0L));
            long netNew = deliverable > initial ? deliverable - initial : 0L;
            long stockBacked = demand - Math.min(demand, netNew);
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

    /** Stock already consumed by the acyclic pass for a deferred cycle output. This is a projection, not a write. */
    private static Map<AEKey, Long> existingComponentReservations(Map<AEKey, Long> required,
            SolveState state) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        required.forEach((key, amount) -> {
            if (amount == null || amount <= 0L) return;
            PlannerAmount used = state.usedAmounts().getOrDefault(key, PlannerAmount.ZERO);
            if (used.signum() > 0) result.put(key, Math.min(amount, used.longValueExact()));
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
            CyclePlanningStatus status) {
        if (status == CyclePlanningStatus.UNREPRESENTABLE) return ComponentPlanningResult.Status.UNREPRESENTABLE;
        if (requiredOutputs.isEmpty()) return ComponentPlanningResult.Status.NOT_REQUIRED;
        return switch (status) {
            case SOLVED -> ComponentPlanningResult.Status.SOLVED_NOT_EMITTED;
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
        for (AEKey member : cycle.members()) exactNet.put(member, PlannerAmount.ZERO);
        Set<IPatternDetails> countedPatterns = new HashSet<>();
        for (var pattern : cycle.patterns()) {
            if (!countedPatterns.add(pattern.details())) continue;
            for (var output : pattern.grossOutputs()) if (exactNet.containsKey(output.what())) {
                exactNet.put(output.what(), exactNet.get(output.what()).add(output.amount()));
            }
            for (CompiledInput input : pattern.inputs()) if (exactNet.containsKey(input.key())) {
                exactNet.put(input.key(), exactNet.get(input.key()).subtract(input.amountPerPattern()));
            }
        }
        Map<AEKey, PlannerAmount> exactTotal = new LinkedHashMap<>();
        if (cycleResult != null && cycleResult.status() == CycleSolveStatus.SUCCESS) {
            for (AEKey member : cycle.members()) exactTotal.put(member, PlannerAmount.ZERO);
            countedPatterns.clear();
            for (var pattern : cycle.patterns()) {
                if (!countedPatterns.add(pattern.details())) continue;
                long times = cycleResult.patternTimes().getOrDefault(pattern.details(), 0L);
                if (times == 0) continue;
                for (var output : pattern.grossOutputs()) if (exactTotal.containsKey(output.what())) {
                    exactTotal.put(output.what(), exactTotal.get(output.what()).add(
                        PlannerAmount.of(output.amount()).multiply(times)));
                }
                for (CompiledInput input : pattern.inputs()) if (exactTotal.containsKey(input.key())) {
                    exactTotal.put(input.key(), exactTotal.get(input.key()).subtract(
                        input.amountPerPattern().multiply(times)));
                }
            }
        }
        addWideCycleDiagnostics(trace, exactNet, "cycle net output");
        addWideCycleDiagnostics(trace, exactTotal, "cycle total net output");
        return new CycleDiagnostic(cycle.members(), cycle.patterns().stream().map(p -> p.details()).toList(),
            representable(exactNet), representable(exactTotal), Map.of()).withAvailableAmounts(inventory);
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
