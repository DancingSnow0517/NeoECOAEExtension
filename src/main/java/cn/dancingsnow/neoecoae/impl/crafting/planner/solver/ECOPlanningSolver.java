package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Selects the linear/component path before falling back to bounded integer search. */
public final class ECOPlanningSolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOPlanningSolver.class);

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
        // A forced dependency closure is linear and remains useful even when a
        // busy server has consumed the bounded-search wall-clock budget.
        var forced = ECOForcedDemandSolver.trySolve(problem, graph);
        if (forced.result().isPresent()) {
            return forced.result().get();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("ECO forced dependency propagation declined: {}", forced.rejection());
        }
        var dag = ECODagDemandSolver.trySolve(problem, graph);
        if (dag.isPresent() && !ECOSolveBudget.shouldStop(deadlineNanos)) {
            return dag.get();
        }
        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            return ECOIntegerHyperflowSolver.solve(problem, graph, budget, deadlineNanos);
        }
        boolean hasAlternatives = hasAlternativePositiveProducers(graph);
        var component = ECOComponentDemandSolver.trySolve(problem, graph, deadlineNanos);
        if (component.isPresent() && !ECOSolveBudget.shouldStop(deadlineNanos)) {
            ECOHyperflowResult<R> result = component.get();
            // Alternative input variants are common in real AE2 graphs. They do
            // not make a component plan unsafe when it closes every dependency;
            // they only mean a partial greedy result must be left to the exact
            // integer search below.
            if (result.status() == ECOHyperflowResult.Status.COMPLETE
                || (!hasAlternatives && result.status() != ECOHyperflowResult.Status.NO_ROUTE)) {
                return result;
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
