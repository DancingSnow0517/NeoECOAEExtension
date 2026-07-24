package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOIntegerHyperflowSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOSolveBudget;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECOIntegerHyperflowSolverTest {
    @Test
    void solvesAWholeBatchChain() {
        var problem = new ECOPlanningProblem<>(
                List.of(
                        operation("smelt", Map.of("ore", 2L), Map.of("ingot", 1L)),
                        operation("assemble", Map.of("ingot", 3L), Map.of("gear", 1L))),
                Map.of("ore", 12L),
                Map.of("gear", 2L));

        var result = ECOIntegerHyperflowSolver.solve(problem, ECOSolveBudget.DEFAULT);

        assertEquals(ECOHyperflowResult.Status.COMPLETE, result.status());
        assertEquals(Map.of("smelt", 6L, "assemble", 2L), result.candidate().executions());
    }

    @Test
    void choosesTheRouteWithLessMissingSourceMaterial() {
        var problem = new ECOPlanningProblem<>(
                List.of(
                        operation("wasteful", Map.of("ore", 4L), Map.of("part", 1L)),
                        operation("efficient", Map.of("ore", 1L), Map.of("part", 1L))),
                Map.of(),
                Map.of("part", 1L));

        var result = ECOIntegerHyperflowSolver.solve(problem, ECOSolveBudget.DEFAULT);

        assertEquals(ECOHyperflowResult.Status.MISSING_SOURCES, result.status());
        assertEquals(Map.of("efficient", 1L), result.candidate().executions());
        assertEquals(1L, result.candidate().sourceShortfall());
    }

    private static ECOPlanningOperation<String, String> operation(
            String name, Map<String, Long> inputs, Map<String, Long> outputs) {
        return new ECOPlanningOperation<>(name, inputs, outputs);
    }
}
