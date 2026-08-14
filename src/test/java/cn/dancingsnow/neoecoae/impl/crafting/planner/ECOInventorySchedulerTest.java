package cn.dancingsnow.neoecoae.impl.crafting.planner;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventoryScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECORepeatedBlock;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduleEntry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ECOInventorySchedulerTest {
    @Test
    void requeuesConsumerWhenProducerAppearsLater() {
        ECOPlanningOperation<String, String> consumer = new ECOPlanningOperation<>(
            "consumer",
            Map.of("intermediate", 1L),
            Map.of("result", 1L)
        );
        ECOPlanningOperation<String, String> producer = new ECOPlanningOperation<>(
            "producer",
            Map.of("ore", 1L),
            Map.of("intermediate", 1L)
        );
        ECOPlanningProblem<String, String> problem = new ECOPlanningProblem<>(
            List.of(consumer, producer),
            Map.of("ore", 1L),
            Map.of("result", 1L)
        );
        ECOPlanCandidate<String> candidate = new ECOPlanCandidate<>(
            Map.of("consumer", 1L, "producer", 1L), 0L, 0L, 0L, 0L
        );

        var schedule = ECOInventoryScheduler.schedule(problem, candidate);

        assertTrue(schedule.executable());
        assertEquals(List.of("producer", "consumer"), schedule.steps().stream()
            .map(step -> ((ECOScheduledStep<String>) step).operation())
            .toList());
    }

    @Test
    void preservesCycleSeedsInsteadOfExhaustingTheFirstExecutableOperation() {
        ECOPlanningOperation<String, String> makeB = new ECOPlanningOperation<>(
            "make_b",
            Map.of("a", 1L),
            Map.of("b", 1L)
        );
        ECOPlanningOperation<String, String> restoreA = new ECOPlanningOperation<>(
            "restore_a",
            Map.of("a", 1L, "b", 1L),
            Map.of("a", 2L)
        );
        ECOPlanningProblem<String, String> problem = new ECOPlanningProblem<>(
            List.of(makeB, restoreA),
            Map.of("a", 10L),
            Map.of("b", 1L)
        );
        ECOPlanCandidate<String> candidate = new ECOPlanCandidate<>(
            Map.of("make_b", 10L, "restore_a", 9L), 0L, 0L, 0L, 0L
        );

        var schedule = ECOInventoryScheduler.schedule(problem, candidate);

        assertTrue(schedule.executable());
        assertTrue(schedule.blockedBy().isEmpty());
        assertEquals(10L, scheduledBatches(schedule, "make_b"));
        assertEquals(9L, scheduledBatches(schedule, "restore_a"));
    }

    @Test
    void schedulesLargeFiveOperationCycleFromDiagnosticRegression() {
        var energized = new ECOPlanningOperation<>("energized",
            Map.of("charged", 32L, "energized_dust", 32L, "water", 500L),
            Map.of("energized", 64L));
        var charge = new ECOPlanningOperation<>("charge",
            Map.of("certus", 64L, "water", 1_000L), Map.of("charged", 64L));
        var energizedDust = new ECOPlanningOperation<>("energized_dust",
            Map.of("energized", 1L), Map.of("energized_dust", 1L));
        var certusDust = new ECOPlanningOperation<>("certus_dust",
            Map.of("certus", 1L), Map.of("certus_dust", 1L));
        var certus = new ECOPlanningOperation<>("certus",
            Map.of("charged", 16L, "certus_dust", 16L, "water", 500L),
            Map.of("certus", 64L));
        ECOPlanningProblem<String, String> problem = new ECOPlanningProblem<>(
            List.of(energized, charge, energizedDust, certusDust, certus),
            Map.of(
                "water", 4_981_420_921_127L,
                "energized", 2_865_564_863L,
                "charged", 83_776_085L,
                "energized_dust", 40_152_320L,
                "certus_dust", 63_984_467L,
                "certus", 11L
            ),
            Map.of("energized", 500_000_000L)
        );
        ECOPlanCandidate<String> candidate = new ECOPlanCandidate<>(Map.of(
            "energized", 14_370_240L,
            "charge", 8_314_300L,
            "energized_dust", 419_695_360L,
            "certus_dust", 92_059_117L,
            "certus", 9_752_724L
        ), 0L, 0L, 0L, 0L);

        var schedule = ECOInventoryScheduler.schedule(problem, candidate);

        assertTrue(schedule.executable(), () -> "blocked by " + schedule.blockedBy()
            + " steps=" + schedule.steps()
            + " inventory=" + schedule.remainingInventory());
        assertTrue(schedule.steps().size() < 100);
        candidate.executions().forEach((operation, batches) ->
            assertEquals(batches, scheduledBatches(schedule, operation))
        );
    }

    @Test
    void stillRejectsAGenuineMissingCycleSeed() {
        ECOPlanningOperation<String, String> makeB = new ECOPlanningOperation<>(
            "make_b", Map.of("a", 1L), Map.of("b", 1L)
        );
        ECOPlanningOperation<String, String> makeA = new ECOPlanningOperation<>(
            "make_a", Map.of("b", 1L), Map.of("a", 1L)
        );
        var problem = new ECOPlanningProblem<>(
            List.of(makeB, makeA), Map.of(), Map.of("a", 1L)
        );
        var candidate = new ECOPlanCandidate<>(
            Map.of("make_b", 1L, "make_a", 1L), 0L, 0L, 0L, 0L
        );

        var schedule = ECOInventoryScheduler.schedule(problem, candidate);

        assertFalse(schedule.executable());
    }

    private static long scheduledBatches(
        cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventorySchedule<String, String> schedule,
        String operation
    ) {
        return scheduledBatches(schedule.steps(), operation);
    }

    private static long scheduledBatches(List<? extends ECOScheduleEntry<String>> entries, String operation) {
        long result = 0L;
        for (var entry : entries) {
            if (entry instanceof ECORepeatedBlock<String> block) {
                result = Math.addExact(result, Math.multiplyExact(
                    scheduledBatches(block.body(), operation), block.repetitions()
                ));
            } else {
                var step = (ECOScheduledStep<String>) entry;
                if (step.operation().equals(operation)) {
                    result = Math.addExact(result, step.batches());
                }
            }
        }
        return result;
    }
}
