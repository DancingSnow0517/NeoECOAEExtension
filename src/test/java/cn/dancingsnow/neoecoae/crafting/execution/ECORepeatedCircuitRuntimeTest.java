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
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECORepeatedCircuitRuntimeTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {64, 128, 192})
    void wideLapsKeepBoundedDispatchAndRestoreTheExactLastLap(int bits) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var laps = PlannerAmount.ONE.shiftLeft(bits);
            var fixture = fixture(laps, PlannerAmount.ZERO, PlannerAmount.ZERO, true);
            accept(fixture.runtime, fixture, 0, 5, 5);
            accept(fixture.runtime, fixture, 0, Long.MAX_VALUE, Long.MAX_VALUE);
            var saved = new CompoundTag();
            fixture.runtime.writeToNBT(saved);
            var restored = ECOExecutionRuntime.fromNBT(fixture.plan, fixture.patterns, fixture.progress, saved);
            assertEquals(Long.MAX_VALUE, restored.candidates().get(0).maxDispatchCount());
            assertEquals(laps.multiply(2L).add(3L).subtract(Long.MAX_VALUE), fixture.progress[0].remainingQuantity());
            // The live accepted counters are authoritative on reload, including a prefix beyond 128 bits.
            fixture.progress[0].setExact(laps.multiply(2L).add(8L), PlannerAmount.of(4L));
            fixture.progress[1].setExact(laps, PlannerAmount.ONE);
            restored = ECOExecutionRuntime.fromNBT(fixture.plan, fixture.patterns, fixture.progress, saved);
            accept(restored, fixture, 0, 1, 1);
            accept(restored, fixture, 1, 1, 1);
            accept(restored, fixture, 0, 3, 3);
            assertTrue(restored.isComplete());
        });
    }

    @Test void dynamicCycleRestoresExactFiringsAfterLongSizedBatches() {
        var count = PlannerAmount.ONE.shiftLeft(160);
        var pattern = pattern();
        var task = new ECOExecutionPlan.TaskSpec(0, PlanIdentity.patternIdentityFor(pattern), pattern,
            ECOExecutionPlan.PatternRuntimeInfo.from(pattern), count, 0, ECOExecutionPlan.TaskKind.CYCLE_DYNAMIC, List.of());
        var phase = new ECOExecutionPlan.PhaseSpec(Map.of(0, count), Map.of(), 0, 0,
            ECOExecutionSchedule.Type.DYNAMIC_CYCLE, List.of(0), List.of(), List.of());
        var plan = new ECOExecutionPlan(new PlanIdentity.Signature(mock(AEKey.class), 1, Map.of(), Map.of(), Map.of(), Map.of()),
            ExecutionMode.DYNAMIC_CYCLE, List.of(task), List.of(phase), new ECOExecutionSchedule(List.of()));
        var progress = new ExecutingCraftingJob.TaskProgress();
        progress.setExact(count, count);
        var bindings = new ExecutingCraftingJob.TaskProgress[]{progress};
        var runtime = new ECOExecutionRuntime(plan, Map.of(0, pattern), bindings);
        var candidate = runtime.candidates().get(0);
        progress.accept(Long.MAX_VALUE);
        runtime.onAccepted(candidate, Long.MAX_VALUE, new KeyCounter[0]);
        var saved = new CompoundTag(); runtime.writeToNBT(saved);
        assertEquals(count.subtract(Long.MAX_VALUE).toString(), saved.getList("phases", 10).getCompound(0)
            .getList("dynamic", 10).getCompound(0).getString("count"));
        progress.setExact(count, PlannerAmount.ONE);
        runtime = ECOExecutionRuntime.fromNBT(plan, Map.of(0, pattern), bindings, saved);
        candidate = runtime.candidates().get(0);
        assertEquals(1L, candidate.maxDispatchCount());
        progress.accept(1L); runtime.onAccepted(candidate, 1L, new KeyCounter[0]);
        assertTrue(runtime.isComplete());
    }

    @Test void partialProviderAcceptanceAndReloadKeepLapOrder() {
        var fixture = fixture(3, 0, 0);
        var runtime = fixture.runtime;
        accept(runtime, fixture, 0, 5, 1); // Prefix 5, accept only one.
        assertEquals(4, cn.dancingsnow.neoecoae.platform.JdkCompat.first(runtime.candidates()).maxDispatchCount());
        accept(runtime, fixture, 0, 4, 4);
        accept(runtime, fixture, 0, 2, 1); // Half of first circuit run.
        var state = new CompoundTag();
        var registries = mock(HolderLookup.Provider.class);
        runtime.writeToNBT(state);
        runtime = ECOExecutionRuntime.fromNBT(fixture.plan, fixture.patterns, fixture.progress, state);
        accept(runtime, fixture, 0, 1, 1);
        // A failed provider push makes no onAccepted call and must not advance to the next lap.
        assertEquals(1, cn.dancingsnow.neoecoae.platform.JdkCompat.first(runtime.candidates()).taskId());
        assertEquals(cn.dancingsnow.neoecoae.platform.JdkCompat.first(runtime.candidates()), cn.dancingsnow.neoecoae.platform.JdkCompat.first(runtime.candidates()));
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

    @Test void batchCreditsKeepPartialLapAndDoNotConsumeThePrefixOrTail() {
        var fixture = fixture(3, 0, 0, true);
        var runtime = fixture.runtime;
        accept(runtime, fixture, 0, 5, 5);
        accept(runtime, fixture, 0, 6, 3);
        accept(runtime, fixture, 1, 3, 1);
        var saved = new CompoundTag();
        var registries = mock(HolderLookup.Provider.class);
        runtime.writeToNBT(saved);
        runtime = ECOExecutionRuntime.fromNBT(fixture.plan, fixture.patterns, fixture.progress, saved);
        accept(runtime, fixture, 0, 3, 3);
        accept(runtime, fixture, 1, 2, 2);
        accept(runtime, fixture, 0, 3, 3);
        assertTrue(runtime.isComplete());
    }

    @Test void persistedPlanKeepsCircuitBoundariesAndLegacyStepsRemainReadable() {
        var fixture = fixture(PlannerAmount.ONE.shiftLeft(140), PlannerAmount.ZERO, PlannerAmount.ZERO, false);
        var registries = mock(HolderLookup.Provider.class);
        var definitions = List.of(mock(AEItemKey.class), mock(AEItemKey.class));
        for (int i = 0; i < 2; i++) {
            var tag = new CompoundTag(); tag.putInt("testId", i);
            when(fixture.patterns.get(i).getDefinition()).thenReturn(definitions.get(i));
            when(definitions.get(i).toTag()).thenReturn(tag);
        }
        var saved = ECOExecutionPlanNbtCodec.encode(fixture.plan);
        assertEquals(4, saved.getList("phases", 10).getCompound(0).getList("steps", 10).size());
        var cpu = mock(ECOCraftingCPU.class);
        var logic = new ECOCraftingCPULogic(cpu);
        try (var keys = mockStatic(AEItemKey.class); var patterns = mockStatic(PatternDetailsHelper.class)) {
            keys.when(() -> AEItemKey.fromTag(any())).thenAnswer(call ->
                definitions.get(((CompoundTag) call.getArgument(0)).getInt("testId")));
            for (int i = 0; i < 2; i++) {
                var definition = definitions.get(i);
                patterns.when(() -> PatternDetailsHelper.decodePattern(eq(definition), any())).thenReturn(fixture.patterns.get(i));
            }
            var restored = ECOExecutionPlanNbtCodec.decode(saved, logic,
                new GenericStack(mock(AEKey.class), 1));
            assertEquals(cn.dancingsnow.neoecoae.platform.JdkCompat.first(fixture.plan.phases()).steps(), cn.dancingsnow.neoecoae.platform.JdkCompat.first(restored.phases()).steps());
            assertEquals(fixture.plan.tasks().get(0).plannerTotalCount(), restored.tasks().get(0).plannerTotalCount());
        }
    }

    @Test void overlappingOrOversizedCircuitsCannotReachTheCpu() {
        var fixture = fixture(3, 0, 0);
        var invalid = new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.CYCLE, List.of(0, 1),
            List.of(new ECOExecutionPlan.ExecutionStep(0, 2, 1, 2), new ECOExecutionPlan.ExecutionStep(1, 1, 2, 2)), List.of());
        assertThrows(IllegalArgumentException.class, () -> new ECOExecutionPlan(fixture.plan.signature(),
            ExecutionMode.ORDERED_CYCLE, fixture.plan.tasks(), List.of(invalid), fixture.plan.schedule()));
        var oversized = new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.CYCLE, List.of(0, 1),
            List.of(new ECOExecutionPlan.ExecutionStep(0, 2),
                    new ECOExecutionPlan.ExecutionStep(1, 1, 2, Long.MAX_VALUE)), List.of());
        assertThrows(IllegalArgumentException.class, () -> new ECOExecutionPlan(fixture.plan.signature(),
            ExecutionMode.ORDERED_CYCLE, fixture.plan.tasks(), List.of(oversized), fixture.plan.schedule()));
    }

    private static void accept(ECOExecutionRuntime runtime, Fixture fixture, int id, long allowance, long accepted) {
        var candidates = runtime.candidates();
        assertEquals(1, candidates.size());
        var candidate = cn.dancingsnow.neoecoae.platform.JdkCompat.first(candidates);
        assertEquals(id, candidate.taskId());
        assertEquals(allowance, candidate.maxDispatchCount());
        fixture.progress[id].accept(accepted);
        runtime.onAccepted(candidate, accepted, new KeyCounter[0]);
    }

    private record Fixture(ECOExecutionPlan plan, Map<Integer, IPatternDetails> patterns,
            ExecutingCraftingJob.TaskProgress[] progress, ECOExecutionRuntime runtime) {}

    private static Fixture fixture(long laps, long acceptedFirst, long acceptedSecond) {
        return fixture(laps, acceptedFirst, acceptedSecond, false);
    }

    private static Fixture fixture(long laps, long acceptedFirst, long acceptedSecond, boolean processing) {
        return fixture(PlannerAmount.of(laps), PlannerAmount.of(acceptedFirst), PlannerAmount.of(acceptedSecond), processing);
    }

    private static Fixture fixture(PlannerAmount laps, PlannerAmount acceptedFirst, PlannerAmount acceptedSecond,
            boolean processing) {
        var first = pattern();
        var second = pattern();
        when(first.supportsPushInputsToExternalInventory()).thenReturn(processing);
        when(second.supportsPushInputsToExternalInventory()).thenReturn(processing);
        var patterns = Map.of(0, first, 1, second);
        PlannerAmount[] totals = {laps.multiply(2L).add(8L), laps};
        var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(id -> new ECOExecutionPlan.TaskSpec(id,
            PlanIdentity.patternIdentityFor(patterns.get(id)), patterns.get(id),
            ECOExecutionPlan.PatternRuntimeInfo.from(patterns.get(id)), totals[id], 0,
            ECOExecutionPlan.TaskKind.CYCLE_ORDERED, List.of())).toList();
        var steps = List.of(new ECOExecutionPlan.ExecutionStep(0, 5), new ECOExecutionPlan.ExecutionStep(0, 2),
            new ECOExecutionPlan.ExecutionStep(1, PlannerAmount.ONE, laps.equals(PlannerAmount.ONE) ? 1 : 2, laps),
            new ECOExecutionPlan.ExecutionStep(0, 3));
        var phase = new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.CYCLE, List.of(0, 1), steps, List.of());
        var signature = new PlanIdentity.Signature(mock(AEKey.class), 1, Map.of(), Map.of(), Map.of(), Map.of());
        var plan = new ECOExecutionPlan(signature, ExecutionMode.ORDERED_CYCLE, tasks, List.of(phase), new ECOExecutionSchedule(List.of()));
        var progress = new ExecutingCraftingJob.TaskProgress[2];
        for (int id = 0; id < 2; id++) {
            progress[id] = new ExecutingCraftingJob.TaskProgress();
            progress[id].setExact(totals[id], totals[id]);
            PlannerAmount accepted = id == 0 ? acceptedFirst : acceptedSecond;
            if (accepted.signum() > 0) progress[id].accept(accepted);
        }
        return new Fixture(plan, patterns, progress, new ECOExecutionRuntime(plan, patterns, progress));
    }

    private static IPatternDetails pattern() {
        var result = mock(IPatternDetails.class);
        when(result.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(result.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(mock(AEKey.class), 1)});
        return result;
    }
}
