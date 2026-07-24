package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventoryScheduler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECOInventorySchedulerTest {
    private static final ECOPlanningOperation<String, String> A_TO_B =
            new ECOPlanningOperation<>("a-to-b", Map.of("a", 1L), Map.of("b", 2L));
    private static final ECOPlanningOperation<String, String> B_TO_A =
            new ECOPlanningOperation<>("b-to-a", Map.of("b", 1L), Map.of("a", 2L));

    @Test
    void rejectsASeedlessProductiveCycle() {
        var problem = new ECOPlanningProblem<>(List.of(A_TO_B, B_TO_A), Map.of(), Map.of("a", 1L));
        var algebraicPlan = new ECOPlanCandidate<>(Map.of("a-to-b", 1L, "b-to-a", 1L), 0, 0, 0, 1);

        var schedule = ECOInventoryScheduler.schedule(problem, algebraicPlan);

        assertFalse(schedule.executable());
        assertTrue(schedule.blockedBy().containsKey("a"));
        assertTrue(schedule.blockedBy().containsKey("b"));
    }

    @Test
    void acceptsTheSameCycleWhenInventoryProvidesASeed() {
        var problem = new ECOPlanningProblem<>(List.of(A_TO_B, B_TO_A), Map.of("a", 1L), Map.of("a", 2L));
        var algebraicPlan = new ECOPlanCandidate<>(Map.of("a-to-b", 1L, "b-to-a", 1L), 0, 0, 0, 0);

        var schedule = ECOInventoryScheduler.schedule(problem, algebraicPlan);

        assertTrue(schedule.executable());
    }
}
