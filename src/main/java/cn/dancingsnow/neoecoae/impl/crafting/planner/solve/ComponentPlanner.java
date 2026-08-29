package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleDiagnostic;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ComponentTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.CycleTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public ComponentPlanner(AcyclicCraftingSolver acyclicSolver, CycleSolver cycleSolver) {
        this.acyclicSolver = acyclicSolver;
        this.cycleSolver = cycleSolver;
        this.activeRouteSelector = new ActiveRouteSelector();
    }

    public Outcome plan(CompiledNetwork network, CondensationGraph condensation, KeyCounter inventory,
            long amount, boolean cyclePlanningEnabled, ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        ActiveRouteSelector.Selection activeSelection = activeRouteSelector.select(condensation.source(), cancellation);
        CondensationGraph activeCondensation = activeSelection.condensation();
        List<AEKey> dagOrder = activeCondensation.topologicalOrder().stream()
            .filter(AcyclicComponent.class::isInstance)
            .map(AcyclicComponent.class::cast)
            .map(AcyclicComponent::key)
            .toList();
        var acyclic = acyclicSolver.solve(network, new AcyclicRoutePlan(dagOrder), inventory, amount,
            activeSelection.choices(), cancellation);
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
                    acyclicComponent.patterns().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()), null, null, null));
                continue;
            }

            CycleComponent cycle = (CycleComponent) component;
            Map<AEKey, Long> requiredOutputs = new LinkedHashMap<>();
            for (AEKey member : cycle.members()) {
                long demand = acyclic.state().demandFor(member);
                if (demand > 0) requiredOutputs.put(member, demand);
            }
            CyclePlanningStatus cycleStatus;
            String diagnostic = null;
            CycleSolveResult cycleResult = null;
            if (requiredOutputs.isEmpty()) {
                cycleStatus = CyclePlanningStatus.NOT_REQUIRED;
            } else if (!cyclePlanningEnabled) {
                cycleStatus = CyclePlanningStatus.DISABLED;
                diagnostic = "Cycle planning is disabled";
                unresolvedCycle = true;
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_DISABLED, diagnostic));
            } else {
                // The cycle solver is a plug-in: it receives a snapshot and never touches the DAG workspace.
                Map<AEKey, Long> stock = relevantStock(cycle, inventory, acyclic.state());
                cycleResult = cycleSolver.solve(new CycleSolveRequest(cycle, requiredOutputs, stock,
                    cycle.outgoingDependencies(), new CycleSolveRequest.PlannerOptions(true)), cancellation);
                cycleStatus = CyclePlanningStatus.of(cycleResult.status());
                diagnostic = cycleResult.summary();
                unresolvedCycle |= cycleStatus != CyclePlanningStatus.SOLVED;
                trace.addDiagnostic(new PlannerDiagnostic(diagnosticCode(cycleStatus), diagnostic));
                if (cycleStatus == CyclePlanningStatus.SOLVED) {
                    // Commit only after the complete solver answer has been validated. External demand is
                    // reserved from the remaining stock here; a future route-aware pass may replace this
                    // reservation with crafted intermediates without changing the transaction boundary.
                    if (cycleResult.positiveExternalDemand().entrySet().stream().anyMatch(e ->
                            e.getValue() > remaining(inventory, acyclic.state(), e.getKey()))) {
                        cycleStatus = CyclePlanningStatus.INSUFFICIENT_EXTERNAL_INPUT;
                        diagnostic = "External demand is unavailable: " + cycleResult.positiveExternalDemand();
                        unresolvedCycle = true;
                    } else if (!acyclic.state().applyCycleSolution(cycleResult, inventory)) {
                        cycleStatus = CyclePlanningStatus.UNKNOWN_BUDGET;
                        diagnostic = "Cycle solution validation failed";
                        unresolvedCycle = true;
                    } else {
                        trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_SOLVED,
                            "Cycle merged with " + cycleResult.totalFirings() + " firing(s)"));
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
                requiredOutputs, cycle.patterns().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()), cycleStatus, diagnostic, cycleResult));
            cycleDiagnostics.add(diagnostic(cycle, inventory));
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

    private static Map<AEKey, Long> relevantStock(CycleComponent cycle, KeyCounter inventory, SolveState state) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (AEKey member : cycle.members()) result.put(member, remaining(inventory, state, member));
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

    private static CycleDiagnostic diagnostic(CycleComponent cycle, KeyCounter inventory) {
        Map<AEKey, Long> net = new LinkedHashMap<>();
        for (AEKey member : cycle.members()) net.put(member, 0L);
        for (var pattern : cycle.patterns()) {
            for (var output : pattern.outputs()) if (net.containsKey(output.what())) {
                net.put(output.what(), saturatedAdd(net.get(output.what()), output.amount()));
            }
            for (CompiledInput input : pattern.inputs()) if (net.containsKey(input.key())) {
                net.put(input.key(), saturatedAdd(net.get(input.key()), -input.amountPerPattern()));
            }
        }
        return new CycleDiagnostic(cycle.members(), cycle.patterns().stream().map(p -> p.details()).toList(),
            net, Map.of()).withAvailableAmounts(inventory);
    }

    private static long saturatedAdd(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException ignored) { return right >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE; }
    }
}
