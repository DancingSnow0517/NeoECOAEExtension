package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ECOFastPathFacadeTest {
    @Test void unlimitedProviderStillReceivesOnlyLocallySafeLongBatches() {
        var provider = mock(Provider.class);
        when(provider.eco$prepareFastPath(any())).thenReturn(
            new ECOFastPathDispatchProvider.Preparation(Long.MAX_VALUE, null, false, batch -> true));
        inventory.list.set(key, Long.MAX_VALUE);
        var input = new KeyCounter();
        input.add(key, 1);
        var output = new KeyCounter();
        output.add(key, 2);
        var batch = ECOFastPathFacade.prepare(provider, mock(IPatternDetails.class), new KeyCounter[]{input},
            output, new KeyCounter(), inventory, Long.MAX_VALUE, 0, null, null, null);
        assertNotNull(batch);
        assertEquals(Long.MAX_VALUE / 2, batch.craftCount());
        assertEquals(Long.MAX_VALUE - 1, batch.outputs().getFirst().amount());
        assertTrue(batch.submit(amount -> reservation));
        assertEquals(Long.MAX_VALUE - Long.MAX_VALUE / 2, inventory.list.get(key));
        verify(reservation).commit();
    }
    @Test void poweredLongMaxBatchCommitsOrRollsBackWithoutTruncatingItsCount() {
        for (boolean accepted : new boolean[]{true, false}) {
            var provider = mock(Provider.class);
            when(provider.eco$prepareFastPath(any())).thenReturn(
                new ECOFastPathDispatchProvider.Preparation(Long.MAX_VALUE, null, false, delivery -> {
                    assertEquals(Long.MAX_VALUE, delivery.craftCount());
                    assertEquals(Long.MAX_VALUE, delivery.inputTotal().getFirst().amount());
                    return accepted;
                }));
            when(energy.extractAEPower(anyDouble(), any(), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(call -> call.getArgument(0));
            inventory.list.set(key, Long.MAX_VALUE);
            var input = new KeyCounter();
            input.add(key, 1);
            var output = new KeyCounter();
            output.add(key, 1);
            var batch = ECOFastPathFacade.prepare(provider, mock(IPatternDetails.class), new KeyCounter[]{input},
                output, new KeyCounter(), inventory, Long.MAX_VALUE, 2, energy, null, null);
            assertNotNull(batch);
            assertEquals(Long.MAX_VALUE, batch.craftCount());
            var ledger = new ECOCraftingEnergyTransaction(() -> {}, () -> 1);
            assertEquals(accepted, batch.submit(ignored -> ledger.reserve(energy, 2, batch.craftCount())));
            assertEquals(accepted ? 0 : Long.MAX_VALUE, inventory.list.get(key));
        }
    }

    interface Provider extends ICraftingProvider, ECOFastPathDispatchProvider {}

    private final AEKey key = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
    private final IEnergyService energy = mock(IEnergyService.class);
    private final ECOFastPathFacade.Reservation reservation = mock(ECOFastPathFacade.Reservation.class);

    private ECOFastPathFacade.PreparedBatch prepare(Predicate<ECOFastPathDispatchProvider.Batch> push) {
        inventory.list.add(key, 20);
        var provider = mock(Provider.class);
        when(provider.eco$prepareFastPath(any())).thenReturn(
            new ECOFastPathDispatchProvider.Preparation(8, null, false, push));
        when(energy.extractAEPower(anyDouble(), eq(Actionable.SIMULATE), eq(PowerMultiplier.CONFIG)))
            .thenAnswer(call -> call.getArgument(0));
        var input = new KeyCounter();
        input.add(key, 2);
        var output = new KeyCounter();
        output.add(key, 3);
        var batch = ECOFastPathFacade.prepare(provider, mock(IPatternDetails.class), new KeyCounter[]{input},
            output, new KeyCounter(), inventory, 7, 4, energy, null, null);
        assertNotNull(batch);
        return batch;
    }

    @Test void externalCpuReceivesBoundedTotalsAndCanSubmitOnlyOnce() {
        var batch = prepare(delivery -> delivery.craftCount() == 7 && delivery.inputTotal().getFirst().amount() == 14);
        assertEquals(7, batch.craftCount());
        assertEquals(21, batch.outputs().getFirst().amount());
        assertEquals(28, batch.power());
        assertTrue(batch.submit(amount -> reservation));
        assertEquals(6, inventory.list.get(key));
        assertThrows(IllegalStateException.class, () -> batch.submit(amount -> reservation));
        assertEquals(6, inventory.list.get(key));
        verify(reservation).commit();
        verify(reservation, never()).refund();
    }

    @Test void rejectionRestoresExactInputsAndEnergy() {
        var batch = prepare(delivery -> false);
        assertFalse(batch.submit(amount -> reservation));
        assertEquals(20, inventory.list.get(key));
        verify(reservation).refund();
        verify(reservation, never()).commit();
    }

    @Test void providerExceptionRestoresOnceAndCannotReplay() {
        var batch = prepare(delivery -> { throw new IllegalArgumentException("rejected"); });
        assertThrows(IllegalArgumentException.class, () -> batch.submit(amount -> reservation));
        assertThrows(IllegalStateException.class, () -> batch.submit(amount -> reservation));
        assertEquals(20, inventory.list.get(key));
        verify(reservation).refund();
    }

    @Test void ambiguousAcceptanceRetainsInputsAndEnergy() {
        var batch = prepare(delivery -> { throw new ECOIndeterminateBatchException("unknown", null); });
        assertThrows(ECOIndeterminateBatchException.class, () -> batch.submit(amount -> reservation));
        assertEquals(6, inventory.list.get(key));
        verify(reservation).commit();
        verify(reservation, never()).refund();
    }

    @Test void failedReservationDoesNotTouchInputs() {
        var batch = prepare(delivery -> fail("Provider must not be called"));
        assertFalse(batch.submit(amount -> null));
        assertEquals(20, inventory.list.get(key));
    }

    @Test void inventoryChangeAfterPreparationRefundsEnergy() {
        var batch = prepare(delivery -> fail("Provider must not be called"));
        inventory.list.set(key, 3);
        assertFalse(batch.submit(amount -> reservation));
        assertEquals(3, inventory.list.get(key));
        verify(reservation).refund();
    }

    @Test void realEnergyLedgerRefundsNetworkDebitOnRejection() {
        var batch = prepare(delivery -> false);
        when(energy.extractAEPower(28, Actionable.MODULATE, PowerMultiplier.CONFIG)).thenReturn(28.0);
        var ledger = new ECOCraftingEnergyTransaction(() -> {}, () -> 1);
        assertFalse(batch.submit(amount -> ledger.reserve(energy, amount)));
        verify(energy).injectPower(28, Actionable.MODULATE);
        assertEquals(20, inventory.list.get(key));
    }

    @Test void allocatedDeliveryDoesNotDebitExternalCpuInventoryAgain() {
        var provider = mock(Provider.class);
        when(provider.eco$prepareFastPath(any())).thenReturn(
            new ECOFastPathDispatchProvider.Preparation(8, null, false,
                batch -> batch.inputTotal().getFirst().amount() == 14));
        var execution = mock(cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution.class);
        when(execution.canUseFastPath()).thenReturn(true);
        when(execution.fastPathType()).thenReturn(cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECORecipeClassifier.Type.NORMAL);
        when(execution.arithmeticBatchLimit()).thenReturn(100L);
        when(execution.inputItems()).thenReturn(java.util.List.of(new appeng.api.stacks.GenericStack(key, 2)));
        when(execution.expectedOutputs()).thenReturn(java.util.List.of(new appeng.api.stacks.GenericStack(key, 3)));
        var pattern = mock(IPatternDetails.class);
        var input = new KeyCounter();
        input.add(key, 2);
        var slots = new KeyCounter[]{input};
        inventory.list.add(key, 6); // The external CPU already allocated 14 of its original 20 inputs.
        try (var introspection = mockStatic(cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution.class)) {
            introspection.when(() -> cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution
                .fromProviderPush(pattern, slots, null)).thenReturn(execution);
            var batch = ECOFastPathFacade.prepareAllocated(provider, pattern, slots, 7, null, null);
            assertNotNull(batch);
            assertTrue(batch.submit(amount -> reservation));
            assertEquals(6, inventory.list.get(key));
            assertEquals(2, input.get(key));
            when(execution.expectedContainerItems()).thenReturn(java.util.List.of(new appeng.api.stacks.GenericStack(key, 1)));
            assertNull(ECOFastPathFacade.prepareAllocated(provider, pattern, slots, 7, null, null));
        }
    }
}
