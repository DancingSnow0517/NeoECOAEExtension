package cn.dancingsnow.neoecoae.api.me;

import java.util.function.LongSupplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import cn.dancingsnow.neoecoae.NeoECOAE;

/**
 * Owns the CPU's energy reservation/refund ledger without exposing transaction details to dispatch orchestration.
 * The arithmetic and failure handling intentionally mirror the former CPU-local implementation.
 */
final class ECOCraftingEnergyTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private final Runnable markDirty;
    private final LongSupplier currentTick;
    private double prepaidEnergyCredit;
    private long lastAccountingFailureLogTick = Long.MIN_VALUE;
    private long lastIdleRefundAttemptTick = Long.MIN_VALUE;

    ECOCraftingEnergyTransaction(Runnable markDirty, LongSupplier currentTick) {
        this.markDirty = markDirty;
        this.currentTick = currentTick;
    }

    void readFromNBT(CompoundTag data) {
        double restored = data.getDouble("prepaidEnergyCredit");
        prepaidEnergyCredit = Double.isFinite(restored) && restored > 0.0D ? restored : 0.0D;
    }

    void writeToNBT(CompoundTag data) {
        if (prepaidEnergyCredit > 0.0D && Double.isFinite(prepaidEnergyCredit)) {
            data.putDouble("prepaidEnergyCredit", prepaidEnergyCredit);
        } else {
            data.remove("prepaidEnergyCredit");
        }
    }

    @Nullable
    Reservation reserve(IEnergyService energyService, double power) {
        if (power == 0.0D) return new Reservation(energyService, 0.0D, 0.0D);
        if (!Double.isFinite(power) || power < 0.0D) return null;

        double credit = Math.min(power, prepaidEnergyCredit);
        prepaidEnergyCredit -= credit;
        double networkPower = power - credit;
        if (networkPower <= 0.0D) {
            return new Reservation(energyService, credit, 0.0D);
        }
        try {
            double charged = energyService.extractAEPower(networkPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (!Double.isFinite(charged)
                    || charged < networkPower - 0.01D
                    || charged > networkPower + 0.01D) {
                restoreEnergyCredit(credit);
                if (Double.isFinite(charged) && charged > 0.0D) {
                    refundEnergyOrRetainCredit(energyService, charged);
                }
                logAccountingFailure(
                        "reservation charged " + charged + " of " + networkPower
                                + " after " + credit + " prepaid credit",
                        null);
                return null;
            }
            return new Reservation(energyService, credit, charged);
        } catch (RuntimeException failure) {
            restoreEnergyCredit(credit);
            logAccountingFailure("energy reservation failed", failure);
            return null;
        }
    }

    void returnIdleCredit(IEnergyService energyService) {
        if (prepaidEnergyCredit <= 0.0D || !Double.isFinite(prepaidEnergyCredit)) return;
        long tick = currentTick.getAsLong();
        long elapsed = tick - lastIdleRefundAttemptTick;
        if (lastIdleRefundAttemptTick != Long.MIN_VALUE && elapsed >= 0L && elapsed < 20L) return;
        lastIdleRefundAttemptTick = tick;
        double offered = prepaidEnergyCredit;
        try {
            double overflow = energyService.injectPower(offered, Actionable.MODULATE);
            if (!Double.isFinite(overflow) || overflow < -0.01D || overflow > offered + 0.01D) {
                logAccountingFailure("invalid idle-credit overflow " + overflow + " of " + offered, null);
                return;
            }
            double retained = Math.max(0.0D, overflow);
            if (retained != prepaidEnergyCredit) {
                prepaidEnergyCredit = retained;
                markDirty.run();
            }
        } catch (RuntimeException failure) {
            logAccountingFailure("idle-credit refund failed for " + offered + " energy", failure);
        }
    }

    private void refundEnergyOrRetainCredit(IEnergyService energyService, double amount) {
        if (amount <= 0.0D) return;
        try {
            double overflow = energyService.injectPower(amount, Actionable.MODULATE);
            if (!Double.isFinite(overflow) || overflow < -0.01D || overflow > amount + 0.01D) {
                restoreEnergyCredit(amount);
                logAccountingFailure("invalid refund overflow " + overflow + " of " + amount, null);
                return;
            }
            restoreEnergyCredit(Math.max(0.0D, overflow));
        } catch (RuntimeException failure) {
            restoreEnergyCredit(amount);
            logAccountingFailure("refund failed for " + amount + " energy", failure);
        }
    }

    private void restoreEnergyCredit(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        double updated = prepaidEnergyCredit + amount;
        prepaidEnergyCredit = Double.isFinite(updated) ? updated : Double.MAX_VALUE;
        markDirty.run();
    }

    private void logAccountingFailure(String reason, @Nullable RuntimeException failure) {
        long tick = currentTick.getAsLong();
        long elapsed = tick - lastAccountingFailureLogTick;
        if (lastAccountingFailureLogTick != Long.MIN_VALUE && elapsed >= 0L && elapsed < 1200L) return;
        lastAccountingFailureLogTick = tick;
        if (failure == null) {
            LOGGER.error("ECO crafting energy accounting anomaly: {}", reason);
        } else {
            LOGGER.error("ECO crafting energy accounting anomaly: {}", reason, failure);
        }
    }

    final class Reservation {
        private final IEnergyService energyService;
        private final double reservedCredit;
        private final double networkDebit;
        private boolean settled;

        private Reservation(IEnergyService energyService, double reservedCredit, double networkDebit) {
            this.energyService = energyService;
            this.reservedCredit = reservedCredit;
            this.networkDebit = networkDebit;
        }

        void commit() {
            settled = true;
            if (reservedCredit > 0.0D) markDirty.run();
        }

        void refund() {
            if (!settled) {
                settled = true;
                restoreEnergyCredit(reservedCredit);
                refundEnergyOrRetainCredit(energyService, networkDebit);
            }
        }

        void refundUnaccepted(long acceptedCopies, long offeredCopies) {
            if (offeredCopies <= 0L || acceptedCopies >= offeredCopies) {
                commit();
                return;
            }
            double fraction = Math.max(0.0D, Math.min(1.0D,
                    (double) acceptedCopies / (double) offeredCopies));
            settled = true;
            restoreEnergyCredit(reservedCredit * (1.0D - fraction));
            refundEnergyOrRetainCredit(energyService, networkDebit * (1.0D - fraction));
        }
    }
}
