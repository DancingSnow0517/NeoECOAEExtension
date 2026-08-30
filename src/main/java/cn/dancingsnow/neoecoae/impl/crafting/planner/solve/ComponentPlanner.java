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

/** Numeric planner whose only traversal input is the SCC condensation DAG. */
public final class ComponentPlanner {
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
        // Cycle planning controls whether an unavoidable cycle may be solved; it must never disable choosing
        // an available acyclic producer instead.
        return activeRouteSelector.select(condensation.source(), true, cancellation);
    }

    public Outcome plan(CompiledNetwork network, ActiveRouteSelector.Selection activeSelection,
            KeyCounter inventory, long amount, boolean cyclePlanningEnabled,
            ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
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

        for (var component : activeCondensation.topologicalOrder()) {
            cancellation.checkpoint();
            trace.addComponent(new ComponentTrace(component.componentId(), component.cyclic()
                ? ComponentTrace.Type.CYCLIC : ComponentTrace.Type.ACYCLIC, component.members()));
            if (component instanceof AcyclicComponent acyclicComponent) {
                long demand = acyclic.state().demandFor(acyclicComponent.key());
                componentResults.add(new ComponentPlanningResult(component.componentId(),
                    ComponentPlanningResult.Type.ACYCLIC,
                    demand > 0 ? ComponentPlanningResult.Status.PLANNED : ComponentPlanningResult.Status.NOT_REQUIRED,
                    demand > 0 ? Map.of(acyclicComponent.key(), demand) : Map.of(),
                    acyclicComponent.patterns().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()),
                    null, null, Map.of(), null, null));
                continue;
            }

            CycleComponent cycle = (CycleComponent) component;
            Map<AEKey, Long> requiredOutputs = new LinkedHashMap<>();
            for (AEKey member : cycle.members()) {
                long demand = acyclic.state().demandFor(member);
                if (demand > 0) requiredOutputs.put(member, demand);
            }
            var ownedDetails = cycle.patterns().stream().map(CompiledPattern::details)
                .collect(java.util.stream.Collectors.toSet());
            for (var selected : acyclic.state().selected.entrySet()) {
                if (!ownedDetails.contains(selected.getValue().details())) continue;
                long demand = acyclic.state().demandFor(selected.getKey());
                if (demand > 0) requiredOutputs.put(selected.getKey(), demand);
            }
            CyclePlanningStatus cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
            String diagnostic = null;
            CycleSolveResult cycleResult = null;
            CycleExternalDemandStatus externalDemandStatus = null;
            Map<AEKey, Long> externalMissingItems = Map.of();
            if (requiredOutputs.isEmpty()) {
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
                Map<AEKey, Long> stock = relevantStock(cycle, requiredOutputs.keySet(), inventory, acyclic.state());
                Map<AEKey, Long> solveTargets;
                try {
                    solveTargets = additionalOutputTargets(requiredOutputs, stock, network.goal());
                } catch (ArithmeticException overflow) {
                    solveTargets = Map.of();
                    cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                    diagnostic = "Cycle output target overflow while separating stored stock from new output";
                    unresolvedCycle = true;
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_BUDGET_EXHAUSTED,
                        diagnostic));
                }
                if (!solveTargets.isEmpty()) {
                    cycleResult = cycleSolver.solve(new CycleSolveRequest(cycle, solveTargets, stock,
                        cycle.outgoingDependencies(), new CycleSolveRequest.PlannerOptions()), cancellation);
                    cycleStatus = CyclePlanningStatus.of(cycleResult.status());
                    diagnostic = cycleResult.summary();
                    unresolvedCycle |= cycleStatus != CyclePlanningStatus.SOLVED;
                    trace.addDiagnostic(new PlannerDiagnostic(diagnosticCode(cycleStatus), diagnostic));
                }
                if (cycleResult != null && cycleStatus == CyclePlanningStatus.SOLVED) {
                    var external = externalDemandPlanner.solve(network, cycle, cycleResult, inventory,
                        acyclic.state(), cancellation);
                    externalDemandStatus = external.status();
                    externalMissingItems = external.missingLeaves();
                    trace.addDiagnostic(new PlannerDiagnostic(externalDiagnosticCode(external.status()),
                        external.diagnostic()));
                    if (!external.solved()) {
                        cycleStatus = external.status() == CycleExternalDemandStatus.UNSUPPORTED
                            ? CyclePlanningStatus.UNSUPPORTED : CyclePlanningStatus.INSUFFICIENT_EXTERNAL_INPUT;
                        diagnostic = external.diagnostic();
                        unresolvedCycle = true;
                    } else if (!acyclic.state().applyCycleTransaction(cycleResult, inventory,
                            external.directReservations(), external.states())) {
                        cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                        diagnostic = "Cycle/external-DAG transaction validation failed";
                        unresolvedCycle = true;
                    } else {
                        trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_SOLVED,
                            "Cycle and external DAG merged with " + cycleResult.totalFirings() + " cycle firing(s)"));
                    }
                }
            }
            List<cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphEdge> externalEdges = cycle
                .outgoingDependencies().stream().flatMap(dependency -> dependency.relationships().stream()).toList();
            trace.addCycle(new CycleTrace(cycle.componentId(), cycle.members(), cycle.internalEdges(), externalEdges,
                requiredOutputs, cycleStatus, cycleResult));
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.CYCLE_GROUP, null, null, 0, 0, 0, 0,
                cycleResult == null ? 0 : cycleResult.totalFirings(),
                switch (cycleStatus) {
                    case NOT_REQUIRED -> PlanTraceNode.Selection.NOT_APPLICABLE;
                    case SOLVED -> PlanTraceNode.Selection.SELECTED;
                    default -> PlanTraceNode.Selection.UNSUPPORTED;
                },
                cycleStatus.name()));
            componentResults.add(new ComponentPlanningResult(cycle.componentId(),
                ComponentPlanningResult.Type.CYCLIC,
                componentStatus(requiredOutputs, cycleStatus),
                requiredOutputs, cycle.patterns().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()),
                cycleStatus, externalDemandStatus, externalMissingItems, diagnostic, cycleResult));
            cycleDiagnostics.add(diagnostic(cycle, inventory, cycleResult));
        }

        PlanningStatus status = acyclic.status();
        if (unresolvedCycle && (status == PlanningStatus.SUCCESS || status == PlanningStatus.MISSING_ITEMS)) {
            status = acyclic.state().hasPlannedCrafting() || status == PlanningStatus.MISSING_ITEMS
                ? PlanningStatus.PARTIAL : PlanningStatus.CYCLE_UNRESOLVED;
        }
        return new Outcome(status, acyclic.state(), trace, List.copyOf(cycleDiagnostics),
            List.copyOf(componentResults), activeCondensation.executionOrder().stream()
                .map(c -> c.componentId()).toList());
    }

    private static Map<AEKey, Long> relevantStock(CycleComponent cycle, java.util.Set<AEKey> requiredOutputs,
            KeyCounter inventory, SolveState state) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (AEKey member : cycle.members()) result.put(member, remaining(inventory, state, member));
        for (AEKey required : requiredOutputs) result.putIfAbsent(required, remaining(inventory, state, required));
        for (var dependency : cycle.outgoingDependencies()) {
            for (var relationship : dependency.relationships()) {
                AEKey key = relationship.requiredInput();
                result.putIfAbsent(key, remaining(inventory, state, key));
            }
        }
        return Map.copyOf(result);
    }

    /** Stock the DAG pass has not already spent. The cycle solver must never double-spend an item. */
    private static long remaining(KeyCounter inventory, SolveState state, AEKey key) {
        return Math.max(0L, inventory.get(key) - state.usedItems().get(key));
    }

    /**
     * Cycle stock may enable the witness, but AE2 crafting amounts mean newly requested output. Without this
     * translation, stored copies of the final output satisfy the solver target and produce an empty CPU job.
     */
    private static Map<AEKey, Long> additionalOutputTargets(Map<AEKey, Long> requiredOutputs,
            Map<AEKey, Long> relevantStock, AEKey finalGoal) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        requiredOutputs.forEach((key, amount) ->
            result.put(key, key.equals(finalGoal)
                ? Math.addExact(amount, Math.max(0L, relevantStock.getOrDefault(key, 0L)))
                : amount));
        return Map.copyOf(result);
    }

    private static ComponentPlanningResult.Status componentStatus(Map<AEKey, Long> requiredOutputs,
            CyclePlanningStatus status) {
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
            case CANCELLED -> PlannerDiagnostic.Code.CANCELLED;
            default -> PlannerDiagnostic.Code.CYCLE_NOT_IMPLEMENTED;
        };
    }

    private static PlannerDiagnostic.Code externalDiagnosticCode(CycleExternalDemandStatus status) {
        return switch (status) {
            case SOLVED -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_DEMAND_SOLVED;
            case MISSING -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_DEMAND_MISSING;
            case FORBIDDEN_ROUTE -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_ROUTE_FORBIDDEN;
            case UNSUPPORTED -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_DEMAND_UNSUPPORTED;
            case OVERFLOW -> PlannerDiagnostic.Code.CYCLE_EXTERNAL_DEMAND_OVERFLOW;
        };
    }

    private static CycleDiagnostic diagnostic(CycleComponent cycle, KeyCounter inventory,
            CycleSolveResult cycleResult) {
        Map<AEKey, Long> net = new LinkedHashMap<>();
        for (AEKey member : cycle.members()) net.put(member, 0L);
        Set<IPatternDetails> countedPatterns = new HashSet<>();
        for (var pattern : cycle.patterns()) {
            if (!countedPatterns.add(pattern.details())) continue;
            for (var output : pattern.outputs()) if (net.containsKey(output.what())) {
                net.put(output.what(), saturatedAdd(net.get(output.what()), output.amount()));
            }
            for (CompiledInput input : pattern.inputs()) if (net.containsKey(input.key())) {
                net.put(input.key(), saturatedAdd(net.get(input.key()), -input.amountPerPattern()));
            }
        }
        Map<AEKey, Long> total = new LinkedHashMap<>();
        for (AEKey member : cycle.members()) total.put(member, 0L);
        if (cycleResult == null || cycleResult.status() != CycleSolveStatus.SUCCESS) {
            total.putAll(net);
        } else {
            countedPatterns.clear();
            for (var pattern : cycle.patterns()) {
                if (!countedPatterns.add(pattern.details())) continue;
                long times = cycleResult.patternTimes().getOrDefault(pattern.details(), 0L);
                if (times == 0) continue;
                for (var output : pattern.outputs()) if (total.containsKey(output.what())) {
                    total.put(output.what(), saturatedAdd(total.get(output.what()),
                        saturatedMultiply(output.amount(), times)));
                }
                for (CompiledInput input : pattern.inputs()) if (total.containsKey(input.key())) {
                    total.put(input.key(), saturatedAdd(total.get(input.key()),
                        saturatedMultiply(-input.amountPerPattern(), times)));
                }
            }
        }
        return new CycleDiagnostic(cycle.members(), cycle.patterns().stream().map(p -> p.details()).toList(),
            net, total, Map.of()).withAvailableAmounts(inventory);
    }

    private static long saturatedAdd(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException ignored) { return right >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE; }
    }

    private static long saturatedMultiply(long left, long right) {
        try { return Math.multiplyExact(left, right); }
        catch (ArithmeticException ignored) {
            return (left < 0) == (right < 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }
}
