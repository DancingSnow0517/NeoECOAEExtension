package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningBalances;
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
    // A complete component solution is already executable. Give the integer optimizer only a
    // short opportunity to improve producer selection before returning that solution to AE2.
    private static final long INTEGER_OPTIMIZATION_PHASE_NANOS = 250_000_000L;
    private static final long INTEGER_OPTIMIZATION_DEBUG_PHASE_NANOS = 1_000_000_000L;

    private ECOPlanningSolver() {
    }

    public static void clearCaches() {
        ECOPlannerComputationCache.clear();
        ECOCondensedCycleSolver.clearCache();
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
        return ECOPlannerComputationCache.getOrCompute(
            problem,
            graph,
            budget,
            deadlineNanos,
            () -> solveUncached(problem, graph, budget, deadlineNanos)
        );
    }

    private static <K, R> ECOHyperflowResult<R> solveUncached(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        ECOSolveBudget budget,
        long deadlineNanos
    ) {
        if (ECOPlanningFailureDiagnostics.canLogDetail(
            ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION
        )) {
            ECOPlanningFailureDiagnostics.logDetail(
            ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION,
            "solver_start materials=" + graph.materials().size()
                + " operations=" + graph.operations().size()
                + " inventory=" + ECOPlanningFailureDiagnostics.describeMap(problem.inventory())
                + " requested=" + ECOPlanningFailureDiagnostics.describeMap(problem.requested())
                + " statefulOperations=" + graph.operations().stream()
                    .filter(operation -> !operation.stateTransitionInputs().isEmpty()).count()
            );
        }
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
            logSelected(problem, "dag", dag.get());
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
        // Reversible packing recipes frequently add a back-edge to an otherwise ordinary
        // production chain (for example ingot <-> nugget). Try the acyclic projection before
        // invoking MILP; the original graph remains authoritative for real cycles and shortages.
        ECOPlanningGraph<K, R> acyclicProjection = acyclicProjection(problem, graph);
        if (acyclicProjection.operations().size() < graph.operations().size()) {
            phaseStarted = System.nanoTime();
            var projected = ECOComponentDemandSolver.trySolve(
                problem, acyclicProjection, componentDeadline(deadlineNanos)
            );
            logPhase(problem, "component_acyclic_projection", phaseStarted,
                "result=" + (projected.isPresent() ? projected.get().status() : "empty")
                    + " originalOperations=" + graph.operations().size()
                    + " projectedOperations=" + acyclicProjection.operations().size());
            if (projected.isPresent()
                && projected.get().status() == ECOHyperflowResult.Status.COMPLETE) {
                logSelected(problem, "component_acyclic_projection", projected.get());
                return projected.get();
            }
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
            logSelected(problem, "scc_cycle", cycle.get());
            return cycle.get();
        }
        long componentDeadline = ECOSolveBudget.phaseDeadline(
            deadlineNanos,
            NEConfig.debugECOPlanner ? DEBUG_COMPONENT_PHASE_NANOS : COMPONENT_PHASE_NANOS
        );
        phaseStarted = System.nanoTime();
        Optional<ECOHyperflowResult<R>> component;
        try {
            component = ECOComponentDemandSolver.trySolve(problem, graph, componentDeadline);
        } catch (StackOverflowError overflow) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                ECOPlannerFallbackReason.PLANNING_FAILURE,
                problem.requested().keySet().stream().findFirst().orElse(null),
                problem.requested().values().stream().findFirst().orElse(0L),
                "component",
                "stack_overflow_fallback_to_integer graphMaterials=" + graph.materials().size()
                    + " graphOperations=" + graph.operations().size(),
                overflow
            );
            component = Optional.empty();
        }
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
            && (problem.inventory().isEmpty()
                || inventoryRoute == null
                || !inventoryRoute.reachesTarget()
                || optimisticCapacityProvesShortage(problem, graph));
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
                            ? inventoryRoute == null || !inventoryRoute.reachesTarget()
                                ? "requested_output_not_inventory_reachable"
                                : "optimistic_capacity_below_request"
                            : "residual_inventory_reachable_alternative")
                    + " originalOperations=" + graph.operations().size()
                    + " retainedOperations=" + integerGraph.operations().size()
            );
        }
        if (component.isPresent()
            && (component.get().status() == ECOHyperflowResult.Status.COMPLETE
                && !requiresIntegerOptimization(graph)
                || componentMissingIsConclusive)) {
            logSelected(problem, "component", component.get());
            return component.get();
        }
        boolean completeComponent = component.isPresent()
            && component.get().status() == ECOHyperflowResult.Status.COMPLETE;
        if (!completeComponent) {
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
        }
        long integerDeadline = completeComponent
            ? ECOSolveBudget.phaseDeadline(
                deadlineNanos,
                NEConfig.debugECOPlanner
                    ? INTEGER_OPTIMIZATION_DEBUG_PHASE_NANOS
                    : INTEGER_OPTIMIZATION_PHASE_NANOS
            )
            : deadlineNanos;
        phaseStarted = System.nanoTime();
        ECOHyperflowResult<R> integer = ECOIntegerHyperflowSolver.solve(
            problem, integerGraph, budget, integerDeadline
        );
        logPhase(problem, "integer", phaseStarted,
            "result=" + integer.status() + " expandedStates=" + integer.expandedStates());
        if (component.isPresent()
            && component.get().status() == ECOHyperflowResult.Status.COMPLETE
            && (integer.status() != ECOHyperflowResult.Status.COMPLETE
                || compareCandidates(component.get().candidate(), integer.candidate()) <= 0)) {
            logSelected(problem, "component_after_integer_optimization", component.get());
            return component.get();
        }
        logSelected(problem, "integer", integer);
        return integer;
    }

    private static <R> int compareCandidates(
        ECOPlanCandidate<R> left,
        ECOPlanCandidate<R> right
    ) {
        int result = Long.compare(left.requestedShortfall(), right.requestedShortfall());
        if (result == 0) result = Long.compare(left.dependencyShortfall(), right.dependencyShortfall());
        if (result == 0) result = Long.compare(left.sourceShortfall(), right.sourceShortfall());
        if (result == 0) result = Long.compare(left.totalExecutions(), right.totalExecutions());
        if (result == 0) result = Long.compare(left.surplus(), right.surplus());
        if (result == 0) result = Integer.compare(left.executions().size(), right.executions().size());
        return result;
    }

    private static <K, R> boolean requiresIntegerOptimization(ECOPlanningGraph<K, R> graph) {
        return graph.materials().stream().anyMatch(material ->
            graph.producersOf(material).stream()
                .filter(operation -> ECOPlannerMath.positiveNet(operation, material) > 0L)
                .limit(2)
                .count() > 1L
        );
    }

    private static long componentDeadline(long deadlineNanos) {
        return ECOSolveBudget.phaseDeadline(
            deadlineNanos,
            NEConfig.debugECOPlanner ? DEBUG_COMPONENT_PHASE_NANOS : COMPONENT_PHASE_NANOS
        );
    }

    private static <K, R> ECOPlanningGraph<K, R> acyclicProjection(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph
    ) {
        List<ECOPlanningOperation<K, R>> selected = new ArrayList<>();
        Set<K> expanded = new HashSet<>();
        Set<K> active = new HashSet<>();
        for (K requested : problem.requested().keySet()) {
            collectAcyclic(requested, graph, active, expanded, selected);
        }
        if (selected.size() == graph.operations().size()) {
            return graph;
        }
        return new ECOPlanningGraph<>(selected);
    }

    private static <K, R> void collectAcyclic(
        K material,
        ECOPlanningGraph<K, R> graph,
        Set<K> active,
        Set<K> expanded,
        List<ECOPlanningOperation<K, R>> selected
    ) {
        if (!active.add(material)) {
            return;
        }
        if (!expanded.add(material)) {
            active.remove(material);
            return;
        }
        for (var operation : graph.producersOf(material)) {
            boolean backEdge = operation.inputs().keySet().stream().anyMatch(active::contains)
                || operation.selectableOutputs().stream().anyMatch(
                    output -> !output.equals(material) && active.contains(output)
                );
            if (backEdge) {
                continue;
            }
            operation.inputs().keySet().forEach(
                input -> collectAcyclic(input, graph, active, expanded, selected)
            );
            if (!selected.contains(operation)) {
                selected.add(operation);
            }
        }
        active.remove(material);
    }

    private static <K, R> void logSelected(
        ECOPlanningProblem<K, R> problem,
        String solver,
        ECOHyperflowResult<R> result
    ) {
        if (ECOPlanningFailureDiagnostics.canLogDetail(
            ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION
        )) {
            ECOPlanningFailureDiagnostics.logDetail(
            ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION,
            "solver_selected=" + solver
                + " status=" + result.status()
                + " expandedStates=" + result.expandedStates()
                + " executions=" + ECOPlanningFailureDiagnostics.describeMap(
                    result.candidate().executions()
                )
                + " requestedShortfall=" + result.candidate().requestedShortfall()
                + " dependencyShortfall=" + result.candidate().dependencyShortfall()
                + " sourceShortfall=" + result.candidate().sourceShortfall()
            );
        }
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
            operation.inputs().forEach((key, amount) -> ECOPlanningBalances.mergeScaled(
                problem, balances, key, amount, -batches
            ));
            operation.outputs().forEach((key, amount) -> ECOPlanningBalances.mergeScaled(
                problem, balances, key, amount, batches
            ));
        });
        Map<K, Long> residualInventory = new HashMap<>();
        balances.forEach((key, amount) -> {
            if (amount > 0L && !problem.requested().containsKey(key)) {
                residualInventory.put(key, amount);
            }
        });
        // Requested inventory is not output credit, but it is still a valid bootstrap input for
        // a self-growing recipe. Keep it in the residual problem so the integer fallback and its
        // scheduler can prove that the cycle starts.
        problem.requested().keySet().forEach(key -> {
            long seed = problem.inventory().getOrDefault(key, 0L);
            if (seed > 0L) {
                residualInventory.put(key, seed);
            }
        });
        Set<K> residualUnlimited = new HashSet<>(problem.unlimitedInventory());
        residualUnlimited.retainAll(residualInventory.keySet());
        return new ECOPlanningProblem<>(
            graph.operations(), residualInventory, problem.requested(), residualUnlimited
        );
    }

    /**
     * Proves a shortage using a deliberately optimistic capacity bound. Shared inputs may be
     * counted once per producer and cycles are treated as unbounded, so this can only overestimate
     * what is craftable. A bound below the request is therefore a safe reason to skip integer DFS.
     */
    private static <K, R> boolean optimisticCapacityProvesShortage(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph
    ) {
        Map<K, Long> memo = new HashMap<>();
        Set<K> visiting = new HashSet<>();
        for (var requested : problem.requested().entrySet()) {
            long capacity = optimisticMaterialCapacity(
                requested.getKey(), problem, graph, memo, visiting
            );
            if (capacity < requested.getValue()) {
                return true;
            }
        }
        return false;
    }

    private static <K, R> long optimisticMaterialCapacity(
        K material,
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        Map<K, Long> memo,
        Set<K> visiting
    ) {
        Long cached = memo.get(material);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(material)) {
            return Long.MAX_VALUE;
        }
        long capacity = problem.isUnlimited(material)
            ? Long.MAX_VALUE
            : problem.requested().containsKey(material)
            ? 0L
            : Math.max(0L, problem.inventory().getOrDefault(material, 0L));
        for (var producer : graph.producersOf(material)) {
            long netOutput = ECOPlannerMath.positiveNet(producer, material);
            if (netOutput <= 0L) {
                continue;
            }
            long batches = Long.MAX_VALUE;
            boolean constrained = false;
            for (var input : producer.inputs().entrySet()) {
                long returned = producer.outputs().getOrDefault(input.getKey(), 0L);
                if (!producer.stateTransitionInputs().contains(input.getKey())
                    && returned >= input.getValue()) {
                    continue;
                }
                constrained = true;
                long inputCapacity = optimisticMaterialCapacity(
                    input.getKey(), problem, graph, memo, visiting
                );
                batches = Math.min(batches, inputCapacity / input.getValue());
            }
            long produced = constrained
                ? ECOPlannerMath.saturatedMultiply(batches, netOutput)
                : Long.MAX_VALUE;
            capacity = ECOPlannerMath.saturatedAdd(capacity, produced);
        }
        visiting.remove(material);
        memo.put(material, capacity);
        return capacity;
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
            if (amount > 0L) {
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
