package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;

/** Selects the linear/component path before falling back to bounded integer search. */
public final class ECOPlanningSolver {
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
        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            return ECOIntegerHyperflowSolver.solve(problem, graph, budget, deadlineNanos);
        }
        if (!hasAlternativePositiveProducers(graph)) {
            var component = ECOComponentDemandSolver.trySolve(problem, graph, deadlineNanos);
            if (component.isPresent()
                && !ECOSolveBudget.shouldStop(deadlineNanos)
                && component.get().status() != ECOHyperflowResult.Status.NO_ROUTE) {
                return component.get();
            }
        }
        return ECOIntegerHyperflowSolver.solve(problem, graph, budget, deadlineNanos);
    }

    private static <K, R> boolean hasAlternativePositiveProducers(ECOPlanningGraph<K, R> graph) {
        for (K material : graph.materials()) {
            int producers = 0;
            for (var operation : graph.producersOf(material)) {
                if (ECOPlannerMath.positiveNet(operation, material) > 0 && ++producers > 1) {
                    return true;
                }
            }
        }
        return false;
    }
}
