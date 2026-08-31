package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plans a solved cycle's boundary inputs without ever invoking a nested cycle solve. */
final class ExternalDemandPlanner {
    record Outcome(CycleExternalDemandStatus status, KeyCounter directReservations,
            List<SolveState> states, Map<AEKey, Long> missingLeaves, Set<IPatternDetails> selectedPatterns,
            String diagnostic) {
        boolean solved() { return status == CycleExternalDemandStatus.SOLVED; }
    }

    private final AcyclicCraftingSolver acyclicSolver;
    private final ActiveRouteSelector routeSelector = new ActiveRouteSelector();
    private final CraftingGraphBuilder graphBuilder = new CraftingGraphBuilder();
    private final TarjanSccAnalyzer sccAnalyzer = new TarjanSccAnalyzer();

    ExternalDemandPlanner(AcyclicCraftingSolver acyclicSolver) { this.acyclicSolver = acyclicSolver; }

    Outcome solve(CompiledNetwork network, CycleComponent cycle, CycleSolveResult cycleResult,
            KeyCounter inventory, SolveState base, Map<AEKey, Long> additionalCycleReservations,
            ECOCancellation cancellation) throws InterruptedException {
        KeyCounter available = remainingInventory(inventory, base);
        for (var reservation : additionalCycleReservations.entrySet()) {
            long amount = reservation.getValue();
            if (amount < 0L || available.get(reservation.getKey()) < amount) {
                return failure(CycleExternalDemandStatus.MISSING, Map.of(reservation.getKey(), Math.max(0L, amount)),
                    "Cycle-owned stock is unavailable before external-demand planning");
            }
            if (amount > 0L) available.remove(reservation.getKey(), amount);
        }

        KeyCounter direct = new KeyCounter();
        List<SolveState> states = new ArrayList<>();
        Set<IPatternDetails> selected = new LinkedHashSet<>();
        for (var demand : cycleResult.positiveExternalDemand().entrySet()) {
            cancellation.checkpoint();
            long fromStock = Math.min(demand.getValue(), available.get(demand.getKey()));
            if (fromStock > 0) {
                available.remove(demand.getKey(), fromStock);
                direct.add(demand.getKey(), fromStock);
            }
            long deficit = demand.getValue() - fromStock;
            if (deficit <= 0) continue;

            Outcome one = solveDeficit(network, cycle, demand.getKey(), deficit, available, cancellation);
            if (!one.solved()) return one;
            SolveState state = one.states().getFirst();
            states.add(state);
            selected.addAll(one.selectedPatterns());
            for (var used : state.used) {
                if (!used.getValue().fitsLong()) {
                    return failure(CycleExternalDemandStatus.UNREPRESENTABLE, Map.of(),
                        "External DAG used amount exceeds AE2 long range: key=" + used.getKey()
                            + " amount=" + used.getValue() + " max=" + Long.MAX_VALUE);
                }
                if (available.get(used.getKey()) < used.getValue().longValueExact()) {
                    return failure(CycleExternalDemandStatus.MISSING,
                        Map.of(used.getKey(), used.getValue().longValueExact() - available.get(used.getKey())),
                        "External demands compete for the same remaining inventory");
                }
                available.remove(used.getKey(), used.getValue().longValueExact());
            }
        }
        return new Outcome(CycleExternalDemandStatus.SOLVED, direct, List.copyOf(states), Map.of(),
            Set.copyOf(selected), "External demand solved through inventory and acyclic routes");
    }

