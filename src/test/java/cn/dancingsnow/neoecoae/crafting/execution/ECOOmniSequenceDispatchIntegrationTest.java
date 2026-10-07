package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingProvider;
import com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

class ECOOmniSequenceDispatchIntegrationTest {
    interface MolecularProvider extends ICraftingProvider, MolecularBatchCraftingProvider {}
    interface OmniProvider extends ICraftingProvider, OmniBatchCraftingProvider {}

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static final class Fixture implements AutoCloseable {
        final MockedStatic<ModList> mods = mockStatic(ModList.class);
        final MockedStatic<CraftingCpuHelper> helpers = mockStatic(CraftingCpuHelper.class);
        final AEItemKey input = AEItemKey.of(Items.DIAMOND);
        final AEItemKey output = AEItemKey.of(Items.EMERALD);
        final IPatternDetails pattern = pattern(output);
        final IEnergyService energy = mock(IEnergyService.class);
        final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        final ExecutingCraftingJob job;
        final ECOCraftingProviderDispatcher dispatcher;

        Fixture() {
            var list = mock(ModList.class);
            mods.when(ModList::get).thenReturn(list);
            when(list.isLoaded("molecularmanipulator")).thenReturn(true);
            helpers.when(() -> CraftingCpuHelper.calculatePatternPower(any())).thenReturn(1.0);
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
            var plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(output, 100));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            when(plan.patternTimes()).thenReturn(Map.of(pattern, 100L));
            var link = mock(CraftingLink.class);
            when(link.getCraftingID()).thenReturn(UUID.randomUUID());
            try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
            }
            var context = new ECOCraftingJobContext(mock(ICraftingCPU.class), link.getCraftingID(),
                    job.finalOutput, 100, 100);
            var accounting = new ECOCraftingDispatchAccounting(ignored -> {}, () -> {},
                    ignored -> context, ignored -> {});
            dispatcher = new ECOCraftingProviderDispatcher(null, mock(ECOCraftingFastPathDispatcher.class),
                    new ECOCraftingEnergyTransaction(() -> {}, () -> 100), accounting);
            dispatcher.beginTick(100);
            inventory.insert(input, 100, Actionable.MODULATE);
        }

        private IPatternDetails pattern(AEItemKey product) {
            var selected = mock(IPatternDetails.class);
            when(selected.getDefinition()).thenReturn(AEItemKey.of(Items.STONE));
            when(selected.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
            when(selected.getOutputs()).thenReturn(List.of(new GenericStack(product, 1)));
            return selected;
        }

        ECOCraftingDispatchRequest request(IPatternDetails selected, AEItemKey ingredient, long perCraft,
                AEItemKey product, long count) {
            var inputs = new KeyCounter(); inputs.add(ingredient, perCraft);
            var outputs = new KeyCounter(); outputs.add(product, 1);
            return new ECOCraftingDispatchRequest(job, null, selected, new KeyCounter[]{inputs}, outputs,
                    new KeyCounter(), count, inventory, mock(Level.class));
        }

        ECOCraftingProviderDispatcher.Result dispatch(List<ICraftingProvider> providers, long count) {
            return dispatch(request(pattern, input, 1, output, count), providers);
        }

        ECOCraftingProviderDispatcher.Result dispatch(ECOCraftingDispatchRequest request,
                List<ICraftingProvider> providers) {
            return dispatcher.dispatchCandidate(request, providers, new ECOCraftingDispatchBudget(64, 64),
                    energy, mock(ECODispatchStallDiagnostics.class), ignored -> {}, () -> {},
                    (attempt, provider) -> provider.pushPattern(attempt.pattern(), attempt.inputs()));
        }

        MolecularProvider molecular(long capacity) {
            var provider = mock(MolecularProvider.class);
            when(provider.molecularmanipulator$supportsBatching(any())).thenReturn(true);
            when(provider.molecularmanipulator$getBatchLimit(any())).thenReturn(capacity);
            when(provider.pushPattern(any(), any())).thenReturn(true);
            return provider;
        }

        OmniProvider omni(OmniBatchAdmission admission) {
            var provider = mock(OmniProvider.class);
            when(provider.prepareOmniBatch(any())).thenReturn(admission);
            return provider;
        }

        OmniBatchAdmission admission(OmniBatchDelivery.Backpressure pressure) {
            var admission = mock(OmniBatchAdmission.class);
            when(admission.maxCrafts()).thenReturn(2L);
            doAnswer(call -> {
                accept(call.getArgument(0), pressure);
                return null;
            }).when(admission).commit(any());
            return admission;
        }

        void assertAcceptedTwo(ECOCraftingProviderDispatcher.Result result) {
            assertTrue(result.accepted());
            assertEquals(2, result.acceptedCrafts());
            assertEquals(98, inventory.list.get(input));
            assertEquals(2, job.waitingFor.list.get(output));
            assertEquals(98, job.tasks.get(pattern).value);
            assertFalse(job.suspended);
            verify(energy, never()).injectPower(anyDouble(), any());
        }

        @Override public void close() { helpers.close(); mods.close(); }
    }

    private static void accept(OmniBatchDelivery delivery, OmniBatchDelivery.Backpressure pressure) {
        delivery.accept(new OmniBatchDelivery.Receipt(
                OmniBatchDelivery.Ownership.PERSISTED_PROVIDER_QUEUE, pressure));
    }

    @Test void singleOrderUsesOrdinaryPushWithoutBatchContextOrSuspension() {
        try (var f = new Fixture()) {
            var provider = f.molecular(100);
            when(provider.pushPattern(any(), any())).thenAnswer(call -> {
                assertNull(MolecularBatchDispatchContext.current(call.getArgument(0), call.getArgument(1)));
                return true;
            });
            var result = f.dispatch(List.of(provider), 1);
            assertEquals(1, result.acceptedCrafts());
            assertEquals(99, f.inventory.list.get(f.input));
            assertEquals(1, f.job.waitingFor.list.get(f.output));
            assertFalse(f.job.suspended);
            verify(provider, never()).molecularmanipulator$getBatchLimit(any());
            verify(provider).pushPattern(any(), any());
        }
    }

    @Test void materialLimitedBatchOfOneFallsBackBeforeReservingBatchEnergy() {
        try (var f = new Fixture()) {
            f.inventory.list.set(f.input, 1);
            var provider = f.molecular(100);
            assertEquals(1, f.dispatch(List.of(provider), 100).acceptedCrafts());
            assertEquals(0, f.inventory.list.get(f.input));
            assertFalse(f.job.suspended);
            verify(f.energy).extractAEPower(eq(1.0), eq(Actionable.MODULATE), any());
            verify(provider).pushPattern(any(), any());
        }
    }

    @Test void energyLimitedBatchOfOneFallsBackWithoutLosingInputs() {
        try (var f = new Fixture()) {
            when(f.energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), any()))
                    .thenAnswer(call -> Math.min(1.0, (double) call.getArgument(0)));
            var provider = f.molecular(100);
            assertEquals(1, f.dispatch(List.of(provider), 100).acceptedCrafts());
            assertEquals(99, f.inventory.list.get(f.input));
            assertFalse(f.job.suspended);
            verify(f.energy).extractAEPower(eq(1.0), eq(Actionable.MODULATE), any());
            verify(provider).pushPattern(any(), any());
        }
    }

    @Test void molecularCapacityOfOneUsesOrdinaryPush() {
        try (var f = new Fixture()) {
            assertEquals(1, f.dispatch(List.of(f.molecular(1)), 100).acceptedCrafts());
            assertEquals(99, f.inventory.list.get(f.input));
            assertFalse(f.job.suspended);
        }
    }

    static java.util.stream.Stream<Throwable> postAcceptFailures() {
        return java.util.stream.Stream.of(new IllegalStateException("post-accept cleanup"),
                new NoClassDefFoundError("post-accept optional hook"), new AssertionError("post-accept hook"));
    }

    @ParameterizedTest @MethodSource("postAcceptFailures")
    void acceptedReceiptSurvivesCommitCleanupFailureAndIsAccounted(Throwable failure) {
        try (var f = new Fixture()) {
            var admission = f.admission(OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
            doAnswer(call -> {
                accept(call.getArgument(0), OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
                throw failure;
            }).when(admission).commit(any());
            f.assertAcceptedTwo(f.dispatch(List.of(f.omni(admission)), 100));
            verify(admission).close();
        }
    }

    @Test void repeatedRejectionCannotRefundAnAcceptedBatch() {
        try (var f = new Fixture()) {
            var admission = f.admission(OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
            doAnswer(call -> {
                OmniBatchDelivery delivery = call.getArgument(0);
                accept(delivery, OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
                delivery.reject(OmniBatchDelivery.Rejection.reject(OmniBatchDelivery.RejectReason.INTERNAL_ERROR));
                return null;
            }).when(admission).commit(any());
            f.assertAcceptedTwo(f.dispatch(List.of(f.omni(admission)), 100));
        }
    }

    @Test void admissionCloseFailureCannotUndoAccounting() {
        try (var f = new Fixture()) {
            var admission = f.admission(OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
            doThrow(new IllegalStateException("close cleanup")).when(admission).close();
            f.assertAcceptedTwo(f.dispatch(List.of(f.omni(admission)), 100));
        }
    }

    @Test void missingReceiptSuspendsWithoutRefundingOrFallingBack() {
        try (var f = new Fixture()) {
            var admission = f.admission(OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
            doNothing().when(admission).commit(any());
            var provider = f.omni(admission);
            assertFalse(f.dispatch(List.of(provider), 100).accepted());
            assertTrue(f.job.suspended);
            assertEquals(98, f.inventory.list.get(f.input));
            assertEquals(0, f.job.waitingFor.list.get(f.output));
            verify(provider, never()).pushPattern(any(), any());
            verify(f.energy, never()).injectPower(anyDouble(), any());
        }
    }

    @Test void conflictingAcceptanceAfterRejectionKeepsUncertainResourcesAndSuspends() {
        try (var f = new Fixture()) {
            var admission = f.admission(OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
            doAnswer(call -> {
                OmniBatchDelivery delivery = call.getArgument(0);
                delivery.reject(OmniBatchDelivery.Rejection.reject(OmniBatchDelivery.RejectReason.INTERNAL_ERROR));
                accept(delivery, OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
                return null;
            }).when(admission).commit(any());
            var provider = f.omni(admission);
            assertFalse(f.dispatch(List.of(provider), 100).accepted());
            assertTrue(f.job.suspended);
            assertEquals(98, f.inventory.list.get(f.input));
            verify(provider, never()).pushPattern(any(), any());
            verify(f.energy, never()).injectPower(anyDouble(), any());
        }
    }

    @Test void lateCallbackCannotChangeAcceptedOwnership() {
        try (var f = new Fixture()) {
            var captured = new AtomicReference<OmniBatchDelivery>();
            var admission = f.admission(OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
            doAnswer(call -> {
                captured.set(call.getArgument(0));
                accept(captured.get(), OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
                return null;
            }).when(admission).commit(any());
            f.assertAcceptedTwo(f.dispatch(List.of(f.omni(admission)), 100));
            assertThrows(IllegalStateException.class, () -> captured.get().reject(
                    OmniBatchDelivery.Rejection.reject(OmniBatchDelivery.RejectReason.INTERNAL_ERROR)));
            assertEquals(98, f.inventory.list.get(f.input));
        }
    }

    @ParameterizedTest @EnumSource(OmniBatchDelivery.Backpressure.class)
    void receiptControlsSameTickDispatchAndNextTickResumes(OmniBatchDelivery.Backpressure pressure) {
        try (var f = new Fixture()) {
            var admission = f.admission(pressure);
            var provider = f.omni(admission);
            assertTrue(f.dispatch(List.of(provider), 100).accepted());
            boolean canRepeat = pressure == OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE;
            assertEquals(canRepeat, f.dispatch(List.of(provider), 100).accepted());
            assertEquals(canRepeat ? 96 : 98, f.inventory.list.get(f.input));
            verify(provider, times(canRepeat ? 2 : 1)).prepareOmniBatch(any());
            f.dispatcher.beginTick(100);
            if (!canRepeat) assertFalse(f.dispatch(List.of(provider), 100).accepted());
            f.dispatcher.beginTick(101);
            assertTrue(f.dispatch(List.of(provider), 100).accepted());
            verify(provider, times(canRepeat ? 3 : 2)).prepareOmniBatch(any());
            assertFalse(f.job.suspended);
        }
    }

    @Test void backpressureDoesNotBlockAnotherPatternOrProvider() {
        try (var f = new Fixture()) {
            var paused = f.omni(f.admission(OmniBatchDelivery.Backpressure.RECHECK_NEXT_TICK));
            assertTrue(f.dispatch(List.of(paused), 100).accepted());
            var otherPattern = f.pattern(f.output);
            var progress = new ExecutingCraftingJob.TaskProgress(); progress.value = 100;
            f.job.tasks.put(otherPattern, progress);
            assertTrue(f.dispatch(f.request(otherPattern, f.input, 1, f.output, 100), List.of(paused)).accepted());
            var otherProvider = mock(ICraftingProvider.class);
            when(otherProvider.pushPattern(any(), any())).thenReturn(true);
            assertEquals(1, f.dispatch(List.of(paused, otherProvider), 100).acceptedCrafts());
            verify(paused, times(2)).prepareOmniBatch(any());
            verify(otherProvider).pushPattern(any(), any());
        }
    }

    @Test void resettingDispatchClearsBackpressureForTheNextJob() {
        try (var f = new Fixture()) {
            var provider = f.omni(f.admission(OmniBatchDelivery.Backpressure.RECHECK_NEXT_TICK));
            assertTrue(f.dispatch(List.of(provider), 100).accepted());
            assertFalse(f.dispatch(List.of(provider), 100).accepted());
            f.dispatcher.reset();
            f.dispatcher.beginTick(100);
            assertTrue(f.dispatch(List.of(provider), 100).accepted());
            verify(provider, times(2)).prepareOmniBatch(any());
        }
    }

    @Test void capacityChangedRejectionDefersWithoutSingleFallbackUntilNextTick() {
        try (var f = new Fixture()) {
            var admission = f.admission(OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
            doAnswer(call -> {
                OmniBatchDelivery delivery = call.getArgument(0);
                delivery.reject(OmniBatchDelivery.Rejection.reject(OmniBatchDelivery.RejectReason.CAPACITY_CHANGED));
                return null;
            }).when(admission).commit(any());
            var provider = f.omni(admission);
            assertFalse(f.dispatch(List.of(provider), 100).accepted());
            assertFalse(f.dispatch(List.of(provider), 100).accepted());
            assertEquals(100, f.inventory.list.get(f.input));
            verify(provider).prepareOmniBatch(any());
            verify(provider, never()).pushPattern(any(), any());
            verify(f.energy).injectPower(2.0, Actionable.MODULATE);
            clearInvocations(f.energy);
            f.dispatcher.beginTick(101);
            var nextAdmission = f.admission(OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE);
            when(provider.prepareOmniBatch(any())).thenReturn(nextAdmission);
            f.assertAcceptedTwo(f.dispatch(List.of(provider), 100));
        }
    }

    @Test void fiveNestedComponentLayersReturnOutputsAfterAccountingInTheSameTick() {
        try (var f = new Fixture()) {
            var keys = List.of(f.input, f.output, AEItemKey.of(Items.GOLD_INGOT), AEItemKey.of(Items.IRON_INGOT),
                    AEItemKey.of(Items.REDSTONE), AEItemKey.of(Items.LAPIS_LAZULI));
            var provider = f.molecular(100);
            var pending = new KeyCounter();
            when(provider.pushPattern(any(), any())).thenAnswer(call -> {
                IPatternDetails selected = call.getArgument(0);
                KeyCounter[] inputs = call.getArgument(1);
                var context = MolecularBatchDispatchContext.current(selected, inputs);
                long count = context == null ? 1 : context.craftCount();
                if (context != null) assertEquals(f.job.link.getCraftingID(), context.craftingId());
                pending.add(selected.getOutputs().getFirst().what(), count);
                return true;
            });
            doAnswer(call -> {
                for (var entry : pending) {
                    assertEquals(entry.getLongValue(), f.job.waitingFor.list.get(entry.getKey()),
                            "Outputs must be recorded before the provider flushes");
                    f.inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                    f.job.waitingFor.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                }
                pending.clear();
                return null;
            }).when(provider).flushOutputsAfterCpuAccounting();
            long count = 81;
            for (int layer = 0; layer < 5; layer++) {
                var selected = f.pattern(keys.get(layer + 1));
                var progress = new ExecutingCraftingJob.TaskProgress(); progress.value = count;
                f.job.tasks.put(selected, progress);
                var request = f.request(selected, keys.get(layer), layer == 0 ? 1 : 3, keys.get(layer + 1), count);
                assertEquals(count, f.dispatch(request, List.of(provider)).acceptedCrafts());
                assertEquals(count, f.inventory.list.get(keys.get(layer + 1)));
                assertEquals(0, progress.value);
                assertTrue(f.job.waitingFor.list.isEmpty());
                count /= 3;
            }
            assertEquals(1, f.inventory.list.get(keys.getLast()));
            assertFalse(f.job.suspended);
            verify(provider, times(5)).flushOutputsAfterCpuAccounting();
        }
    }
}
