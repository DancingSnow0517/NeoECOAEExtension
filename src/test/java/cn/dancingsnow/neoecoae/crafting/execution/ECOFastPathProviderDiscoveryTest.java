package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECORecipeClassifier;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ECOFastPathProviderDiscoveryTest {
    interface ForeignProvider extends ICraftingProvider {}
    interface NativeProvider extends ForeignProvider, ECOFastPathDispatchProvider {}
    interface Adapter extends ECOParallelCraftingProvider, ECOFastPathDispatchProvider {}
    interface UnregisteredProvider extends ICraftingProvider {}

    private final ForeignProvider provider = mock(ForeignProvider.class);
    private final Adapter adapter = mock(Adapter.class);
    private final AEKey key = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final IPatternDetails pattern = mock(IPatternDetails.class, RETURNS_DEEP_STUBS);
    private final KeyCounter input = new KeyCounter();
    private final KeyCounter[] slots = {input};
    private final ECOFastPathFacade.Reservation reservation = mock(ECOFastPathFacade.Reservation.class);

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private void register() {
        ECOParallelCraftingProviders.register(ForeignProvider.class, ignored -> adapter);
    }

    @Test void registeredAdapterIsDiscoveredAndReused() {
        register();
        assertTrue(ECOFastPathFacade.supports(provider));
        assertSame(adapter, ECOFastPathFacade.resolveProvider(provider));
        assertSame(adapter, ECOParallelCraftingProviders.find(provider));
    }

    @Test void nativeContractTakesPrecedenceOverRegisteredAdapter() {
        register();
        var nativeProvider = mock(NativeProvider.class);
        assertTrue(ECOFastPathFacade.supports(nativeProvider));
        assertSame(nativeProvider, ECOFastPathFacade.resolveProvider(nativeProvider));
    }

    @Test void ordinaryOnlyAdapterDoesNotAdvertiseFastPath() {
        var ordinary = mock(ECOParallelCraftingProvider.class);
        ECOParallelCraftingProviders.register(ForeignProvider.class, ignored -> ordinary);
        assertSame(ordinary, ECOParallelCraftingProviders.find(provider));
        assertFalse(ECOFastPathFacade.supports(provider));
        assertNull(ECOFastPathFacade.resolveProvider(provider));
    }

    @Test void absentOrDecliningAdaptersLeaveProviderUnsupported() {
        var unregistered = mock(UnregisteredProvider.class);
        assertFalse(ECOFastPathFacade.supports(unregistered));
        assertNull(ECOFastPathFacade.resolveProvider(unregistered));
        ECOParallelCraftingProviders.register(ForeignProvider.class, ignored -> null);
        assertFalse(ECOFastPathFacade.supports(provider));
        assertNull(ECOFastPathFacade.resolveProvider(provider));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void registeredAdapterTransfersLongBatchOrRollsBack(boolean accepted) {
        register();
        long count = 1_000_000_000_000_000L;
        input.add(key, 9);
        var outputs = new KeyCounter();
        outputs.add(key, 1);
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.list.add(key, 9 * count);
        when(adapter.eco$prepareFastPath(any())).thenAnswer(call -> {
            assertEquals(9, input.get(key));
            assertEquals(9 * count, inventory.list.get(key));
            return new ECOFastPathDispatchProvider.Preparation(count, null, false, batch -> {
                assertEquals(count, batch.craftCount());
                assertEquals(9 * count, batch.inputTotal().getFirst().amount());
                assertEquals(count, batch.outputTotal().getFirst().amount());
                return accepted;
            });
        });
        var batch = ECOFastPathFacade.prepare(provider, pattern, slots, outputs, new KeyCounter(),
            inventory, count, 0, null, null, null);
        assertNotNull(batch);
        assertEquals(count, batch.craftCount());
        assertEquals(accepted, batch.submit(amount -> reservation));
        assertEquals(accepted ? 0 : 9 * count, inventory.list.get(key));
        assertEquals(9, input.get(key));
        verify(adapter, never()).eco$getAvailableParallelSlots();
        if (accepted) {
            verify(reservation).commit();
            verify(reservation, never()).refund();
        } else {
            verify(reservation).refund();
            verify(reservation, never()).commit();
        }
    }

    @Test void allocatedLongBatchUsesRegisteredAdapter() {
        register();
        long count = (long) Integer.MAX_VALUE + 1;
        input.add(key, 9);
        when(adapter.eco$prepareFastPath(any())).thenReturn(
            new ECOFastPathDispatchProvider.Preparation(count, null, false, batch -> {
                assertEquals(count, batch.craftCount());
                assertEquals(9 * count, batch.inputTotal().getFirst().amount());
                return true;
            }));
        var execution = execution();
        try (var introspection = mockStatic(ECOExtractedPatternExecution.class)) {
            introspection.when(() -> ECOExtractedPatternExecution.fromProviderPush(pattern, slots, null))
                .thenReturn(execution);
            var batch = ECOFastPathFacade.prepareAllocated(provider, pattern, slots, count, null, null);
            assertNotNull(batch);
            assertEquals(count, batch.craftCount());
            assertTrue(batch.submit(amount -> reservation));
            assertEquals(9, input.get(key));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void exactAllocatedBatchUsesRegisteredAdapterWithoutNarrowing(boolean accepted) {
        register();
        var count = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        var jobId = UUID.randomUUID();
        var dispatch = mock(java.util.function.BooleanSupplier.class);
        when(dispatch.getAsBoolean()).thenReturn(accepted);
        when(adapter.eco$prepareExactFastPath(any(), eq(count))).thenReturn(
            new ECOFastPathDispatchProvider.ExactPreparation(count, dispatch));
        var execution = execution();
        try (var introspection = mockStatic(ECOExtractedPatternExecution.class)) {
            introspection.when(() -> ECOExtractedPatternExecution.fromProviderPush(pattern, slots, null))
                .thenReturn(execution);
            var batch = ECOFastPathFacade.prepareExactAllocated(provider, pattern, slots, count, null, jobId);
            assertNotNull(batch);
            assertEquals(count, batch.craftCount());
            verifyNoInteractions(dispatch);
            assertEquals(accepted, batch.submit());
            assertThrows(IllegalStateException.class, batch::submit);
            verify(dispatch).getAsBoolean();
        }
    }

    private ECOExtractedPatternExecution execution() {
        var execution = mock(ECOExtractedPatternExecution.class);
        when(execution.canUseFastPath()).thenReturn(true);
        when(execution.fastPathType()).thenReturn(ECORecipeClassifier.Type.NORMAL);
        when(execution.arithmeticBatchLimit()).thenReturn(Long.MAX_VALUE / 9);
        when(execution.inputItems()).thenReturn(List.of(new GenericStack(key, 9)));
        when(execution.expectedOutputs()).thenReturn(List.of(new GenericStack(key, 1)));
        return execution;
    }
}