    private Outcome solveDeficit(CompiledNetwork network, CycleComponent cycle, AEKey goal, long amount,
            KeyCounter inventory, ECOCancellation cancellation) throws InterruptedException {
        Set<IPatternDetails> forbiddenPatterns = cycle.patterns().stream()
            .map(pattern -> pattern.details()).collect(java.util.stream.Collectors.toSet());
        Set<AEKey> forbiddenMembers = Set.copyOf(cycle.members());
        Map<AEKey, List<cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern>> producers =
            new LinkedHashMap<>();
        network.producers().forEach((key, candidates) -> producers.put(key,
            forbiddenMembers.contains(key) ? List.of() : candidates.stream()
                .filter(pattern -> !forbiddenPatterns.contains(pattern.details())).toList()));
        int patterns = producers.values().stream().mapToInt(List::size).sum();
        int edges = producers.values().stream().flatMap(List::stream).mapToInt(p -> p.inputs().size()).sum();
        CompiledNetwork filtered = new CompiledNetwork(goal, producers,
            network.emittable().stream().filter(key -> !forbiddenMembers.contains(key)).collect(java.util.stream.Collectors.toSet()),
            patterns, edges);

        var graph = graphBuilder.build(filtered, cancellation);
        var condensation = CondensationGraph.build(graph, sccAnalyzer.analyze(graph, cancellation), cancellation);
        var selection = routeSelector.select(condensation.source(), cancellation);
        if (!selection.acyclic()) {
            return failure(CycleExternalDemandStatus.UNSUPPORTED, Map.of(),
                "External demand requires another cyclic component");
        }
        List<AEKey> route = selection.condensation().topologicalOrder().stream()
            .filter(AcyclicComponent.class::isInstance).map(AcyclicComponent.class::cast)
            .map(AcyclicComponent::key).toList();
        var solved = acyclicSolver.solve(filtered, new AcyclicRoutePlan(route), inventory, amount,
            selection.choices(), cancellation);
        if (solved.status() == PlanningStatus.SUCCESS) {
            return new Outcome(CycleExternalDemandStatus.SOLVED, new KeyCounter(), List.of(solved.state()), Map.of(),
                solved.state().selected.values().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()),
                "External DAG solved");
        }
        if (solved.status() == PlanningStatus.MISSING_ITEMS) {
            Map<AEKey, Long> missing = positive(solved.state().missingItems());
            if (missing.keySet().stream().anyMatch(forbiddenMembers::contains)) {
                return failure(CycleExternalDemandStatus.FORBIDDEN_ROUTE, missing,
                    "All usable external routes re-enter the current cycle component");
            }
            return failure(CycleExternalDemandStatus.MISSING, missing, "External DAG leaf material is missing");
        }
        if (solved.status() == PlanningStatus.AMOUNT_OVERFLOW) {
            return failure(CycleExternalDemandStatus.OVERFLOW, Map.of(), "External DAG arithmetic overflow");
        }
        if (solved.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE) {
            return failure(CycleExternalDemandStatus.UNREPRESENTABLE, Map.of(),
                solved.trace().diagnostics().stream()
                    .filter(diagnostic -> diagnostic.code() == cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic.Code
                        .EXECUTION_AMOUNT_UNREPRESENTABLE)
                    .map(cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic::message)
                    .findFirst().orElse("External DAG plan exceeds AE2 long range"));
        }
        return failure(CycleExternalDemandStatus.UNSUPPORTED, Map.of(),
            "External DAG contains an unsupported pattern or route");
    }

    private static Outcome failure(CycleExternalDemandStatus status, Map<AEKey, Long> missing, String diagnostic) {
        return new Outcome(status, new KeyCounter(), List.of(), Map.copyOf(missing), Set.of(), diagnostic);
    }
    private static KeyCounter remainingInventory(KeyCounter inventory, SolveState base) {
        KeyCounter result = new KeyCounter();
        for (var entry : inventory) {
            long remaining = base.used.get(entry.getKey()).compareTo(PlannerAmount.of(entry.getLongValue())) >= 0
                ? 0L : PlannerAmount.of(entry.getLongValue()).subtract(base.used.get(entry.getKey())).longValueExact();
            if (remaining > 0) result.add(entry.getKey(), remaining);
        }
        return result;
    }
    private static Map<AEKey, Long> positive(KeyCounter counter) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var entry : counter) if (entry.getLongValue() > 0) result.put(entry.getKey(), entry.getLongValue());
        return Map.copyOf(result);
    }
}
