package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOPlanningSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOSolveBudget;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ECOPlanningGraphCacheTest {
    @AfterEach
    void clearCaches() {
        ECOPlanningGraph.clearCaches();
        ECOPlanningSolver.clearCaches();
    }

    @Test
    void reusesReachableGraphForEquivalentRecipeSetAndTarget() {
        List<ECOPlanningOperation<String, String>> operations = List.of(
            operation("make_target", Map.of("intermediate", 1L), Map.of("target", 1L)),
            operation("make_intermediate", Map.of("raw", 1L), Map.of("intermediate", 1L)),
            operation("unrelated", Map.of("other_raw", 1L), Map.of("other", 1L))
        );

        ECOPlanningGraph<String, String> first = ECOGraphPruner.targetReachable(operations, Set.of("target"));
        ECOPlanningGraph<String, String> second = ECOGraphPruner.targetReachable(
            List.copyOf(operations), Set.of("target")
        );

        assertSame(first, second);
        assertEquals(2, first.operations().size());
    }

    @Test
    void separatesDifferentTargetsAndInvalidatesOnClear() {
        List<ECOPlanningOperation<String, String>> operations = List.of(
            operation("make_first", Map.of("raw", 1L), Map.of("first", 1L)),
            operation("make_second", Map.of("raw", 1L), Map.of("second", 1L))
        );

        ECOPlanningGraph<String, String> first = ECOGraphPruner.targetReachable(operations, Set.of("first"));
        ECOPlanningGraph<String, String> second = ECOGraphPruner.targetReachable(operations, Set.of("second"));
        ECOPlanningGraph.clearCaches();
        ECOPlanningGraph<String, String> afterClear = ECOGraphPruner.targetReachable(operations, Set.of("first"));

        assertNotSame(first, second);
        assertNotSame(first, afterClear);
    }

    @Test
    void solvedResultsSurviveInterleavedTargetRevisions() {
        ECOPlanningProblem<String, String> firstProblem = new ECOPlanningProblem<>(
            List.of(operation("make_first", Map.of("raw", 1L), Map.of("first", 1L))),
            Map.of("raw", 1L),
            Map.of("first", 1L)
        );
        ECOPlanningProblem<String, String> secondProblem = new ECOPlanningProblem<>(
            List.of(operation("make_second", Map.of("raw", 1L), Map.of("second", 1L))),
            Map.of("raw", 1L),
            Map.of("second", 1L)
        );
        ECOPlanningGraph<String, String> firstGraph = new ECOPlanningGraph<>(firstProblem.operations());
        ECOPlanningGraph<String, String> secondGraph = new ECOPlanningGraph<>(secondProblem.operations());
        ECOSolveBudget budget = new ECOSolveBudget(1_000L, 32, 2, 1_000_000_000L);

        ECOHyperflowResult<String> first = ECOPlanningSolver.solve(firstProblem, firstGraph, budget);
        ECOPlanningSolver.solve(secondProblem, secondGraph, budget);
        ECOHyperflowResult<String> repeated = ECOPlanningSolver.solve(firstProblem, firstGraph, budget);

        assertEquals(ECOHyperflowResult.Status.COMPLETE, first.status());
        assertSame(first, repeated);
    }

    private static ECOPlanningOperation<String, String> operation(
        String reference,
        Map<String, Long> inputs,
        Map<String, Long> outputs
    ) {
        return new ECOPlanningOperation<>(reference, inputs, outputs, outputs.keySet(), Set.of());
    }
}
