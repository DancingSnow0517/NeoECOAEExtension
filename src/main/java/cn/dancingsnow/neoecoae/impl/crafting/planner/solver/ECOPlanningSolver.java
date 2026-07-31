package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventoryScheduler;
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
        var forced = ECOForcedDemandSolver.trySolve(problem, graph, deadlineNanos);
        if (forced.result().isPresent()) {
            ECOHyperflowResult<R> result = forced.result().get();
            if (result.status() == ECOHyperflowResult.Status.COMPLETE) {
                ScheduleValidation validation = validateSchedule(problem, result, budget, deadlineNanos);
                if (validation == ScheduleValidation.EXECUTABLE
                    || validation == ScheduleValidation.EXECUTABLE_WITH_SYNTHETIC_SOURCES) {
                    return result;
                }
                if (validation == ScheduleValidation.BUDGET_EXHAUSTED) {
                    return withStatus(result, ECOHyperflowResult.Status.BUDGET_EXHAUSTED);
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "ECO forced dependency propagation declined: {}",
                forced.result().map(result -> "partial result " + result.status()).orElse(forced.rejection())
            );
        }
        var dag = ECODagDemandSolver.trySolve(problem, graph, deadlineNanos);
        if (dag.isPresent() && !ECOSolveBudget.shouldStop(deadlineNanos)) {
            ScheduleValidation validation = validateSchedule(problem, dag.get(), budget, deadlineNanos);
            if (validation == ScheduleValidation.EXECUTABLE
                || validation == ScheduleValidation.EXECUTABLE_WITH_SYNTHETIC_SOURCES) {
                return dag.get();
            }
            if (validation == ScheduleValidation.BUDGET_EXHAUSTED) {
                return withStatus(dag.get(), ECOHyperflowResult.Status.BUDGET_EXHAUSTED);
            }
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
                ScheduleValidation validation = validateSchedule(problem, result, budget, deadlineNanos);
                if (validation == ScheduleValidation.EXECUTABLE
                    || (!hasAlternatives
                        && validation == ScheduleValidation.EXECUTABLE_WITH_SYNTHETIC_SOURCES)) {
                    return result;
                }
                if (validation == ScheduleValidation.BUDGET_EXHAUSTED) {
                    return withStatus(result, ECOHyperflowResult.Status.BUDGET_EXHAUSTED);
                }
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

    private static <K, R> ScheduleValidation validateSchedule(
        ECOPlanningProblem<K, R> problem,
        ECOHyperflowResult<R> result,
        ECOSolveBudget budget,
        long deadlineNanos
    ) {
        if (result.status() != ECOHyperflowResult.Status.COMPLETE
            && result.status() != ECOHyperflowResult.Status.MISSING_SOURCES) {
            return ScheduleValidation.BLOCKED;
        }
        try {
            var schedule = ECOInventoryScheduler.scheduleWithSyntheticSources(
                problem,
                result.candidate(),
                deadlineNanos,
                Math.max(1_024L, Math.min(50_000L, budget.maxExpandedStates()))
            );
            if (schedule.executable()) {
                return schedule.syntheticSources().isEmpty()
                    ? ScheduleValidation.EXECUTABLE
                    : ScheduleValidation.EXECUTABLE_WITH_SYNTHETIC_SOURCES;
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                    "ECO candidate scheduling declined after {} states: exhausted={}, blockedBy={}",
                    schedule.expandedStates(),
                    schedule.budgetExhausted(),
                    schedule.blockedBy()
                );
            }
            return schedule.budgetExhausted()
                ? ScheduleValidation.BUDGET_EXHAUSTED
                : ScheduleValidation.BLOCKED;
        } catch (ArithmeticException | IllegalArgumentException failure) {
            LOGGER.debug("ECO candidate scheduling rejected an invalid count vector", failure);
            return ScheduleValidation.BLOCKED;
        }
    }

    private static <R> ECOHyperflowResult<R> withStatus(
        ECOHyperflowResult<R> result,
        ECOHyperflowResult.Status status
    ) {
        return new ECOHyperflowResult<>(status, result.candidate(), result.expandedStates());
    }

    private enum ScheduleValidation {
        EXECUTABLE,
        EXECUTABLE_WITH_SYNTHETIC_SOURCES,
        BLOCKED,
        BUDGET_EXHAUSTED
    }
}
