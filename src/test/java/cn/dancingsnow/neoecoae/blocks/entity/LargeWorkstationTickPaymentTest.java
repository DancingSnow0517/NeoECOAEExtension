package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LargeWorkstationTickPaymentTest {
    @Test
    void partialPowerChargeIsRefundedWithoutConsumingCoolant() {
        AtomicReference<Double> refund = new AtomicReference<>(0.0D);
        AtomicInteger coolantCalls = new AtomicInteger();
        IEnergySource source = (amount, action, multiplier) -> action == Actionable.MODULATE ? 40.0D : amount;

        var result = LargeWorkstationTickPayment.commit(source, 100.0D, () -> {
            coolantCalls.incrementAndGet();
            return true;
        }, refund::set);

        assertEquals(LargeWorkstationTickPayment.Result.POWER_MISSING, result);
        assertEquals(40.0D, refund.get());
        assertEquals(0, coolantCalls.get());
    }

    @Test
    void failedCoolantCommitRefundsFullPowerCharge() {
        AtomicReference<Double> refund = new AtomicReference<>(0.0D);
        IEnergySource source = (amount, action, multiplier) -> amount;

        var result = LargeWorkstationTickPayment.commit(source, 100.0D, () -> false, refund::set);

        assertEquals(LargeWorkstationTickPayment.Result.COOLANT_BLOCKED, result);
        assertEquals(100.0D, refund.get());
    }

    @Test
    void successfulTickPaysOnceWithoutRefund() {
        AtomicReference<Double> refund = new AtomicReference<>(0.0D);
        AtomicInteger coolantCalls = new AtomicInteger();
        IEnergySource source = (amount, action, multiplier) -> amount;

        var result = LargeWorkstationTickPayment.commit(source, 100.0D, () -> {
            coolantCalls.incrementAndGet();
            return true;
        }, refund::set);

        assertEquals(LargeWorkstationTickPayment.Result.PAID, result);
        assertEquals(0.0D, refund.get());
        assertEquals(1, coolantCalls.get());
    }
}
