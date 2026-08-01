package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;

/** Selects the linear/component path before falling back to bounded integer search. */
public final class ECOPlanningSolver {
    private static final long CYCLE_PHASE_NANOS = 2_000_000_000L;
    private static final long COMPONENT_PHASE_NANOS = 500_000_000L;

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
        var dag = ECODagDemandSolver.trySolve(problem, graph);
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
            return ECOIntegerHyperflowSolver.solve(problem, graph, budget, deadlineNanos);
        }
        long cycleDeadline = ECOSolveBudget.phaseDeadline(deadlineNanos, CYCLE_PHASE_NANOS);
        var cycle = ECOCondensedCycleSolver.trySolve(problem, graph, cycleDeadline);
        if (cycle.isPresent()) {
            return cycle.get();
        }
        long componentDeadline = ECOSolveBudget.phaseDeadline(deadlineNanos, COMPONENT_PHASE_NANOS);
        var component = ECOComponentDemandSolver.trySolve(problem, graph, componentDeadline);
        if (component.isPresent()
            && !ECOSolveBudget.shouldStop(componentDeadline)
            && component.get().status() != ECOHyperflowResult.Status.NO_ROUTE) {
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
            component.isEmpty() ? "component_no_result_or_limit; switch_to_integer" : "component_no_usable_route; switch_to_integer"
        );
        return ECOIntegerHyperflowSolver.solve(problem, graph, budget, deadlineNanos);
    }
}
