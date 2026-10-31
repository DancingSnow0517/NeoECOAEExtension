package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.compat.gregtech.ECOGregTechProcessingDispatch;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.gtlcore.gtlcore.integration.ae2.crafting.IPatternProviderAutoExpand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOGregTechProcessingDispatchIntegrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void molecularAssemblerReceivesTheWholeReadyBatchWithItsRegisteredPattern() {
        var f = new Fixture(200_000, false);
        var provider = f.molecularAssembler();
        assertFalse(ECOGregTechProcessingDispatch.isBuffer(provider));
        assertTrue(ECOGregTechProcessingDispatch.isBatchProvider(provider));
        var result = f.dispatch(provider, (request, target) -> fail("Assembler requires canonical-pattern admission"));
        assertEquals(200_000, result.acceptedCrafts());
        assertEquals(1, provider.pushes);
        assertEquals(200_000, provider.queued);
        assertEquals(600_000, provider.received.get(f.iron));
        assertEquals(100_000_000, provider.received.get(f.water));
        assertEquals(0, f.inventory.list.get(f.iron));
        assertEquals(0, f.progress.value);
        assertEquals(400_000, f.job.waitingFor.list.get(f.output));
        assertEquals(3, f.inputs[0].get(f.iron));
    }

    @Test void molecularAssemblerQueueCapacityPreventsOutputOverflowBeforeAnyDebit() {
        var f = new Fixture(64, false);
        var provider = f.molecularAssembler();
        provider.queued = Long.MAX_VALUE / 2 - 4;
        var result = f.dispatch(provider, (request, target) -> fail("Capacity must be checked before native admission"));
        assertEquals(4, result.acceptedCrafts());
        assertEquals(Long.MAX_VALUE / 2, provider.queued);
        assertEquals(1, provider.pushes);
        assertEquals(180, f.inventory.list.get(f.iron));
        assertEquals(60, f.progress.value);
        assertEquals(8, f.job.waitingFor.list.get(f.output));
    }

    @Test void molecularAssemblerMissingToolsCannotConsumeBatchMaterialsOrEnergy() {
        var f = new Fixture(64, false);
        var provider = f.molecularAssembler();
        provider.toolsPresent = false;
        assertNull(f.dispatch(provider, (request, target) -> fail("Missing tools must stop batch admission")));
        assertEquals(0, provider.pushes);
        assertEquals(192, f.inventory.list.get(f.iron));
        assertEquals(64, f.progress.value);
        verify(f.energy, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
    }

    @Test void molecularAssemblerRejectingTheBatchRefundsEveryInputAndTheReservedEnergy() {
        var f = new Fixture(64, false);
        var provider = f.molecularAssembler();
        provider.reject = true;
        assertNull(f.dispatch(provider, (request, target) -> fail("A rejected native batch must not use a wrapper")));
        assertEquals(192, f.inventory.list.get(f.iron));
        assertEquals(32_000, f.inventory.list.get(f.water));
        assertEquals(0, provider.queued);
        assertEquals(64, f.progress.value);
        assertTrue(f.job.waitingFor.list.isEmpty());
        assertFalse(f.job.suspended);
        verify(f.energy, atLeastOnce()).injectPower(anyDouble(), eq(Actionable.MODULATE));
    }

    @Test void gtlBufferReceivesOneCompleteBatchUsingItsCanonicalPattern() {
        var f = new Fixture(64, false);
        var provider = f.gtlBuffer();
        var result = f.dispatch(provider, (request, target) -> fail("Native GTL admission owns the transport"));
        assertEquals(64, result.acceptedCrafts());
        assertEquals(1, provider.pushes);
        assertEquals(192, provider.stored.get(f.iron));
        assertEquals(32_000, provider.stored.get(f.water));
        assertEquals(0, f.inventory.list.get(f.iron));
        assertEquals(0, f.progress.value);
        assertEquals(128, f.job.waitingFor.list.get(f.output));
        assertEquals(3, f.inputs[0].get(f.iron));
    }

    @Test void nativeGtBufferEmitsEveryRepeatedSparseInputAndKeepsWorkerPatternIdentity() {
        var f = new Fixture(64, false);
        var provider = new com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine();
        provider.pattern = f.pattern;
        assertNotNull(f.dispatch(provider, (request, target) -> fail("GT transport owns native admission")));
        assertSame(f.pattern, provider.workerPattern);
        assertEquals(1, provider.pushes);
        assertEquals(192, provider.stored.get(f.iron));
        assertEquals(32_000, provider.stored.get(f.water));
        assertSame(f.pattern, ECOGregTechProcessingDispatch.bufferExecutionPattern(f.pattern, f.inputs));
    }

    @Test void gtlBufferLongCapacityPreventsOverflowAndDoesNotReplayAnOrdinaryPush() {
        var f = new Fixture(64, false);
        var provider = f.gtlBuffer();
        provider.stored.add(f.iron, Long.MAX_VALUE - 12);
        var result = f.dispatch(provider, (request, target) -> fail("Capacity-limited GTL dispatch remains native"));
        assertEquals(4, result.acceptedCrafts());
        assertEquals(Long.MAX_VALUE, provider.stored.get(f.iron));
        assertEquals(1, provider.pushes);
        assertEquals(2, provider.probes);
        assertEquals(180, f.inventory.list.get(f.iron));
        assertTrue(f.dispatcher.isScaledFallbackDeferred(f.request, provider));
    }

    @Test void zeroNativeCapacityDoesNotExtractOrInvokeTheProvider() {
        var f = new Fixture(64, false);
        var provider = f.gtlBuffer();
        provider.stored.add(f.iron, Long.MAX_VALUE);
        assertNull(f.dispatch(provider, (request, target) -> fail("A full buffer cannot receive a fallback batch")));
        assertEquals(0, provider.pushes);
        assertEquals(192, f.inventory.list.get(f.iron));
        verify(f.energy, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
    }

    @Test void rejectedNativeBatchesRestoreEveryInputAndEnergyReservation() {
        var f = new Fixture(64, true);
        var provider = f.gtlBuffer();
        provider.reject = true;
        assertNull(f.dispatch(provider, (request, target) -> fail("Native rejection must not use a scaled wrapper")));
        assertEquals(192, f.inventory.list.get(f.iron));
        assertEquals(1, f.inventory.list.get(f.circuit));
        assertEquals(0, provider.stored.get(f.iron));
        assertEquals(64, f.progress.value);
        assertFalse(f.job.suspended);
        verify(f.energy, atLeastOnce()).injectPower(anyDouble(), eq(Actionable.MODULATE));
    }

    @Test void failureAfterInsertionRetainsOwnershipAndSuspendsWithoutDuplicatingTheBatch() {
        var f = new Fixture(64, true);
        var provider = f.gtlBuffer();
        provider.failAfterInsertion = true;
        assertNull(f.dispatch(provider, (request, target) -> fail("Uncertain custody must never be replayed")));
        assertEquals(1, provider.pushes);
        assertEquals(192, provider.stored.get(f.iron));
        assertEquals(1, provider.stored.get(f.circuit));
        assertEquals(0, f.inventory.list.get(f.iron));
        assertEquals(0, f.inventory.list.get(f.circuit));
        assertTrue(f.job.suspended);
        verify(f.energy, never()).injectPower(anyDouble(), any());
    }

    @Test void gtlProviderUsesEcoDoublingEvenWhenGtlAutoExpandIsDisabled() {
        var f = new Fixture(16, false);
        var provider = f.gtlProvider();
        var queried = new ArrayList<Long>();
        when(((IPatternProviderAutoExpand) provider).gtlcore$getMaxPatternOperations(eq(f.pattern), anyLong()))
                .thenAnswer(call -> { queried.add(call.getArgument(1)); return 1L; });
        var offers = new ArrayList<Long>();
        var stored = new KeyCounter();
        var result = f.dispatch(provider, (request, target) -> {
            offers.add(request.allowedCrafts());
            request.pattern().pushInputsToExternalInventory(request.inputs(), stored::add);
            return true;
        });
        assertEquals(16, result.acceptedCrafts());
        assertTrue(queried.isEmpty());
        assertEquals(List.of(1L, 2L, 4L, 8L, 1L), offers);
        assertEquals(48, stored.get(f.iron));
        assertEquals(8_000, stored.get(f.water));
    }

    @Test void aSingleProgrammedCircuitCanConfigureAnEntireNegotiatedBatch() {
        var f = new Fixture(64, true);
        var provider = f.gtlProvider();
        var stored = new KeyCounter();
        var result = f.dispatch(provider, (request, target) -> {
            assertEquals(64, request.allowedCrafts());
            assertEquals(1, request.inputs()[2].get(f.circuit));
            assertEquals(1, request.pattern().getInputs()[2].getMultiplier());
            request.pattern().pushInputsToExternalInventory(request.inputs(), stored::add);
            return true;
        });
        assertEquals(64, result.acceptedCrafts());
        assertEquals(1, stored.get(f.circuit));
        assertEquals(192, stored.get(f.iron));
        assertEquals(0, f.inventory.list.get(f.circuit));
        assertEquals(128, f.job.waitingFor.list.get(f.output));
    }

    @Test void gtlBufferVariantsAlsoUseCircuitConfigurationAmounts() {
        var f = new Fixture(64, true);
        var provider = f.gtlBuffer();
        assertEquals(64, f.dispatch(provider, (request, target) -> false).acceptedCrafts());
        assertEquals(1, provider.stored.get(f.circuit));
    }

    @Test void aConfigurationBatchBacksOffUsingEcoPolicyAndRefundsTheRejectedOffer() {
        var f = new Fixture(64, true);
        var provider = f.gtlProvider();
        when(((IPatternProviderAutoExpand) provider).gtlcore$getMaxPatternOperations(any(), anyLong()))
                .thenThrow(new AssertionError("GTL must not choose an ECO job's batch"));
        var offers = new ArrayList<Long>();
        var stored = new KeyCounter();
        var result = f.dispatch(provider, (request, target) -> {
            offers.add(request.allowedCrafts());
            if (request.allowedCrafts() > 32) return false;
            request.pattern().pushInputsToExternalInventory(request.inputs(), stored::add);
            return true;
        });
        assertEquals(List.of(64L, 32L), offers);
        assertEquals(32, result.acceptedCrafts());
        assertEquals(1, stored.get(f.circuit));
        assertEquals(96, stored.get(f.iron));
        assertEquals(96, f.inventory.list.get(f.iron));
        assertEquals(0, f.inventory.list.get(f.circuit));
        assertEquals(64, f.job.waitingFor.list.get(f.output));
        assertFalse(f.job.suspended);
    }

    @Test void unrelatedResourcesNeverEnterTheNativeItemFluidBufferLane() {
        var f = new Fixture(16, false);
        f.inputs[0].add(mock(AEKey.class), 1);
        assertFalse(ECOProcessingPatternDispatcher.supportsScaledDispatch(f.request, f.gtlBuffer()));
    }

    @Test void liveProviderBackpressureStopsEcoDoublingWithoutReadingGtlPolicy() {
        var f = new Fixture(16, false);
        var provider = f.gtlProvider();
        when(((IPatternProviderAutoExpand) provider).gtlcore$getMaxPatternOperations(any(), anyLong()))
                .thenReturn(4L, 0L);
        var result = f.dispatch(provider, (request, target) -> {
            when(provider.isBusy()).thenReturn(true);
            return true;
        });
        assertEquals(1, result.acceptedCrafts());
        assertEquals(45, f.inventory.list.get(f.iron));
        assertEquals(15, f.progress.value);
        verify((IPatternProviderAutoExpand) provider, never()).gtlcore$getMaxPatternOperations(any(), anyLong());
    }

    @Test void brokenGtlAutoExpandPolicyCannotDisableEcoDoubling() {
        var f = new Fixture(16, false);
        var provider = f.gtlProvider();
        when(((IPatternProviderAutoExpand) provider).gtlcore$getMaxPatternOperations(any(), anyLong()))
                .thenThrow(new IllegalStateException("Optional capacity query unavailable"));
        var offers = new ArrayList<Long>();
        var result = f.dispatch(provider, (request, target) -> { offers.add(request.allowedCrafts()); return true; });
        assertEquals(16, result.acceptedCrafts());
        assertEquals(List.of(1L, 2L, 4L, 8L, 1L), offers);
        assertFalse(f.job.suspended);
    }

    @Test void ordinaryProvidersKeepTheirLinearCircuitMaterialContract() {
        var f = new Fixture(64, true);
        var provider = mock(PatternProviderLogic.class, withSettings().extraInterfaces(PatternProviderLogicAccessor.class));
        when(((PatternProviderLogicAccessor) provider).neoecoae$getSendList()).thenReturn(List.of());
        var result = f.dispatch(provider, (request, target) -> true);
        assertEquals(1, result.acceptedCrafts());
        assertEquals(189, f.inventory.list.get(f.iron));
        assertEquals(63, f.progress.value);
    }

    @Test void nativeBufferStillHonorsEcosPerTickDispatchLimit() {
        var f = new Fixture(1_000_001, false);
        var provider = f.gtlBuffer();
        assertEquals(1_000_000, f.dispatch(provider, (request, target) -> false).acceptedCrafts());
        assertEquals(1, provider.pushes);
        assertEquals(3, f.inventory.list.get(f.iron));
        assertEquals(1, f.progress.value);
    }

    private static final class Fixture {
        final AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        final AEItemKey output = AEItemKey.of(Items.DIAMOND);
        final AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        final AEItemKey circuit = mock(AEItemKey.class);
        final IPatternDetails pattern = mock(AEProcessingPattern.class);
        final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        final IEnergyService energy = mock(IEnergyService.class);
        final ExecutingCraftingJob.TaskProgress progress = new ExecutingCraftingJob.TaskProgress();
        final ExecutingCraftingJob job;
        final KeyCounter[] inputs;
        final ECOCraftingDispatchRequest request;
        final ECOProcessingPatternDispatcher dispatcher;

        Fixture(int crafts, boolean configureCircuit) {
            when(circuit.getId()).thenReturn(new ResourceLocation("gtceu", "programmed_circuit"));
            when(circuit.getType()).thenReturn(appeng.api.stacks.AEKeyType.items());
            var keys = configureCircuit ? List.<AEKey>of(iron, water, circuit) : List.<AEKey>of(iron, water);
            var patternInputs = new IPatternDetails.IInput[keys.size()];
            inputs = new KeyCounter[keys.size()];
            for (int i = 0; i < keys.size(); i++) {
                long amount = i == 0 ? 3 : i == 1 ? 500 : 1;
                var key = keys.get(i);
                patternInputs[i] = mock(IPatternDetails.IInput.class);
                when(patternInputs[i].getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(key, 1)});
                when(patternInputs[i].getMultiplier()).thenReturn(amount);
                inputs[i] = new KeyCounter();
                inputs[i].add(key, amount);
                inventory.insert(key, amount * (i == 2 ? 1 : crafts), Actionable.MODULATE);
            }
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.STONE));
            when(pattern.getInputs()).thenReturn(patternInputs);
            when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output, 2)});
            when(pattern.supportsPushInputsToExternalInventory()).thenReturn(true);
            doAnswer(call -> {
                IPatternDetails.PatternInputSink sink = call.getArgument(1);
                sink.pushInput(iron, 1);
                sink.pushInput(water, 500);
                sink.pushInput(iron, 2);
                if (configureCircuit) sink.pushInput(circuit, 1);
                return null;
            }).when(pattern).pushInputsToExternalInventory(any(), any());
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
            var plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(output, crafts * 2L));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            var link = mock(CraftingLink.class);
            when(link.getCraftingID()).thenReturn(UUID.randomUUID());
            try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
            }
            progress.value = crafts;
            job.tasks.put(pattern, progress);
            var outputs = new KeyCounter();
            outputs.add(output, 2);
            request = new ECOCraftingDispatchRequest(job, null, pattern, inputs, outputs, new KeyCounter(),
                    crafts, inventory, mock(Level.class));
            var accounting = new ECOCraftingDispatchAccounting(ignored -> {}, () -> {},
                    current -> new ECOCraftingJobContext(mock(ICraftingCPU.class), link.getCraftingID(),
                            current.finalOutput, crafts * 2L, current.remainingAmount), ignored -> {});
            dispatcher = new ECOProcessingPatternDispatcher(null,
                    new ECOCraftingEnergyTransaction(() -> {}, () -> 0), accounting);
            dispatcher.beginTick(0);
        }

        org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase gtlBuffer() {
            var provider = new org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase();
            provider.pattern = pattern;
            return provider;
        }

        org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine molecularAssembler() {
            var provider = new org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine();
            provider.pattern = pattern;
            return provider;
        }

        PatternProviderLogic gtlProvider() {
            var provider = mock(PatternProviderLogic.class, withSettings()
                    .extraInterfaces(PatternProviderLogicAccessor.class, IPatternProviderAutoExpand.class));
            when(((PatternProviderLogicAccessor) provider).neoecoae$getSendList()).thenReturn(List.of());
            when(((IPatternProviderAutoExpand) provider).gtlcore$getMaxPatternOperations(any(), anyLong()))
                    .thenAnswer(call -> call.getArgument(1));
            return provider;
        }

        ECOCraftingDispatchResult dispatch(ICraftingProvider provider,
                ECOCraftingProviderDispatcher.ECOCraftingNormalPush push) {
            return dispatcher.tryScaledDispatch(request, provider, 1, energy, ignored -> {}, push);
        }
    }
}
