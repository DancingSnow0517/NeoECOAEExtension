package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;

import java.util.function.LongSupplier;
import java.math.BigDecimal;

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
 * Counts and credits remain exact; only the AE2 energy interface uses doubles.
 */
final class ECOCraftingEnergyTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private final Runnable markDirty;
    private final LongSupplier currentTick;
    // Exact credit includes interface rounding and refunds the network could not accept.
    private BigDecimal prepaidEnergyCredit = BigDecimal.ZERO;
    private long lastAccountingFailureLogTick = Long.MIN_VALUE;
    private long lastIdleRefundAttemptTick = Long.MIN_VALUE;

    ECOCraftingEnergyTransaction(Runnable markDirty, LongSupplier currentTick) {
        this.markDirty = markDirty;
        this.currentTick = currentTick;
    }

    void readFromNBT(CompoundTag data) {
        prepaidEnergyCredit = BigDecimal.ZERO;
        if (data.contains("prepaidEnergyCreditExact", net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                prepaidEnergyCredit = new BigDecimal(data.getString("prepaidEnergyCreditExact"))
                    .max(BigDecimal.ZERO);
            } catch (NumberFormatException invalid) {
                LOGGER.error("Invalid exact crafting energy credit", invalid);
            }
        } else {
            double restored = data.getDouble("prepaidEnergyCredit");
            if (Double.isFinite(restored) && restored > 0) prepaidEnergyCredit = decimal(restored);
        }
    }

    void writeToNBT(CompoundTag data) {
        data.remove("prepaidEnergyCredit");
        if (prepaidEnergyCredit.signum() > 0) {
            data.putString("prepaidEnergyCreditExact", prepaidEnergyCredit.toString());
        } else {
            data.remove("prepaidEnergyCreditExact");
        }
    }

    @Nullable
    Reservation reserve(IEnergyService service, double power) {
        return reserve(service, power, 1L);
    }

    @Nullable
    Reservation reserve(IEnergyService service, double perCraft, long count) {
        return reserve(service, perCraft, java.math.BigInteger.valueOf(count));
    }

    @Nullable
    Reservation reserve(IEnergyService service, double perCraft, java.math.BigInteger count) {
        if (!Double.isFinite(perCraft) || perCraft < 0 || count.signum() < 0) return null;
        var power = new BigDecimal(perCraft).multiply(new BigDecimal(count));
        var credit = power.min(prepaidEnergyCredit);
        var required = power.subtract(credit);
        double request = required.doubleValue();
        if (!Double.isFinite(request)) return null;
        if (decimal(request).compareTo(required) < 0) request = Math.nextUp(request);
        if (!Double.isFinite(request)) return null;
        double charged = 0;
        if (request > 0) {
            try {
                charged = service.extractAEPower(request, Actionable.MODULATE, PowerMultiplier.CONFIG);
            } catch (RuntimeException failure) {
                logAccountingFailure("energy reservation failed", failure);
                return null;
            }
            // Do not forgive an underpayment with a growing floating-point tolerance.
            if (!Double.isFinite(charged) || charged != request) {
                if (Double.isFinite(charged) && charged > 0) refundEnergyOrRetainCredit(service, decimal(charged));
                logAccountingFailure("reservation charged " + charged + " of " + request, null);
                return null;
            }
        }
        prepaidEnergyCredit = prepaidEnergyCredit.subtract(credit);
        if (credit.signum() > 0) markDirty.run();
        return new Reservation(service, credit, decimal(charged), power);
    }

    static long maxSafeCrafts(double perCraftEnergy) {
        return cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper.maxEnergySafeCrafts(perCraftEnergy);
    }

    void returnIdleCredit(IEnergyService service) {
        if (prepaidEnergyCredit.signum() <= 0) return;
        long tick = currentTick.getAsLong();
        long elapsed = tick - lastIdleRefundAttemptTick;
        if (lastIdleRefundAttemptTick != Long.MIN_VALUE && elapsed >= 0 && elapsed < 20) return;
        lastIdleRefundAttemptTick = tick;
        var refund = prepaidEnergyCredit;
        prepaidEnergyCredit = BigDecimal.ZERO;
        markDirty.run();
        refundEnergyOrRetainCredit(service, refund);
    }

    private static BigDecimal decimal(double value) {
        return new BigDecimal(value);
    }

    private void refundEnergyOrRetainCredit(IEnergyService service, BigDecimal amount) {
        if (amount.signum() <= 0) return;
        // Round refunds down; retain every unrepresentable unit in the exact ledger.
        double offered = Math.min(Double.MAX_VALUE, amount.doubleValue());
        if (decimal(offered).compareTo(amount) > 0) offered = Math.nextDown(offered);
        if (offered <= 0) { restoreEnergyCredit(amount); return; }
        try {
            double overflow = service.injectPower(offered, Actionable.MODULATE);
            if (!Double.isFinite(overflow) || overflow < 0 || overflow > offered) {
                restoreEnergyCredit(amount);
                logAccountingFailure("invalid refund overflow " + overflow + " of " + offered, null);
                return;
            }
            restoreEnergyCredit(amount.subtract(decimal(offered)).add(decimal(overflow)));
        } catch (RuntimeException failure) {
            restoreEnergyCredit(amount);
            logAccountingFailure("refund failed for " + amount + " energy", failure);
        }
    }

    private void restoreEnergyCredit(BigDecimal amount) {
        if (amount.signum() <= 0) return;
        prepaidEnergyCredit = prepaidEnergyCredit.add(amount);
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

    final class Reservation implements cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchExecutor.LinearEnergy {
        private final IEnergyService energyService;
        private final BigDecimal reservedCredit;
        private final BigDecimal networkDebit;
        private final BigDecimal exactCost;
        private boolean settled;

        private Reservation(IEnergyService service, BigDecimal credit,
                BigDecimal debit, BigDecimal cost) {
            energyService = service;
            reservedCredit = credit;
            networkDebit = debit;
            exactCost = cost;
        }

        public void commit() {
            if (settled) return;
            settled = true;
            restoreEnergyCredit(reservedCredit.add(networkDebit).subtract(exactCost));
        }

        public void refund() {
            if (settled) return;
            settled = true;
            restoreEnergyCredit(reservedCredit);
            refundEnergyOrRetainCredit(energyService, networkDebit);
        }

        public void refundUnaccepted(long acceptedCopies, long offeredCopies) {
            if (settled) return;
            if (offeredCopies <= 0 || acceptedCopies >= offeredCopies) { commit(); return; }
            if (acceptedCopies <= 0) { refund(); return; }
            // Batch reservations originate from per-copy cost * offeredCopies, so division is exact.
            var acceptedCost = exactCost.divide(BigDecimal.valueOf(offeredCopies))
                .multiply(BigDecimal.valueOf(acceptedCopies));
            settled = true;
            refundEnergyOrRetainCredit(energyService, reservedCredit.add(networkDebit).subtract(acceptedCost));
        }
    }
}
