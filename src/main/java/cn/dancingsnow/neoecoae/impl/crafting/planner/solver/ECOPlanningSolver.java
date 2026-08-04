package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Selects the linear/component path before falling back to bounded integer search. */
public final class ECOPlanningSolver {
    private static final long CYCLE_PHASE_NANOS = 2_000_000_000L;
    private static final long COMPONENT_PHASE_NANOS = 500_000_000L;
    private static final long DEBUG_CYCLE_PHASE_NANOS = 10_000_000_000L;
    private static final long DEBUG_COMPONENT_PHASE_NANOS = 5_000_000_000L;

    private ECOPlanningSolver() {
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOSolveBudget budget
    ) {
        long deadlineNanos = budget.deadlineNanos();
        ECOPlanningGraph<K, R> graph = ECOGraphPruner.targetReachable(problem);
        return solve(problem, graph, budget, deadlineNanos);
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOSolveBudget budget,
        long deadlineNanos
    ) {
        ECOPlanningGraph<K, R> graph = ECOGraphPruner.targetReachable(problem);
        return solve(problem, graph, budget, deadlineNanos);
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        ECOSolveBudget budget
    ) {
        return solve(problem, graph, budget, budget.deadlineNanos());
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        ECOSolveBudget budget,
        long deadlineNanos
    ) {
        // A DAG can be balanced algebraically while still over-consuming a finite state prefix.
        // Route stateful variants through the capacity-aware component solver instead.
        long phaseStarted = System.nanoTime();
        var dag = graph.operations().stream().anyMatch(
            operation -> !operation.stateTransitionInputs().isEmpty()
        )
            ? Optional.<ECOHyperflowResult<R>>empty()
            : ECODagDemandSolver.trySolve(problem, graph);
        logPhase(problem, "dag", phaseStarted, "result=" + (dag.isPresent() ? dag.get().status() : "empty"));
        if (dag.isPresent() && !ECOSolveBudget.shouldStop(deadlineNanos)) {
            return dag.get();
        }
        ECOPlanningFailureDiagnostics.logFailure(
            ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION,
            ECOPlannerFallbackReason.SOLVER_NO_ROUTE,
            problem.requested().keySet().stream().findFirst().orElse(null),
            problem.requested().values().stream().findFirst().orElse(0L),
            "solver",
            dag.isEmpty() ? "dag_no_result_or_cycle; switch_to_component" : "dag_result_not_selected_due_to_deadline"
        );
        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION,
                ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED,
                problem.requested().keySet().stream().findFirst().orElse(null),
                problem.requested().values().stream().findFirst().orElse(0L),
                "solver",
                "deadline_before_component; switch_to_integer"
            );
            phaseStarted = System.nanoTime();
            ECOHyperflowResult<R> integer = ECOIntegerHyperflowSolver.solve(
                problem, graph, budget, deadlineNanos
            );
            logPhase(problem, "integer_after_expired_deadline", phaseStarted,
                "result=" + integer.status() + " expandedStates=" + integer.expandedStates());
            return integer;
        }
        long cycleDeadline = ECOSolveBudget.phaseDeadline(
            deadlineNanos, NEConfig.debugECOPlanner ? DEBUG_CYCLE_PHASE_NANOS : CYCLE_PHASE_NANOS
        );
        Optional<ECOHyperflowResult<R>> cycle;
        phaseStarted = System.nanoTime();
        try {
            cycle = ECOCondensedCycleSolver.trySolve(problem, graph, cycleDeadline);
        } catch (StackOverflowError overflow) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                ECOPlannerFallbackReason.PLANNING_FAILURE,
                problem.requested().keySet().stream().findFirst().orElse(null),
                problem.requested().values().stream().findFirst().orElse(0L),
                "scc_ojalgo",
                "stack_overflow_switch_to_component",
                overflow
            );
            cycle = Optional.empty();
        }
        logPhase(problem, "scc_cycle", phaseStarted,
            "result=" + (cycle.isPresent() ? cycle.get().status() : "empty"));
        if (cycle.isPresent()) {
            return cycle.get();
        }
        long componentDeadline = ECOSolveBudget.phaseDeadline(
            deadlineNanos,
            NEConfig.debugECOPlanner ? DEBUG_COMPONENT_PHASE_NANOS : COMPONENT_PHASE_NANOS
        );
        phaseStarted = System.nanoTime();
        var component = ECOComponentDemandSolver.trySolve(problem, graph, componentDeadline);
        logPhase(problem, "component", phaseStarted,
            "result=" + (component.isPresent() ? component.get().status() : "empty"));
        long integerGateStarted = System.nanoTime();
        ECOPlanningGraph<K, R> integerGraph = graph;
        InventoryRoute<K, R> inventoryRoute = null;
        if (component.isPresent()
            && component.get().status() == ECOHyperflowResult.Status.MISSING_SOURCES
            && !problem.inventory().isEmpty()) {
            ECOPlanningProblem<K, R> residualProblem = residualProblem(
                problem, graph, component.get()
            );
            inventoryRoute = inventoryExecutableTargetRoute(residualProblem, graph);
            integerGraph = inventoryRoute.graph();
        }
        boolean componentMissingIsConclusive = component.isPresent()
            && component.get().status() == ECOHyperflowResult.Status.MISSING_SOURCES
            && (problem.inventory().isEmpty() || inventoryRoute == null || !inventoryRoute.reachesTarget());
        if (component.isPresent()
            && component.get().status() == ECOHyperflowResult.Status.MISSING_SOURCES) {
            logPhase(
                problem,
                "integer_gate",
                integerGateStarted,
                "searchRequired=" + !componentMissingIsConclusive
                    + " reason=" + (problem.inventory().isEmpty()
                        ? "empty_inventory"
                        : componentMissingIsConclusive
                            ? "requested_output_not_inventory_reachable"
                            : "residual_inventory_reachable_alternative")
                    + " originalOperations=" + graph.operations().size()
                    + " retainedOperations=" + integerGraph.operations().size()
            );
        }
        if (component.isPresent()
            && !ECOSolveBudget.shouldStop(componentDeadline)
            && (component.get().status() == ECOHyperflowResult.Status.COMPLETE
                || componentMissingIsConclusive)) {
            return component.get();
        }
        ECOPlanningFailureDiagnostics.logFailure(
            ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION,
            component.isEmpty()
                ? ECOPlannerFallbackReason.SOLVER_NO_ROUTE
                : ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED,
            problem.requested().keySet().stream().findFirst().orElse(null),
            problem.requested().values().stream().findFirst().orElse(0L),
            "solver",
            component.isEmpty()
                ? "component_no_result_or_limit; switch_to_integer"
                : "component_status=" + component.get().status() + "; switch_to_integer"
        );
        phaseStarted = System.nanoTime();
        ECOHyperflowResult<R> integer = ECOIntegerHyperflowSolver.solve(
            problem, integerGraph, budget, deadlineNanos
        );
        logPhase(problem, "integer", phaseStarted,
            "result=" + integer.status() + " expandedStates=" + integer.expandedStates());
        return integer;
    }

    private static <K, R> ECOPlanningProblem<K, R> residualProblem(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        ECOHyperflowResult<R> component
    ) {
        Map<K, Long> balances = ECOPlannerMath.initialBalances(problem);
        Map<R, ECOPlanningOperation<K, R>> byReference = new HashMap<>();
        for (var operation : graph.operations()) {
            byReference.putIfAbsent(operation.reference(), operation);
        }
        component.candidate().executions().forEach((reference, batches) -> {
            ECOPlanningOperation<K, R> operation = byReference.get(reference);
            if (operation == null || batches <= 0L) {
                return;
            }
            operation.inputs().forEach((key, amount) -> balances.merge(
                key, ECOPlannerMath.saturatedMultiply(amount, -batches), ECOPlannerMath::saturatedAdd
            ));
            operation.outputs().forEach((key, amount) -> balances.merge(
                key, ECOPlannerMath.saturatedMultiply(amount, batches), ECOPlannerMath::saturatedAdd
            ));
        });
        Map<K, Long> residualInventory = new HashMap<>();
        balances.forEach((key, amount) -> {
            if (amount > 0L && !problem.requested().containsKey(key)) {
                residualInventory.put(key, amount);
            }
        });
        return new ECOPlanningProblem<>(graph.operations(), residualInventory, problem.requested());
    }

    /**
     * Checks whether current inventory can structurally start a complete route to every request.
     * Quantities are intentionally ignored: a false result proves integer producer mixing cannot
     * turn a source-shortage result into a complete plan, while a true result keeps the fallback.
     */
    private static <K, R> InventoryRoute<K, R> inventoryExecutableTargetRoute(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph
    ) {
        Set<K> reachable = new HashSet<>();
        ArrayDeque<K> newlyReachable = new ArrayDeque<>();
        problem.inventory().forEach((key, amount) -> {
            if (amount > 0L && !problem.requested().containsKey(key)) {
                reachable.add(key);
                newlyReachable.addLast(key);
            }
        });

        List<ECOPlanningOperation<K, R>> operations = graph.operations();
        int[] missingInputs = new int[operations.size()];
        boolean[] fired = new boolean[operations.size()];
        Map<K, List<Integer>> waitingByInput = new HashMap<>();
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int index = 0; index < operations.size(); index++) {
            for (K input : operations.get(index).inputs().keySet()) {
                if (!reachable.contains(input)) {
                    missingInputs[index]++;
                    waitingByInput.computeIfAbsent(input, ignored -> new ArrayList<>()).add(index);
                }
            }
            if (missingInputs[index] == 0) {
                ready.addLast(index);
            }
        }

        while (!ready.isEmpty() || !newlyReachable.isEmpty()) {
            while (!newlyReachable.isEmpty()) {
                List<Integer> waiting = waitingByInput.remove(newlyReachable.removeFirst());
                if (waiting == null) {
                    continue;
                }
                for (int index : waiting) {
                    if (--missingInputs[index] == 0) {
                        ready.addLast(index);
                    }
                }
            }
            while (!ready.isEmpty()) {
                int index = ready.removeFirst();
                if (fired[index]) {
                    continue;
                }
                fired[index] = true;
                for (K output : operations.get(index).outputs().keySet()) {
                    if (reachable.add(output)) {
                        newlyReachable.addLast(output);
                    }
                }
            }
        }

        List<ECOPlanningOperation<K, R>> executable = new ArrayList<>();
        for (int index = 0; index < operations.size(); index++) {
            if (fired[index]) {
                executable.add(operations.get(index));
            }
        }
        ECOPlanningGraph<K, R> executableGraph = new ECOPlanningGraph<>(executable);
        ECOPlanningGraph<K, R> targetRoute = ECOGraphPruner.targetReachable(
            executableGraph, problem.requested().keySet());
        boolean reachesTarget = reachable.containsAll(problem.requested().keySet())
            && problem.requested().keySet().stream().allMatch(key ->
                !targetRoute.producersOf(key).isEmpty());
        return new InventoryRoute<>(targetRoute, reachesTarget);
    }

    private record InventoryRoute<K, R>(ECOPlanningGraph<K, R> graph, boolean reachesTarget) {
    }

    private static <K, R> void logPhase(
        ECOPlanningProblem<K, R> problem,
        String phase,
        long startedNanos,
        String context
    ) {
        ECOPlanningFailureDiagnostics.logTiming(
            ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION,
            problem.requested().keySet().stream().findFirst().orElse(null),
            problem.requested().values().stream().findFirst().orElse(0L),
            "solver",
            phase,
            startedNanos,
            context
        );
    }
}
