package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.crafting.pattern.EncodedProcessingPattern;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** Runs real CPU scheduling, ingredient extraction, provider transactions and ledger settlement. */
class ECOStickyDispatchIntegrationTest {
    private MockedStatic<TickHandler> ticks;
    private long tick;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @BeforeEach void setupTicks() {
        var handler = mock(TickHandler.class);
        when(handler.getCurrentTick()).thenAnswer(call -> tick);
        ticks = mockStatic(TickHandler.class);
        ticks.when(TickHandler::instance).thenReturn(handler);
    }

    @AfterEach void closeTicks() { ticks.close(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulPatternKeepsDispatchingBeyondThirtyTwoAcrossCallbacks(boolean ecoPlan) {
        var f = new Fixture(ecoPlan, 1, 100_000);
        for (int i = 0; i < 16; i++) assertEquals(64, f.dispatch(64));
        f.assertRun(0, f.first, 1024);
        assertEquals(100_000 - 1024, f.logic.getInventory().list.get(f.inputs[f.first]));
        assertEquals(1024, f.job.waitingFor.list.get(f.outputs[f.first]));
        assertEquals(100_000, f.logic.getInventory().list.get(f.inputs[f.second]));
        assertFalse(f.job.suspended, f.job.permanentExecutionError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulLargeBatchesKeepPriorityAndPreserveMaterialAccounting(boolean ecoPlan) {
        var f = new Fixture(ecoPlan, 512, 100_000);
        assertEquals(64 * 512, f.dispatch(64));
        assertEquals(64 * 512, f.dispatch(64));
        f.assertRun(0, f.first, 128);
        assertEquals(100_000 - 128 * 512, f.logic.getInventory().list.get(f.inputs[f.first]));
        assertEquals(100_000, f.logic.getInventory().list.get(f.inputs[f.second]));
        assertEquals(128 * 512, f.job.waitingFor.list.get(f.outputs[f.first]));
        assertEquals(0, f.job.waitingFor.list.get(f.outputs[f.second]));
        assertTrue(f.batchSizes.stream().allMatch(count -> count == 512));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryBudgetPausesPriorityAcrossTicksWithoutCountingAsFailure(boolean ecoPlan) {
        var f = new Fixture(ecoPlan, 1, 100_000);
        for (int i = 0; i < 40; i++) assertEquals(1, f.dispatch(1));
        assertEquals(0, f.dispatch(0));
        assertEquals(64, f.dispatch(64));
        f.assertRun(0, f.first, 104);
    }

    @Test void aRejectedPatternYieldsImmediatelyAndRefundsTheRejectedInput() {
        var f = new Fixture(true, 1, 100_000);
        assertEquals(64, f.dispatch(64));
        f.rejectNext(f.first);
        assertEquals(1, f.dispatch(1));
        assertEquals(63, f.dispatch(63));
        f.assertRun(0, f.first, 64);
        f.assertRun(64, f.second, 64);
        assertEquals(100_000 - 64, f.logic.getInventory().list.get(f.inputs[f.first]));
        assertEquals(64, f.job.waitingFor.list.get(f.outputs[f.first]));
        assertEquals(64, f.dispatch(64));
        f.assertRun(64, f.second, 128);
    }

    @Test void anotherProviderCanAcceptTheSamePatternWithoutLosingPriority() {
        var f = new Fixture(true, 1, 100_000);
        assertEquals(64, f.dispatch(64));
        f.rejectNext(f.first);
        var alternative = mock(ICraftingProvider.class);
        when(alternative.pushPattern(any(), any())).thenAnswer(call -> f.receive(f.first, 1));
        when(f.service.getProviders(f.patterns[f.first])).thenReturn(List.of(f.providers[f.first], alternative));
        assertEquals(1, f.dispatch(1));
        assertEquals(63, f.dispatch(63));
        f.assertRun(0, f.first, 128);
        assertEquals(100_000 - 128, f.logic.getInventory().list.get(f.inputs[f.first]));
        verify(alternative, atLeastOnce()).pushPattern(eq(f.patterns[f.first]), any());
    }

    @Test void missingIngredientsReleasePriorityAndOtherPatternKeepsItWhenInputsReturn() {
        var f = new Fixture(true, 1, 100_000);
        assertEquals(64, f.dispatch(64));
        long removed = f.logic.getInventory().extract(f.inputs[f.first], Long.MAX_VALUE, Actionable.MODULATE);
        assertEquals(1, f.dispatch(1));
        f.logic.getInventory().insert(f.inputs[f.first], removed, Actionable.MODULATE);
        f.logic.taskSchedulerForOutput().recordPhysicalInsert(f.inputs[f.first]);
        assertEquals(64, f.dispatch(64));
        f.assertRun(64, f.second, 65);
        when(f.providers[f.second].isBusy()).thenReturn(true);
        assertEquals(64, f.dispatch(64));
        f.assertRun(129, f.first, 64);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completingATaskImmediatelyMovesToTheNextPattern(boolean ecoPlan) {
        var f = new Fixture(ecoPlan, 1, 3);
        assertEquals(6, f.dispatch(64));
        f.assertRun(0, f.first, 3);
        f.assertRun(3, f.second, 3);
        assertEquals(0, f.dispatch(64));
        assertTrue(f.job.tasks.values().stream().allMatch(progress -> progress.value == 0));
        if (ecoPlan) assertTrue(f.job.executionRuntime.isComplete());
    }

    @Test void resettingDispatchStateDropsThePreviousPriority() {
        var f = new Fixture(true, 1, 100_000);
        assertEquals(5, f.dispatch(5));
        f.rejectNext(f.first);
        assertEquals(5, f.dispatch(5));
        f.assertRun(5, f.second, 5);
        f.logic.taskSchedulerForLifecycle().resetDispatchState();
        assertEquals(64, f.dispatch(64));
        f.assertRun(10, f.first, 64);
    }

    @Test void aTemporarilyIllegalCandidateCannotUseItsPreviousPriority() throws ReflectiveOperationException {
        var f = new Fixture(true, 1, 100_000);
        var runtime = spy(f.job.executionRuntime);
        var runtimeField = ExecutingCraftingJob.class.getDeclaredField("executionRuntime");
        runtimeField.setAccessible(true);
        runtimeField.set(f.job, runtime);
        boolean[] firstLegal = {true};
        doAnswer(call -> {
            @SuppressWarnings("unchecked")
            var candidates = (List<ECOExecutionRuntime.DispatchCandidate>) call.callRealMethod();
            return firstLegal[0] ? candidates : candidates.stream()
                    .filter(candidate -> !candidate.pattern().equals(f.patterns[f.first])).toList();
        }).when(runtime).candidates();
        assertEquals(5, f.dispatch(5));
        firstLegal[0] = false;
        assertEquals(1, f.dispatch(1));
        firstLegal[0] = true;
        assertEquals(64, f.dispatch(64));
        f.assertRun(5, f.second, 65);
    }

    @Test void busyProvidersReleasePriorityWithoutAttemptingAPush() {
        var f = new Fixture(true, 1, 100_000);
        assertEquals(64, f.dispatch(64));
        when(f.providers[f.first].isBusy()).thenReturn(true);
        assertEquals(64, f.dispatch(64));
        when(f.providers[f.first].isBusy()).thenReturn(false);
        assertEquals(64, f.dispatch(64));
        f.assertRun(0, f.first, 64);
        f.assertRun(64, f.second, 128);
        verify(f.providers[f.first], times(64)).pushPattern(any(), any());
    }

    @Test void noBatchAttemptAndAnExhaustedOrdinaryBudgetOnlyPausePriority() {
        var f = new Fixture(true, 512, 100_000);
        assertEquals(5 * 512, f.dispatch(5));
        for (var provider : f.providers) {
            when(((ParallelProvider) provider).eco$getAvailableParallelSlots()).thenReturn(0);
        }
        assertEquals(0, f.dispatch(0));
        for (var provider : f.providers) {
            when(((ParallelProvider) provider).eco$getAvailableParallelSlots()).thenReturn(512);
        }
        assertEquals(64 * 512, f.dispatch(64));
        f.assertRun(0, f.first, 69);
    }

    interface ParallelProvider extends ICraftingProvider, ECOParallelCraftingProvider {}

    private final class Fixture {
        final AEItemKey[] inputs = {AEItemKey.of(Items.QUARTZ), AEItemKey.of(Items.COBBLESTONE)};
        final AEItemKey[] outputs = {AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(Items.GOLD_INGOT)};
        final AEProcessingPattern[] patterns = {pattern(0), pattern(1)};
        final ICraftingProvider[] providers = new ICraftingProvider[2];
        final IEnergyService energy = mock(IEnergyService.class);
        final CraftingService service = mock(CraftingService.class);
        final Level level = mock(Level.class);
        final List<Integer> accepted = new ArrayList<>();
        final List<Long> batchSizes = new ArrayList<>();
        final ECOCraftingCPULogic logic;
        final ExecutingCraftingJob job;
        final int first;
        final int second;
        BiPredicate<Integer, Long> admission = (id, count) -> true;

        Fixture(boolean ecoPlan, int batchSize, long count) {
            var cpu = mock(ECOCraftingCPU.class);
            when(cpu.isActive()).thenReturn(true);
            when(cpu.getLevel()).thenReturn(level);
            logic = new ECOCraftingCPULogic(cpu);
            var plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(outputs[1], count));
            when(plan.patternTimes()).thenReturn(Map.of(patterns[0], count, patterns[1], count));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            var tasks = List.of(task(0, count), task(1, count));
            var phase = new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.DAG,
                    List.of(0, 1), List.of(), List.of());
            var executionPlan = new ECOExecutionPlan(mock(PlanIdentity.Signature.class), ExecutionMode.PHASED_DAG,
                    tasks, List.of(phase), new ECOExecutionSchedule(List.of()));
            var link = mock(CraftingLink.class);
            when(link.getCraftingID()).thenReturn(UUID.randomUUID());
            try (var tracker = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(plan, ecoPlan ? executionPlan : null, ignored -> {}, link, null);
            }
            logic.setJobFromPersistence(job);
            first = ecoPlan || job.tasks.keySet().iterator().next().equals(patterns[0]) ? 0 : 1;
            second = 1 - first;
            for (int id = 0; id < patterns.length; id++) {
                final int patternId = id;
                if (batchSize > 1) {
                    var provider = mock(ParallelProvider.class);
                    when(provider.eco$getAvailableParallelSlots()).thenReturn(batchSize);
                    when(provider.eco$pushPatternBatch(any(), any(), anyLong(), any()))
                            .thenAnswer(call -> receive(patternId, call.getArgument(2)));
                    providers[id] = provider;
                } else {
                    providers[id] = mock(ICraftingProvider.class);
                    when(providers[id].pushPattern(any(), any())).thenAnswer(call -> receive(patternId, 1));
                }
                when(service.getProviders(patterns[id])).thenReturn(List.of(providers[id]));
                logic.getInventory().insert(inputs[id], count, Actionable.MODULATE);
            }
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
        }

        private AEProcessingPattern pattern(int id) {
            var encoded = mock(EncodedProcessingPattern.class);
            when(encoded.sparseInputs()).thenReturn(List.of(new GenericStack(inputs[id], 1)));
            when(encoded.sparseOutputs()).thenReturn(List.of(new GenericStack(outputs[id], 1)));
            var definition = mock(AEItemKey.class);
            when(definition.get(AEComponents.ENCODED_PROCESSING_PATTERN)).thenReturn(encoded);
            return new AEProcessingPattern(definition);
        }

        private ECOExecutionPlan.TaskSpec task(int id, long count) {
            return new ECOExecutionPlan.TaskSpec(id, mock(PlanIdentity.PatternIdentity.class), patterns[id],
                    ECOExecutionPlan.PatternRuntimeInfo.from(patterns[id]), count, 0, ECOExecutionPlan.TaskKind.DAG);
        }

        private boolean receive(int id, long count) {
            if (!admission.test(id, count)) return false;
            accepted.add(id);
            batchSizes.add(count);
            return true;
        }

        void rejectNext(int target) {
            admission = (id, count) -> {
                if (id != target) return true;
                admission = (ignored, copies) -> true;
                return false;
            };
        }

        int dispatch(int budget) {
            tick++;
            return logic.executeNormalCrafting(budget, service, energy, level);
        }

        void assertRun(int start, int id, int length) {
            assertEquals(Collections.nCopies(length, id), accepted.subList(start, start + length));
        }
    }
}
