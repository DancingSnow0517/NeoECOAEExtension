package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import java.math.BigDecimal;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ECOCraftingEnergyTransactionTest {
    private final IEnergyService service = mock(IEnergyService.class);
    private final ECOCraftingEnergyTransaction ledger = new ECOCraftingEnergyTransaction(() -> {}, () -> 1);

    private void fullyPowered() {
        when(service.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
            .thenAnswer(call -> call.getArgument(0));
    }

    private BigDecimal credit(ECOCraftingEnergyTransaction target) {
        var tag = new CompoundTag();
        target.writeToNBT(tag);
        return tag.contains("prepaidEnergyCreditExact")
            ? new BigDecimal(tag.getString("prepaidEnergyCreditExact")) : BigDecimal.ZERO;
    }

    @Test void longMaxBatchRetainsRoundingCreditAcrossSaveAndConsumesItExactlyOnce() {
        fullyPowered();
        var reservation = ledger.reserve(service, 2, Long.MAX_VALUE);
        assertNotNull(reservation);
        reservation.commit();
        reservation.commit();
        assertEquals(0, new BigDecimal(2).compareTo(credit(ledger)));
        var tag = new CompoundTag();
        ledger.writeToNBT(tag);
        var restored = new ECOCraftingEnergyTransaction(() -> {}, () -> 2);
        restored.readFromNBT(tag);
        clearInvocations(service);
        var paid = restored.reserve(service, 2);
        assertNotNull(paid);
        paid.commit();
        verifyNoInteractions(service);
        assertEquals(0, credit(restored).signum());
    }

    @Test void rejectedBatchRefundsActualDebitOnlyOnce() {
        fullyPowered();
        var reservation = ledger.reserve(service, 2, Long.MAX_VALUE);
        assertNotNull(reservation);
        reservation.refund();
        reservation.refund();
        verify(service, times(1)).injectPower(0x1.0p64, Actionable.MODULATE);
        assertEquals(0, credit(ledger).signum());
    }

    @Test void insufficientEnergyRejectsAndRetainsUnacceptedRefund() {
        when(service.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
            .thenReturn(100.0);
        when(service.injectPower(100, Actionable.MODULATE)).thenReturn(100.0);
        assertNull(ledger.reserve(service, 2, Long.MAX_VALUE));
        assertEquals(0, BigDecimal.valueOf(100).compareTo(credit(ledger)));
    }

    @Test void partialAcceptanceNearLongMaxDoesNotRoundAwayOneRejectedCopy() {
        fullyPowered();
        var reservation = ledger.reserve(service, 2, Long.MAX_VALUE);
        assertNotNull(reservation);
        reservation.refundUnaccepted(Long.MAX_VALUE - 1, Long.MAX_VALUE);
        // Two for the rejected copy plus two originally rounded up by the interface.
        verify(service).injectPower(4, Actionable.MODULATE);
        assertEquals(0, credit(ledger).signum());
    }

    @Test void largeRetainedCreditDoesNotLoseSmallRefunds() {
        fullyPowered();
        when(service.injectPower(anyDouble(), eq(Actionable.MODULATE)))
            .thenAnswer(call -> call.getArgument(0));
        var first = ledger.reserve(service, 2, Long.MAX_VALUE);
        var second = ledger.reserve(service, 1);
        first.refund();
        second.refund();
        assertEquals(0, new BigDecimal(0x1.0p64).add(BigDecimal.ONE).compareTo(credit(ledger)));
        ledger.returnIdleCredit(service);
        assertEquals(0, new BigDecimal(0x1.0p64).add(BigDecimal.ONE).compareTo(credit(ledger)));
    }

    @Test void legacyCreditMigratesAndInvalidPowerIsRejected() {
        var tag = new CompoundTag();
        tag.putDouble("prepaidEnergyCredit", 1.5);
        ledger.readFromNBT(tag);
        assertEquals(0, new BigDecimal("1.5").compareTo(credit(ledger)));
        assertNull(ledger.reserve(service, Double.POSITIVE_INFINITY));
        assertNull(ledger.reserve(service, Double.NaN));
        assertNull(ledger.reserve(service, -1));
        verifyNoInteractions(service);
    }
}
