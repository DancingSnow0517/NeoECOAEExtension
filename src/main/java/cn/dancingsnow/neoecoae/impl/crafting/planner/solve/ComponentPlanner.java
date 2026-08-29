package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest;
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
        List<ComponentPlanningResult> components
    ) {}

    private final AcyclicCraftingSolver acyclicSolver;
    private final CycleSolver cycleSolver;

    public ComponentPlanner(AcyclicCraftingSolver acyclicSolver, CycleSolver cycleSolver) {
        this.acyclicSolver = acyclicSolver;
        this.cycleSolver = cycleSolver;
    }

    public Outcome plan(CompiledNetwork network, CondensationGraph condensation, KeyCounter inventory,
            long amount, boolean cyclePlanningEnabled, ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        List<AEKey> dagOrder = condensation.topologicalOrder().stream()
            .filter(AcyclicComponent.class::isInstance)
            .map(AcyclicComponent.class::cast)
            .map(AcyclicComponent::key)
            .toList();
        var acyclic = acyclicSolver.solve(network, new AcyclicRoutePlan(dagOrder), inventory, amount, cancellation);
        ECOPlanTrace trace = acyclic.trace();
        List<ComponentPlanningResult> componentResults = new ArrayList<>();
        List<CycleDiagnostic> cycleDiagnostics = new ArrayList<>();
        boolean unresolvedCycle = false;

        for (var component : condensation.topologicalOrder()) {
            cancellation.checkpoint();
            trace.addComponent(new ComponentTrace(component.componentId(), component.cyclic()
                ? ComponentTrace.Type.CYCLIC : ComponentTrace.Type.ACYCLIC, component.members()));
            if (component instanceof AcyclicComponent acyclicComponent) {
                long demand = acyclic.state().demandFor(acyclicComponent.key());
                componentResults.add(new ComponentPlanningResult(component.componentId(),
                    ComponentPlanningResult.Type.ACYCLIC,
                    demand > 0 ? ComponentPlanningResult.Status.PLANNED : ComponentPlanningResult.Status.NOT_REQUIRED,
                    demand > 0 ? Map.of(acyclicComponent.key(), demand) : Map.of(), null, null));
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
            if (requiredOutputs.isEmpty()) {
                cycleStatus = CyclePlanningStatus.NOT_REQUIRED;
            } else if (!cyclePlanningEnabled) {
                cycleStatus = CyclePlanningStatus.DISABLED;
                diagnostic = "Cycle planning is disabled";
                unresolvedCycle = true;
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_DISABLED, diagnostic));
            } else {
                Map<AEKey, Long> stock = relevantStock(cycle, inventory);
                var result = cycleSolver.solve(new CycleSolveRequest(cycle, requiredOutputs, stock,
                    cycle.outgoingDependencies(), new CycleSolveRequest.PlannerOptions(true)), cancellation);
                cycleStatus = result.status();
                diagnostic = result.diagnostic();
                unresolvedCycle = true;
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_NOT_IMPLEMENTED,
                    diagnostic == null ? cycleStatus.name() : diagnostic));
            }
            trace.addCycle(new CycleTrace(cycle.componentId(), cycle.members(), cycle.internalEdges(),
                requiredOutputs, cycleStatus));
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.CYCLE_GROUP, null, null, 0, 0, 0, 0, 0,
                cycleStatus == CyclePlanningStatus.NOT_REQUIRED ? PlanTraceNode.Selection.NOT_APPLICABLE
                    : PlanTraceNode.Selection.UNSUPPORTED,
                cycleStatus.name()));
            componentResults.add(new ComponentPlanningResult(cycle.componentId(),
                ComponentPlanningResult.Type.CYCLIC,
                requiredOutputs.isEmpty() ? ComponentPlanningResult.Status.NOT_REQUIRED
                    : ComponentPlanningResult.Status.UNRESOLVED,
                requiredOutputs, cycleStatus, diagnostic));
            cycleDiagnostics.add(diagnostic(cycle, inventory));
        }

        PlanningStatus status = acyclic.status();
        if (unresolvedCycle && (status == PlanningStatus.SUCCESS || status == PlanningStatus.MISSING_ITEMS)) {
            status = acyclic.state().hasPlannedCrafting() || status == PlanningStatus.MISSING_ITEMS
                ? PlanningStatus.PARTIAL : PlanningStatus.CYCLE_UNRESOLVED;
        }
        return new Outcome(status, acyclic.state(), trace, List.copyOf(cycleDiagnostics),
            List.copyOf(componentResults));
    }

    private static Map<AEKey, Long> relevantStock(CycleComponent cycle, KeyCounter inventory) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (AEKey member : cycle.members()) result.put(member, Math.max(0L, inventory.get(member)));
        for (var dependency : cycle.outgoingDependencies()) {
            for (var relationship : dependency.relationships()) {
                AEKey key = relationship.requiredInput();
                result.putIfAbsent(key, Math.max(0L, inventory.get(key)));
            }
        }
        return Map.copyOf(result);
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
