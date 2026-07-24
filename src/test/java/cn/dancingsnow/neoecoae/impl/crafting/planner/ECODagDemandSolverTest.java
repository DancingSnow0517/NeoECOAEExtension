package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECODagDemandSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECODagDemandSolverTest {
    @Test
    void propagatesBatchedDemandWithoutSearch() {
        var problem = new ECOPlanningProblem<>(
                List.of(
                        new ECOPlanningOperation<>("smelt", Map.of("ore", 2L), Map.of("ingot", 3L)),
                        new ECOPlanningOperation<>("assemble", Map.of("ingot", 5L), Map.of("machine", 1L))),
                Map.of("ore", 8L),
                Map.of("machine", 2L));

        var result = ECODagDemandSolver.trySolve(problem).orElseThrow();

        assertEquals(ECOHyperflowResult.Status.COMPLETE, result.status());
        assertEquals(Map.of("assemble", 2L, "smelt", 4L), result.candidate().executions());
        assertEquals(2, result.expandedStates());
    }

    @Test
    void leavesMultipleRoutesForTheIntegerSolver() {
        var problem = new ECOPlanningProblem<>(
                List.of(
                        new ECOPlanningOperation<>("route-a", Map.of("a", 1L), Map.of("part", 1L)),
                        new ECOPlanningOperation<>("route-b", Map.of("b", 1L), Map.of("part", 1L))),
                Map.of(),
                Map.of("part", 1L));

        assertTrue(ECODagDemandSolver.trySolve(problem).isEmpty());
    }
}
