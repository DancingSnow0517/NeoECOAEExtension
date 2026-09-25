package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECORepeatedCircuitRuntimeTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test void partialProviderAcceptanceAndReloadKeepLapOrder() {
        var fixture = fixture(3, 0, 0);
        var runtime = fixture.runtime;
        accept(runtime, fixture, 0, 5, 1); // Prefix 5, accept only one.
        assertEquals(4, runtime.candidates().getFirst().maxDispatchCount());
        accept(runtime, fixture, 0, 4, 4);
        accept(runtime, fixture, 0, 2, 1); // Half of first circuit run.
        var state = new CompoundTag();
        var registries = mock(HolderLookup.Provider.class);
        runtime.writeToNBT(state, registries);
        runtime = ECOExecutionRuntime.fromNBT(fixture.plan, fixture.patterns, fixture.progress, state, registries);
        accept(runtime, fixture, 0, 1, 1);
        // A failed provider push makes no onAccepted call and must not advance to the next lap.
        assertEquals(1, runtime.candidates().getFirst().taskId());
        assertEquals(runtime.candidates().getFirst(), runtime.candidates().getFirst());
        accept(runtime, fixture, 1, 1, 1);
        for (int lap = 1; lap < 3; lap++) {
            accept(runtime, fixture, 0, 2, 2);
            accept(runtime, fixture, 1, 1, 1);
        }
        accept(runtime, fixture, 0, 3, 3); // Tail is allowed only after all laps.
        assertTrue(runtime.isComplete());
    }

    @Test void reconcileTrillionLapsSkipsCompletedPrefixArithmetically() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            long n = 1_000_000_000_000L;
            var fixture = fixture(n, 5 + (n - 1) * 2 + 1, n - 1);
            accept(fixture.runtime, fixture, 0, 1, 1);
            accept(fixture.runtime, fixture, 1, 1, 1);
            accept(fixture.runtime, fixture, 0, 3, 3);
            assertTrue(fixture.runtime.isComplete());
        });
    }

    @Test void persistedPlanKeepsCircuitBoundariesAndLegacyStepsRemainReadable() {
        var fixture = fixture(1_000_000_000_000L, 0, 0);
        var registries = mock(HolderLookup.Provider.class);
        var definitions = List.of(mock(AEItemKey.class), mock(AEItemKey.class));
        for (int i = 0; i < 2; i++) {
            var tag = new CompoundTag(); tag.putInt("testId", i);
            when(fixture.patterns.get(i).getDefinition()).thenReturn(definitions.get(i));
            when(definitions.get(i).toTag(registries)).thenReturn(tag);
        }
        var saved = ECOExecutionPlanNbtCodec.encode(fixture.plan, registries);
        assertEquals(4, saved.getList("phases", 10).getCompound(0).getList("steps", 10).size());
        var cpu = mock(ECOCraftingCPU.class);
        var logic = new ECOCraftingCPULogic(cpu);
        try (var keys = mockStatic(AEItemKey.class); var patterns = mockStatic(PatternDetailsHelper.class)) {
            keys.when(() -> AEItemKey.fromTag(eq(registries), any())).thenAnswer(call ->
                definitions.get(((CompoundTag) call.getArgument(1)).getInt("testId")));
            for (int i = 0; i < 2; i++) {
                var definition = definitions.get(i);
                patterns.when(() -> PatternDetailsHelper.decodePattern(eq(definition), any())).thenReturn(fixture.patterns.get(i));
            }
            var restored = ECOExecutionPlanNbtCodec.decode(saved, registries, logic,
                new GenericStack(mock(AEKey.class), 1));
            assertEquals(fixture.plan.phases().getFirst().steps(), restored.phases().getFirst().steps());
        }
    }

    @Test void overlappingOrOversizedCircuitsCannotReachTheCpu() {
        var fixture = fixture(3, 0, 0);
        var invalid = new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.CYCLE, List.of(0, 1),
            List.of(new ECOExecutionPlan.ExecutionStep(0, 2, 1, 2), new ECOExecutionPlan.ExecutionStep(1, 1, 2, 2)), List.of());
        assertThrows(IllegalArgumentException.class, () -> new ECOExecutionPlan(fixture.plan.signature(),
            ExecutionMode.ORDERED_CYCLE, fixture.plan.tasks(), List.of(invalid), fixture.plan.schedule()));
    }

    private static void accept(ECOExecutionRuntime runtime, Fixture fixture, int id, long allowance, long accepted) {
        var candidates = runtime.candidates();
        assertEquals(1, candidates.size());
        var candidate = candidates.getFirst();
        assertEquals(id, candidate.taskId());
        assertEquals(allowance, candidate.maxDispatchCount());
        fixture.progress[id].accept(accepted);
        runtime.onAccepted(candidate, accepted, new KeyCounter[0]);
    }

    private record Fixture(ECOExecutionPlan plan, Map<Integer, IPatternDetails> patterns,
            ExecutingCraftingJob.TaskProgress[] progress, ECOExecutionRuntime runtime) {}

    private static Fixture fixture(long laps, long acceptedFirst, long acceptedSecond) {
        var first = pattern();
        var second = pattern();
        var patterns = Map.of(0, first, 1, second);
        long[] totals = {Math.addExact(Math.multiplyExact(laps, 2L), 8L), laps};
        var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(id -> new ECOExecutionPlan.TaskSpec(id,
            PlanIdentity.patternIdentityFor(patterns.get(id)), patterns.get(id),
            ECOExecutionPlan.PatternRuntimeInfo.from(patterns.get(id)), totals[id], 0,
            ECOExecutionPlan.TaskKind.CYCLE_ORDERED)).toList();
        var steps = List.of(new ECOExecutionPlan.ExecutionStep(0, 5), new ECOExecutionPlan.ExecutionStep(0, 2),
            new ECOExecutionPlan.ExecutionStep(1, 1, laps == 1 ? 1 : 2, laps), new ECOExecutionPlan.ExecutionStep(0, 3));
        var phase = new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.CYCLE, List.of(0, 1), steps, List.of());
        var signature = new PlanIdentity.Signature(mock(AEKey.class), 1, Map.of(), Map.of(), Map.of(), Map.of());
        var plan = new ECOExecutionPlan(signature, ExecutionMode.ORDERED_CYCLE, tasks, List.of(phase), new ECOExecutionSchedule(List.of()));
        var progress = new ExecutingCraftingJob.TaskProgress[2];
        for (int id = 0; id < 2; id++) {
            progress[id] = new ExecutingCraftingJob.TaskProgress();
            progress[id].setExact(BigInteger.valueOf(totals[id]), BigInteger.valueOf(totals[id]));
            long accepted = id == 0 ? acceptedFirst : acceptedSecond;
            if (accepted > 0) progress[id].accept(accepted);
        }
        return new Fixture(plan, patterns, progress, new ECOExecutionRuntime(plan, patterns, progress));
    }

    private static IPatternDetails pattern() {
        var result = mock(IPatternDetails.class);
        when(result.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(result.getOutputs()).thenReturn(List.of(new GenericStack(mock(AEKey.class), 1)));
        return result;
    }
}
