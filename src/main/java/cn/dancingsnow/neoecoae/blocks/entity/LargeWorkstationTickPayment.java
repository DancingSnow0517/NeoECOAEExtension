package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/** Pays for one processing tick only when its coolant transaction can also complete. */
final class LargeWorkstationTickPayment {
    private static final double ENERGY_TOLERANCE = 0.001D;

    enum Result {
        PAID,
        POWER_MISSING,
        COOLANT_BLOCKED
    }

    private LargeWorkstationTickPayment() {
    }

    static Result commit(
        IEnergySource source,
        double required,
        BooleanSupplier consumeCoolant,
        DoubleConsumer refundEnergy
    ) {
        if (!Double.isFinite(required) || required < 0.0D) return Result.POWER_MISSING;

        double charged = required == 0.0D
            ? 0.0D : source.extractAEPower(required, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (!Double.isFinite(charged) || charged < 0.0D || charged + ENERGY_TOLERANCE < required
            || charged > required + ENERGY_TOLERANCE) {
            if (Double.isFinite(charged) && charged > 0.0D) refundEnergy.accept(charged);
            return Result.POWER_MISSING;
        }
        if (!consumeCoolant.getAsBoolean()) {
            if (charged > 0.0D) refundEnergy.accept(charged);
            return Result.COOLANT_BLOCKED;
        }
        return Result.PAID;
    }
}
