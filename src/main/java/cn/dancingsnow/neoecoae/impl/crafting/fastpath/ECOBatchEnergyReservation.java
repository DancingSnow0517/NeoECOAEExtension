package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import org.jetbrains.annotations.Nullable;

/** A small transaction for energy charged before a provider takes ownership. */
public final class ECOBatchEnergyReservation {
    private final IEnergyService energyService;
    private final double amount;
    private boolean active;

    private ECOBatchEnergyReservation(IEnergyService energyService, double amount) {
        this.energyService = energyService;
        this.amount = amount;
        this.active = true;
    }

    @Nullable public static ECOBatchEnergyReservation tryReserve(
            IEnergyService energyService, double amount, boolean virtualCrafting) {
        if (virtualCrafting || amount == 0.0D) {
            return new ECOBatchEnergyReservation(energyService, 0.0D);
        }
        if (!Double.isFinite(amount) || amount < 0.0D) {
            return null;
        }
        double extracted = energyService.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (Double.isFinite(extracted) && extracted >= amount - 0.01D) {
            // Keep the actual debit so a tolerant partial extraction cannot be over-refunded.
            return new ECOBatchEnergyReservation(energyService, Math.max(0.0D, extracted));
        }
        if (Double.isFinite(extracted) && extracted > 0.0D) {
            energyService.injectPower(extracted, Actionable.MODULATE);
        }
        return null;
    }

    public void commit() {
        active = false;
    }

    @Nullable public RuntimeException refundSafely() {
        if (!active) {
            return null;
        }
        active = false;
        if (amount == 0.0D) {
            return null;
        }
        try {
            double remaining = energyService.injectPower(amount, Actionable.MODULATE);
            if (!Double.isFinite(remaining) || remaining > 0.01D) {
                return new IllegalStateException("Energy refund was only partially accepted: " + remaining);
            }
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }
}
