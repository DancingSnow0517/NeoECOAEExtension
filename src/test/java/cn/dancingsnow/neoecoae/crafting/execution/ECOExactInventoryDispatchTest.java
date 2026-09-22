package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;

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
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECOExactInventoryDispatchTest {
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private final AEKey input = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final AEKey output = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final ECOExactInventory inventory = new ECOExactInventory(ignored -> {});
    private final IEnergyService energy = mock(IEnergyService.class);
    interface Provider extends ICraftingProvider, ECOFastPathDispatchProvider {}

    private ECOFastPathFacade.PreparedBatch prepare(boolean exactProvider, java.util.function.Predicate<ECOFastPathDispatchProvider.Batch> push) {
        var provider = mock(Provider.class);
        when(provider.eco$prepareFastPath(any())).thenReturn(
            new ECOFastPathDispatchProvider.Preparation(Long.MAX_VALUE, null, false, push, exactProvider));
        when(energy.extractAEPower(anyDouble(), any(), eq(PowerMultiplier.CONFIG)))
            .thenAnswer(call -> call.getArgument(0));
        var slots = new KeyCounter[9];
        for (int i = 0; i < 9; i++) { slots[i] = new KeyCounter(); slots[i].add(input, 1); }
        var outputs = new KeyCounter(); outputs.add(output, 1);
        return ECOFastPathFacade.prepare(provider, mock(IPatternDetails.class, RETURNS_DEEP_STUBS), slots, outputs,
            new KeyCounter(), inventory, Long.MAX_VALUE, 9, energy, null, null, inventory.isEnabled());
    }

    private void fill() {
        inventory.setEnabled(true);
        for (int i = 0; i < 9; i++) inventory.insert(input, Long.MAX_VALUE, Actionable.MODULATE);
    }

    @Test void nineToOneLongMaxBatchDebitsNineLongMaxAndUsesNativeBigIntegerReceipt() {
        fill();
        var batch = prepare(true, b -> {
            assertEquals(Long.MAX_VALUE, b.craftCount());
            assertEquals(MAX.multiply(BigInteger.valueOf(9)), b.exactInputTotal().get(input));
            assertThrows(ArithmeticException.class, b::inputTotal);
            return true;
        });
        assertNotNull(batch);
        assertEquals(Long.MAX_VALUE, batch.craftCount());
        var ledger = new ECOCraftingEnergyTransaction(() -> {}, () -> 1);
        assertTrue(batch.submit(ignored -> ledger.reserve(energy, 9, batch.craftCount())));
        assertEquals(BigInteger.ZERO, inventory.amount(input));
        assertEquals(Long.MAX_VALUE, batch.outputs().getFirst().amount());
        assertTrue(inventory.list.isEmpty());
    }

    @Test void rejectAndExceptionRestoreTheEntireBigIntegerDebit() {
        for (boolean throwsFailure : new boolean[]{false, true}) {
            inventory.clear(); fill();
            var batch = prepare(true, b -> {
                if (throwsFailure) throw new IllegalArgumentException("Rejected");
                return false;
            });
            var reservation = mock(ECOFastPathFacade.Reservation.class);
            if (throwsFailure) assertThrows(IllegalArgumentException.class, () -> batch.submit(ignored -> reservation));
            else assertFalse(batch.submit(ignored -> reservation));
            assertEquals(MAX.multiply(BigInteger.valueOf(9)), inventory.amount(input));
            verify(reservation).refund();
            assertThrows(IllegalStateException.class, () -> batch.submit(ignored -> reservation));
        }
    }

    @Test void ambiguousAcceptanceDoesNotRefundOwnedMaterials() {
        fill();
        var batch = prepare(true, b -> { throw new ECOIndeterminateBatchException("uncertain", null); });
        var reservation = mock(ECOFastPathFacade.Reservation.class);
        assertThrows(ECOIndeterminateBatchException.class, () -> batch.submit(ignored -> reservation));
        assertEquals(BigInteger.ZERO, inventory.amount(input));
        verify(reservation).commit();
        verify(reservation, never()).refund();
    }

    @Test void ordinaryJobsAndLegacyProvidersKeepTheirLongInputLimit() {
        inventory.insert(input, Long.MAX_VALUE, Actionable.MODULATE);
        assertFalse(inventory.isEnabled());
        assertEquals(Long.MAX_VALUE / 9, prepare(true, b -> true).craftCount());
        inventory.clear(); fill();
        assertEquals(Long.MAX_VALUE / 9, prepare(false, b -> true).craftCount());
    }

    @Test void exactBatchUsesActualStockAndRejectsAnInventoryRaceAtomically() {
        fill();
        var batch = prepare(true, b -> fail("Must not submit after materials disappeared"));
        inventory.extract(input, 1, Actionable.MODULATE);
        var before = inventory.amount(input);
        var reservation = mock(ECOFastPathFacade.Reservation.class);
        assertFalse(batch.submit(ignored -> reservation));
        assertEquals(before, inventory.amount(input));
        verify(reservation).refund();
        assertEquals(Long.MAX_VALUE - 1, prepare(true, b -> true).craftCount());
    }

    @Test void cancellingRetainsOverflowUntilEveryWindowIsDrained() {
        fill(); inventory.setEnabled(false);
        assertTrue(inventory.isEnabled());
        for (int i = 0; i < 9; i++) assertEquals(Long.MAX_VALUE, inventory.extract(input, Long.MAX_VALUE, Actionable.MODULATE));
        assertTrue(inventory.list.isEmpty());
        inventory.setEnabled(false);
        assertFalse(inventory.isEnabled());
    }

    @Test void multikeyDebitIsAtomicAndSmallChangesSurviveHugeBalances() {
        fill();
        var before = inventory.amount(input);
        assertFalse(inventory.debit(Map.of(input, before, output, BigInteger.ONE)));
        assertEquals(before, inventory.amount(input));
        inventory.insert(input, 3, Actionable.MODULATE);
        inventory.extract(input, 2, Actionable.MODULATE);
        assertEquals(before.add(BigInteger.ONE), inventory.amount(input));
        assertEquals(Long.MAX_VALUE, inventory.extract(input, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(before.add(BigInteger.ONE), inventory.amount(input));
    }
    @Test void exactAndLegacyNbtRoundTripWithoutDuplicatingTheCompatibilityWindow() {
        fill(); inventory.insert(input, 7, Actionable.MODULATE);
        var registry = mock(net.minecraft.core.HolderLookup.Provider.class);
        when(input.toTagGeneric(registry)).thenAnswer(call -> new net.minecraft.nbt.CompoundTag());
        try (var keys = mockStatic(AEKey.class)) {
            keys.when(() -> AEKey.fromTagGeneric(eq(registry), any())).thenReturn(input);
            var saved = inventory.writeToNBT(registry);
            assertEquals(Long.MAX_VALUE, saved.getCompound(0).getLong("#"));
            var restored = new ECOExactInventory(ignored -> {});
            restored.readFromNBT(saved, registry);
            assertEquals(inventory.amount(input), restored.amount(input));
            assertTrue(restored.isEnabled());
            assertEquals(Long.MAX_VALUE, restored.list.get(input));
            // Repeated load replaces rather than adds the saved ledger.
            restored.readFromNBT(saved, registry);
            assertEquals(inventory.amount(input), restored.amount(input));
            for (int i = 0; i < 9; i++) restored.extract(input, Long.MAX_VALUE, Actionable.MODULATE);
            assertEquals(BigInteger.valueOf(7), restored.amount(input));
            var legacy = new net.minecraft.nbt.ListTag();
            var entry = new net.minecraft.nbt.CompoundTag(); entry.putLong("#", 17); legacy.add(entry);
            restored.readFromNBT(legacy, registry);
            assertEquals(BigInteger.valueOf(17), restored.amount(input));
            assertFalse(restored.writeToNBT(registry).getCompound(0).contains("ecoExactAmount"));
            saved.getCompound(0).putString("ecoExactAmount", "invalid");
            assertThrows(NumberFormatException.class, () -> restored.readFromNBT(saved, registry));
            assertEquals(BigInteger.valueOf(17), restored.amount(input));
        }
    }

    @Test void notificationFailureCannotLoseAnExactDebit() {
        var failing = new ECOExactInventory(ignored -> { throw new IllegalStateException("notification"); });
        failing.setEnabled(true);
        // Seed the compatibility window without sending a notification.
        failing.list.set(input, 20);
        assertThrows(IllegalStateException.class, () -> failing.debit(Map.of(input, BigInteger.TEN)));
        assertEquals(BigInteger.valueOf(20), failing.amount(input));
    }

    @Test void protectedSeedIsSubtractedBeforeSaturatingThePreview() {
        fill();
        var pattern = mock(IPatternDetails.class, RETURNS_DEEP_STUBS);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        var preview = new ECOCraftingInputPreview(inventory, pattern, Map.of(input, Long.MAX_VALUE));
        assertEquals(9, preview.extract(input, 9, Actionable.MODULATE));
        assertEquals(MAX.multiply(BigInteger.valueOf(9)), inventory.amount(input));
    }

}
