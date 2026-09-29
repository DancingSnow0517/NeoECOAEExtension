package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import java.math.BigDecimal;
import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/** Prepaid batch energy and refunds that could not yet return to the grid, measured in raw AE. */
public final class ECOBatchEnergyLedger {
    private BigDecimal pendingRefund = BigDecimal.ZERO;
    private final Runnable changed;

    public ECOBatchEnergyLedger() {
        this(() -> {});
    }

    public ECOBatchEnergyLedger(Runnable changed) {
        this.changed = changed;
    }

    @Nullable public Reservation reserve(IEnergyService service, double requested) {
        return reserve(service, requested, BigInteger.ONE);
    }

    @Nullable public Reservation reserve(IEnergyService service, double unitPower, BigInteger count) {
        try {
            refundPending(service);
        } catch (RuntimeException ignored) {
            // Pending credit remains in the persistent ledger for a later tick.
        }
        if (!Double.isFinite(unitPower) || unitPower < 0.0D || count.signum() < 0) return null;
        BigDecimal cost = new BigDecimal(unitPower).multiply(new BigDecimal(count));
        double requested = cost.doubleValue();
        if (!Double.isFinite(requested)) return null;
        if (new BigDecimal(requested).compareTo(cost) < 0) requested = Math.nextUp(requested);
        if (!Double.isFinite(requested)) return null;
        if (requested == 0.0D) return new Reservation(service, BigDecimal.ZERO, cost);
        double extracted = service.extractAEPower(requested, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (!Double.isFinite(extracted) || extracted < 0.0D || extracted > requested) {
            throw new IllegalStateException("Invalid batch energy extraction: " + extracted);
        }
        // IEnergyService already returns AE units; PowerMultiplier is part of the service call, not
        // an additional conversion for the refund path.
        BigDecimal raw = new BigDecimal(extracted);
        if (extracted != requested) {
            refund(service, raw);
            return null;
        }
        return new Reservation(service, raw, cost);
    }

    public void refundPending(IEnergyService service) {
        if (pendingRefund.signum() <= 0) return;
        double offered = Math.min(Double.MAX_VALUE, pendingRefund.doubleValue());
        if (new BigDecimal(offered).compareTo(pendingRefund) > 0) offered = Math.nextDown(offered);
        if (offered <= 0.0D) return;
        // Keep custody until injection reports its unaccepted suffix.
        double leftover = service.injectPower(offered, Actionable.MODULATE);
        if (!Double.isFinite(leftover) || leftover < 0.0D || leftover > offered) {
            throw new IllegalStateException("Invalid batch energy refund: " + leftover);
        }
        pendingRefund = pendingRefund.subtract(new BigDecimal(offered)).add(new BigDecimal(leftover));
        changed.run();
    }

    private void refund(IEnergyService service, BigDecimal amount) {
        pendingRefund = pendingRefund.add(amount);
        changed.run();
        try {
            refundPending(service);
        } catch (RuntimeException ignored) {
            // The CPU persists this balance and retries when the grid can accept it.
        }
    }

    public BigDecimal pendingRefund() {
        return pendingRefund;
    }

    public void writeToNBT(CompoundTag data) {
        data.putString("batchEnergyRefundExact", pendingRefund.toString());
    }

    public void readFromNBT(CompoundTag data) {
        BigDecimal restored = data.contains("batchEnergyRefundExact")
                ? new BigDecimal(data.getString("batchEnergyRefundExact"))
                : BigDecimal.valueOf(data.getDouble("batchEnergyRefund"));
        if (restored.signum() < 0) {
            throw new IllegalArgumentException("Invalid persisted batch energy refund");
        }
        pendingRefund = restored;
    }

    public final class Reservation {
        private final IEnergyService service;
        private final BigDecimal rawAmount;
        private final BigDecimal cost;
        private boolean settled;

        private Reservation(IEnergyService service, BigDecimal rawAmount, BigDecimal cost) {
            this.service = service;
            this.rawAmount = rawAmount;
            this.cost = cost;
        }

        public void commit() {
            ensureOpen();
            settled = true;
            BigDecimal credit = rawAmount.subtract(cost).max(BigDecimal.ZERO);
            if (credit.signum() > 0) refund(service, credit);
        }

        public void commitLinear(long accepted, long offered) {
            ensureOpen();
            if (offered <= 0L || accepted <= 0L || accepted > offered) {
                throw new IllegalArgumentException("Invalid energy settlement");
            }
            settled = true;
            BigDecimal acceptedCost =
                    cost.multiply(BigDecimal.valueOf(accepted)).divide(BigDecimal.valueOf(offered));
            BigDecimal credit = rawAmount.subtract(acceptedCost).max(BigDecimal.ZERO);
            if (credit.signum() > 0) refund(service, credit);
        }

        public void rollback() {
            if (settled) return;
            settled = true;
            refund(service, rawAmount);
        }

        private void ensureOpen() {
            if (settled) throw new IllegalStateException("Batch energy already settled");
        }
    }
}
