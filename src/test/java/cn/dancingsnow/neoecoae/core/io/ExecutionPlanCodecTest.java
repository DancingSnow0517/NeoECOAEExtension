package cn.dancingsnow.neoecoae.core.io;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionPlanCodecTest {
    private static final ExecutionPlanCodec.Platform<Integer, String> PLATFORM = new ExecutionPlanCodec.Platform<>() {
        @Override public void writePattern(DataWriter<String> task, Integer pattern) { task.writeInt("recipe", pattern); }
        @Override public Integer readPattern(DataReader<String> task) { return task.readInt("recipe"); }
        @Override public void writeStack(DataWriter<String> entry, String key, long amount) {
            entry.writeKey("resource", key);
            entry.writeLong("quantity", amount);
        }
        @Override public ExecutionPlanCodec.Stack<String> readStack(DataReader<String> entry) {
            String key = entry.readKey("resource");
            return key == null ? null : new ExecutionPlanCodec.Stack<>(key, entry.readLong("quantity"));
        }
    };

    @Test void completeExecutionStructureRoundTripsWithoutGameClassesOrPatternTypes() {
        var tasks = List.of(
            new ExecutionPlanCodec.Task<>(0, 41, 6L, 0, "CYCLE_ORDERED", List.of(
                new ExecutionPlanCodec.Allocation<>(0, List.of(new ExecutionPlanCodec.Run<>("iron", 2L, 6L))))),
            new ExecutionPlanCodec.Task<Integer, String>(1, 42, 9L, 1, "CYCLE_DYNAMIC", List.of()));
        var phases = List.of(
            new ExecutionPlanCodec.Phase<>(0, 3, "CYCLE", List.of(0),
                List.of(new ExecutionPlanCodec.Step(0, 2L, 1, 3L)), List.of(), Map.<Integer, Long>of(), Map.of("tool", 1L)),
            new ExecutionPlanCodec.Phase<>(1, 8, "DYNAMIC_CYCLE", List.of(1), List.of(), List.of(0), Map.of(1, 7L), Map.of("seed", 2L)));
        var plan = new ExecutionPlanCodec.Plan<>("DYNAMIC_CYCLE", tasks, phases);
        var backend = new MemoryData<String>();
        ExecutionPlanCodec.write(backend, plan, PLATFORM);
        assertEquals(plan, ExecutionPlanCodec.read(backend, PLATFORM));
        var phase = backend.readCompoundList("phases").getFirst();
        assertArrayEquals(new int[]{0}, phase.readIntArray("tasks"));
        assertTrue(phase.readCompoundList("steps").getFirst().contains("repeatWidth"));
        assertEquals(3L, phase.readCompoundList("steps").getFirst().readLong("repetitions"));
    }

    @Test void legacyStepsWithoutRepeatMetadataRetainSingleExecutionDefaults() {
        var backend = new MemoryData<String>();
        backend.writeString("mode", "ORDERED_CYCLE");
        backend.writeList("phases", List.of(phase -> {
            phase.writeInt("index", 0);
            phase.writeList("steps", List.of(step -> {
                step.writeInt("task", 0);
                step.writeLong("count", Long.MAX_VALUE);
            }));
        }));
        var step = ExecutionPlanCodec.read(backend, PLATFORM).phases().getFirst().steps().getFirst();
        assertEquals(new ExecutionPlanCodec.Step(0, Long.MAX_VALUE, 1, 1L), step);
    }

    @Test void malformedTaskPhaseAndSeedStructuresRejectBeforeReturningAPlan() {
        var tasks = new MemoryData<String>();
        tasks.writeList("tasks", List.of(task -> task.writeInt("id", 1)));
        assertThrows(IllegalArgumentException.class, () -> ExecutionPlanCodec.read(tasks, PLATFORM));
        var phases = new MemoryData<String>();
        phases.writeList("phases", List.of(phase -> phase.writeInt("index", 1)));
        assertThrows(IllegalArgumentException.class, () -> ExecutionPlanCodec.read(phases, PLATFORM));
        for (long amount : new long[]{0L, -1L}) {
            var invalidSeed = new MemoryData<String>();
            invalidSeed.writeList("phases", List.of(phase -> phase.writeList("initialSeed", List.of(seed -> {
                seed.writeKey("resource", "seed");
                seed.writeLong("quantity", amount);
            }))));
            assertThrows(IllegalArgumentException.class, () -> ExecutionPlanCodec.read(invalidSeed, PLATFORM));
        }
    }

    @Test void uncompressedStepsStillOmitOptionalMetadataFromSaves() {
        var backend = new MemoryData<String>();
        var phase = new ExecutionPlanCodec.Phase<String>(0, 0, "CYCLE", List.of(0),
            List.of(new ExecutionPlanCodec.Step(0, 1L, 1, 1L)), List.of(), Map.of(), Map.of());
        ExecutionPlanCodec.write(backend, new ExecutionPlanCodec.Plan<Integer, String>("ORDERED_CYCLE", List.of(), List.of(phase)), PLATFORM);
        var step = backend.readCompoundList("phases").getFirst().readCompoundList("steps").getFirst();
        assertFalse(step.contains("repeatWidth"));
        assertFalse(step.contains("repetitions"));
    }
}
