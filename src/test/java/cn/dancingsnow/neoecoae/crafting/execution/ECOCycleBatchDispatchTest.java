package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
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

class ECOCycleBatchDispatchTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test void repeatedProcessingCircuitOffersAllRemainingLaps() {
        var f = new Fixture(64, false, false);
        var first = f.runtime.candidates().getFirst();
        assertEquals(64, first.maxDispatchCount());
        f.accept(first, 1);
        // One adaptive visit keeps its original candidate while accepting 1, 2, 4... chunks.
        f.accept(first, 2);
        f.accept(first, 4);
        assertEquals(1, f.runtime.candidates().getFirst().taskId());
        var second = f.runtime.candidates().getFirst();
        f.accept(second, 7);
        assertEquals(57, f.runtime.candidates().getFirst().maxDispatchCount());
        f.accept(f.runtime.candidates().getFirst(), 57);
        f.accept(f.runtime.candidates().getFirst(), 57);
        assertTrue(f.runtime.isComplete());
    }

    @Test void acceptedAheadOfTheLapSurvivesBoundAndUnboundReload() {
        var f = new Fixture(16, false, false);
        f.accept(f.runtime.candidates().getFirst(), 8);
        var tag = new CompoundTag();
        var registries = mock(HolderLookup.Provider.class);
        f.runtime.writeToNBT(tag, registries);
        for (boolean bound : List.of(false, true)) {
            var restored = bound
                ? ECOExecutionRuntime.fromNBT(f.plan, f.patterns, f.progress, tag, registries)
                : ECOExecutionRuntime.fromNBT(f.plan, f.patterns, tag, registries);
            var next = bound ? restored.candidates() : restored.candidates(Map.of(f.first, 8L, f.second, 16L));
            assertEquals(1, next.getFirst().taskId());
            restored.onAccepted(next.getFirst(), 8, new KeyCounter[0]);
            if (bound) f.progress[1].accept(8);
            next = bound ? restored.candidates() : restored.candidates(Map.of(f.first, 8L, f.second, 8L));
            assertEquals(0, next.getFirst().taskId());
            assertEquals(8, next.getFirst().maxDispatchCount());
        }
    }

    @Test void trillionAcceptedLapsAdvanceWithoutPerLapReplay() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var f = new Fixture(1_000_000_000_000L, false, false);
            f.accept(f.runtime.candidates().getFirst(), f.laps);
            f.accept(f.runtime.candidates().getFirst(), f.laps);
            assertTrue(f.runtime.isComplete());
        });
    }

    @Test void unknownOrSubstitutableInputsKeepTheOrderedWitness() {
        var f = new Fixture(16, false, false);
        when(f.first.getInputs()[0].getPossibleInputs()).thenReturn(new GenericStack[]{
            new GenericStack(f.bucket, 1), new GenericStack(mock(AEKey.class), 1)});
        var runtime = new ECOExecutionRuntime(f.plan, f.patterns, f.progress);
        assertEquals(1, runtime.candidates().getFirst().maxDispatchCount());
    }

    @Test void acceptanceCannotExceedRemainingRepeatedWork() {
        var f = new Fixture(8, false, false);
        var first = f.runtime.candidates().getFirst();
        f.accept(first, 7);
        assertThrows(IllegalArgumentException.class,
            () -> f.runtime.onAccepted(first, 2, new KeyCounter[0]));
        f.accept(first, 1);
        f.accept(f.runtime.candidates().getFirst(), 8);
        assertTrue(f.runtime.isComplete());
    }

    @Test void sharedInputsAllowSurplusBatchesWithoutStealingTheOtherBranch() {
        for (boolean dynamic : List.of(false, true)) {
            var f = new Fixture(32, true, dynamic);
            var candidate = f.runtime.candidates().getFirst();
            assertEquals(32, candidate.maxDispatchCount());
            var inventory = new ListCraftingInventory(ignored -> {});
            inventory.insert(f.bucket, 32, Actionable.MODULATE);
            inventory.insert(f.fuel, 33, Actionable.MODULATE);
            var inputs = new KeyCounter();
            inputs.add(f.bucket, 1); inputs.add(f.fuel, 1);
            assertEquals(1, f.runtime.limitCycleBatch(candidate, new KeyCounter[]{inputs}, inventory, 32));
            inventory.insert(f.fuel, 31, Actionable.MODULATE);
            assertEquals(32, f.runtime.limitCycleBatch(candidate, new KeyCounter[]{inputs}, inventory, 32));
            f.accept(candidate, 7);
            inventory.extract(f.fuel, 7, Actionable.MODULATE);
            assertEquals(25, f.runtime.limitCycleBatch(candidate, new KeyCounter[]{inputs}, inventory, 25));
        }
    }

    @Test void oneBucketCannotBorrowItsFutureReturnsToFundABatch() {
        var f = new Fixture(64, false, false);
        var job = f.job();
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(f.bucket, 1, Actionable.MODULATE);
        var input = new KeyCounter(); input.add(f.bucket, 1);
        var output = new KeyCounter(); output.add(f.filled, 1);
        var candidate = job.executionRuntime.candidates().getFirst();
        var request = new ECOCraftingDispatchRequest(job, candidate, f.first, new KeyCounter[]{input}, output,
            new KeyCounter(), candidate.maxDispatchCount(), inventory, mock(net.minecraft.world.level.Level.class));
        var energy = mock(appeng.api.networking.energy.IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
        var provider = mock(appeng.api.networking.crafting.ICraftingProvider.class);
        var batch = ECOBatchDispatchPlanning.plan(request, provider, 64, 64, 1, energy,
            cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchMode.LINEAR);
        assertEquals(1, batch.craftCount());
        inventory.insert(f.bucket, 7, Actionable.MODULATE);
        batch = ECOBatchDispatchPlanning.plan(request, provider, 64, 64, 1, energy,
            cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchMode.LINEAR);
        assertEquals(8, batch.craftCount());
    }

    @Test void adaptiveChunksCommitAcrossLapsUsingRealRuntimeAndAccounting() {
        var f = new Fixture(16, false, false);
        var job = f.job();
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(f.bucket, 16, Actionable.MODULATE);
        var provider = mock(appeng.helpers.patternprovider.PatternProviderLogic.class,
            withSettings().extraInterfaces(cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor.class));
        when(((cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor) provider)
            .neoecoae$getSendList()).thenReturn(List.of());
        var energy = mock(appeng.api.networking.energy.IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
        var dispatcher = new ECOProcessingPatternDispatcher(null, new ECOCraftingEnergyTransaction(() -> {}, () -> 0),
            new ECOCraftingDispatchAccounting(ignored -> {}, () -> {},
                current -> new cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext(
                    mock(appeng.api.networking.crafting.ICraftingCPU.class), current.link.getCraftingID(),
                    current.finalOutput, f.laps, current.remainingAmount), ignored -> {}));
        dispatcher.beginTick(0);
        for (int taskId = 0; taskId < 2; taskId++) {
            var candidate = job.executionRuntime.candidates().getFirst();
            assertEquals(taskId, candidate.taskId());
            var input = new KeyCounter(); input.add(taskId == 0 ? f.bucket : f.filled, 1);
            var output = new KeyCounter(); output.add(taskId == 0 ? f.filled : f.bucket, 1);
            var request = new ECOCraftingDispatchRequest(job, candidate, candidate.pattern(), new KeyCounter[]{input},
                output, new KeyCounter(), candidate.maxDispatchCount(), inventory, mock(net.minecraft.world.level.Level.class));
            var offers = new java.util.ArrayList<Long>();
            var result = dispatcher.tryScaledDispatch(request, provider, 1, energy, ignored -> {},
                (scaled, ignored) -> { offers.add(scaled.allowedCrafts()); return true; });
            assertEquals(List.of(1L, 2L, 4L, 8L, 1L), offers);
            assertEquals(16, result.acceptedCrafts());
            assertEquals(0, job.tasks.get(candidate.pattern()).value);
            inventory.insert(taskId == 0 ? f.filled : f.bucket, 16, Actionable.MODULATE);
        }
        assertTrue(job.executionRuntime.isComplete());
    }

    private static ExecutingCraftingJob job(Fixture f) {
        var shell = mock(appeng.api.networking.crafting.ICraftingPlan.class);
        when(shell.finalOutput()).thenReturn(new GenericStack(f.filled, f.laps));
        when(shell.emittedItems()).thenReturn(new KeyCounter());
        when(shell.patternTimes()).thenReturn(Map.of(f.first, f.laps, f.second, f.laps));
        try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
            var link = mock(appeng.crafting.CraftingLink.class);
            when(link.getCraftingID()).thenReturn(java.util.UUID.randomUUID());
            return new ExecutingCraftingJob(shell, f.plan, ignored -> {}, link, null);
        }
    }

    private static final class Fixture {
        final long laps;
        final AEKey bucket = AEItemKey.of(net.minecraft.world.item.Items.BUCKET);
        final AEKey filled = AEItemKey.of(net.minecraft.world.item.Items.WATER_BUCKET);
        final AEKey fuel = AEItemKey.of(net.minecraft.world.item.Items.COAL);
        final IPatternDetails first, second;
        final Map<Integer, IPatternDetails> patterns;
        final ExecutingCraftingJob.TaskProgress[] progress = new ExecutingCraftingJob.TaskProgress[2];
        final ECOExecutionPlan plan;
        final ECOExecutionRuntime runtime;

        Fixture(long laps, boolean sharedFuel, boolean dynamic) {
            this.laps = laps;
            first = pattern(sharedFuel ? List.of(bucket, fuel) : List.of(bucket), filled);
            second = pattern(sharedFuel ? List.of(filled, fuel) : List.of(filled), bucket);
            patterns = Map.of(0, first, 1, second);
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(id -> new ECOExecutionPlan.TaskSpec(id,
                PlanIdentity.patternIdentityFor(patterns.get(id)), patterns.get(id),
                ECOExecutionPlan.PatternRuntimeInfo.from(patterns.get(id)), laps, 0,
                dynamic ? ECOExecutionPlan.TaskKind.CYCLE_DYNAMIC : ECOExecutionPlan.TaskKind.CYCLE_ORDERED)).toList();
            var steps = dynamic ? List.<ECOExecutionPlan.ExecutionStep>of() : List.of(
                new ECOExecutionPlan.ExecutionStep(0, 1), new ECOExecutionPlan.ExecutionStep(1, 1, 2, laps));
            var phase = new ECOExecutionPlan.PhaseSpec(0, 0,
                dynamic ? ECOExecutionSchedule.Type.DYNAMIC_CYCLE : ECOExecutionSchedule.Type.CYCLE,
                List.of(0, 1), steps, List.of(), dynamic ? Map.of(0, laps, 1, laps) : Map.of(), Map.of());
            plan = new ECOExecutionPlan(new PlanIdentity.Signature(mock(AEKey.class), 1, Map.of(), Map.of(), Map.of(), Map.of()),
                dynamic ? ExecutionMode.DYNAMIC_CYCLE : ExecutionMode.ORDERED_CYCLE, tasks, List.of(phase),
                new ECOExecutionSchedule(List.of()));
            for (int id = 0; id < 2; id++) {
                progress[id] = new ExecutingCraftingJob.TaskProgress();
                progress[id].setExact(BigInteger.valueOf(laps), BigInteger.valueOf(laps));
            }
            runtime = new ECOExecutionRuntime(plan, patterns, progress);
        }

        void accept(ECOExecutionRuntime.DispatchCandidate candidate, long count) {
            progress[candidate.taskId()].accept(count);
            runtime.onAccepted(candidate, count, new KeyCounter[0]);
        }

        ExecutingCraftingJob job() { return ECOCycleBatchDispatchTest.job(this); }

        private static IPatternDetails pattern(List<AEKey> keys, AEKey output) {
            var pattern = mock(appeng.crafting.pattern.AEProcessingPattern.class);
            when(pattern.getDefinition()).thenReturn(mock(AEItemKey.class));
            var inputs = keys.stream().map(key -> {
                var input = mock(IPatternDetails.IInput.class);
                when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(key, 1)});
                when(input.getMultiplier()).thenReturn(1L);
                return input;
            }).toArray(IPatternDetails.IInput[]::new);
            when(pattern.getInputs()).thenReturn(inputs);
            when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 1)));
            when(pattern.supportsPushInputsToExternalInventory()).thenReturn(true);
            return pattern;
        }
    }
}
