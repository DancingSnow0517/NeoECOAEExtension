package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** A small transaction for energy charged before a provider takes ownership. */
public final class ECOBatchEnergyReservation {
    private static final double ENERGY_TOLERANCE = 0.01D;
    private static final AtomicLong NEXT_RESERVATION_ID = new AtomicLong();
    private static final List<PendingRefund> PENDING_REFUNDS = new ArrayList<>();

    private final IEnergyService energyService;
    private final long reservationId;
    private final double requestedAmount;
    private final double extractedAmount;
    private final boolean fullyReserved;
    private double committedAmount;
    private double refundRequested;
    private double refundAccepted;
    private double pendingRefund;
    private boolean active;

    private ECOBatchEnergyReservation(
            IEnergyService energyService,
            double requestedAmount,
            double extractedAmount,
            boolean fullyReserved) {
        this.energyService = energyService;
        this.reservationId = NEXT_RESERVATION_ID.incrementAndGet();
        this.requestedAmount = requestedAmount;
        this.extractedAmount = extractedAmount;
        this.fullyReserved = fullyReserved;
        this.active = true;
    }

    @Nullable
    public static ECOBatchEnergyReservation tryReserve(
            IEnergyService energyService, double amount, boolean virtualCrafting) {
        retryPendingRefunds(energyService);
        if (virtualCrafting || amount == 0.0D) {
            return new ECOBatchEnergyReservation(energyService, 0.0D, 0.0D, true);
        }
        if (!Double.isFinite(amount) || amount < 0.0D) {
            return null;
        }
        double extracted = energyService.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (!Double.isFinite(extracted) || extracted < 0.0D) {
            return null;
        }
        // Keep the actual debit. A near-complete extraction is accepted by AE2's normal tolerance,
        // while a smaller extraction remains a live reservation so the caller can refund it and
        // decide whether the operation may proceed.
        return new ECOBatchEnergyReservation(
            energyService,
            amount,
            extracted,
            extracted >= amount - ENERGY_TOLERANCE
        );
    }

    /** Returns whether the requested amount, rather than only a partial debit, is reserved. */
    public boolean isFullyReserved() {
        return fullyReserved;
    }

    public void commit() {
        if (!active || pendingRefund > ENERGY_TOLERANCE) {
            return;
        }
        committedAmount = extractedAmount;
        pendingRefund = 0.0D;
        active = false;
        removePendingRefund(this);
    }

    /** Commits only the consumed portion and returns any excess reservation to the network. */
    @Nullable
    public RuntimeException commitConsumed(double consumedAmount) {
        if (!active && pendingRefund <= ENERGY_TOLERANCE) {
            return null;
        }
        if (!active) {
            return incompleteRefund(
                "commit attempted while a previous refund is still pending", null
            );
        }
        if (!Double.isFinite(consumedAmount) || consumedAmount < 0.0D) {
            IllegalArgumentException invalid =
                new IllegalArgumentException("Invalid consumed energy amount: " + consumedAmount);
            RuntimeException refundFailure = refundSafely();
            if (refundFailure != null) {
                invalid.addSuppressed(refundFailure);
            }
            return invalid;
        }
        committedAmount = Math.min(extractedAmount, consumedAmount);
        double refund = Math.max(0.0D, extractedAmount - committedAmount);
        if (refund <= ENERGY_TOLERANCE) {
            // Treat the tolerance remainder as consumed. This keeps the conservation fields exact
            // even when AE2 returns a result a few millipowers below the requested amount.
            committedAmount = extractedAmount;
            active = false;
            pendingRefund = 0.0D;
            removePendingRefund(this);
            return null;
        }
        active = false;
        pendingRefund = refund;
        return settleRefund(refund, "excess reservation refund");
    }

    @Nullable
    public RuntimeException refundSafely() {
        if (!active && pendingRefund <= ENERGY_TOLERANCE) {
            return null;
        }
        if (active) {
            committedAmount = 0.0D;
            pendingRefund = extractedAmount;
            active = false;
        }
        if (pendingRefund <= ENERGY_TOLERANCE) {
            committedAmount = extractedAmount;
            active = false;
            removePendingRefund(this);
            return null;
        }
        return settleRefund(pendingRefund, "reservation refund");
    }

    /**
     * Retries refunds that AE2 could not accept during the original rollback. A failed injection
     * is not treated as a reason to forget the energy: the remainder stays in this queue until a
     * later CPU tick can return it. Strong references are intentional so unloading a grid cannot
     * silently discard the energy debt.
     */
    public static void retryPendingRefunds(@Nullable IEnergyService energyService) {
        if (energyService == null) {
            return;
        }
        List<PendingRefund> candidates = new ArrayList<>();
        synchronized (PENDING_REFUNDS) {
            for (PendingRefund pending : PENDING_REFUNDS) {
                if (pending.energyService == energyService && pending.amount > ENERGY_TOLERANCE) {
                    candidates.add(pending);
                }
            }
        }

        for (PendingRefund pending : candidates) {
            double requested = pending.amount;
            double remaining;
            try {
                remaining = energyService.injectPower(requested, Actionable.MODULATE);
            } catch (RuntimeException ignored) {
                continue;
            }

            ECOBatchEnergyReservation reservation = pending.reservation;
            if (!isValidRemainder(remaining, requested)) {
                continue;
            }
            double normalizedRemaining = Math.min(requested, Math.max(0.0D, remaining));
            if (normalizedRemaining <= ENERGY_TOLERANCE) {
                normalizedRemaining = 0.0D;
            }
            double accepted = requested - normalizedRemaining;
            if (reservation != null) {
                reservation.recordRefundAttempt(requested, accepted, normalizedRemaining);
            }
            synchronized (PENDING_REFUNDS) {
                if (normalizedRemaining <= ENERGY_TOLERANCE) {
                    PENDING_REFUNDS.remove(pending);
                } else {
                    pending.amount = normalizedRemaining;
                }
            }
        }
    }

    public long reservationId() {
        return reservationId;
    }

    public double requestedAmount() {
        return requestedAmount;
    }

    public double extractedAmount() {
        return extractedAmount;
    }

    public double committedAmount() {
        return committedAmount;
    }

    public double refundRequested() {
        return refundRequested;
    }

    public double refundAccepted() {
        return refundAccepted;
    }

    public double pendingRefundAmount() {
        return pendingRefund;
    }

    private RuntimeException settleRefund(double requested, String operation) {
        double reservationBeforeRefund = requested;
        double remaining;
        try {
            remaining = energyService.injectPower(requested, Actionable.MODULATE);
        } catch (RuntimeException exception) {
            refundRequested += requested;
            registerPendingRefund(this, requested);
            return incompleteRefund(operation, exception, reservationBeforeRefund);
        }

        if (!isValidRemainder(remaining, requested)) {
            refundRequested += requested;
            pendingRefund = requested;
            registerPendingRefund(this, requested);
            return incompleteRefund(operation, null, reservationBeforeRefund, remaining);
        }

        double normalizedRemaining = Math.min(requested, Math.max(0.0D, remaining));
        double accepted = requested - normalizedRemaining;
        recordRefundAttempt(requested, accepted, normalizedRemaining);
        if (pendingRefund <= ENERGY_TOLERANCE) {
            pendingRefund = 0.0D;
            removePendingRefund(this);
            return null;
        }
        registerPendingRefund(this, pendingRefund);
        return incompleteRefund(operation, null, reservationBeforeRefund);
    }

    private void recordRefundAttempt(double requested, double accepted, double remaining) {
        // Count every physical injectPower attempt, including retries, so repeated partial
        // acceptance is visible in the reservation diagnostics.
        if (requested > 0.0D) {
            refundRequested += requested;
        }
        refundAccepted += accepted;
        pendingRefund = remaining;
    }

    private RuntimeException incompleteRefund(String operation, @Nullable Throwable cause) {
        return incompleteRefund(operation, cause, pendingRefund);
    }

    private RuntimeException incompleteRefund(
            String operation, @Nullable Throwable cause, double reservationBeforeRefund) {
        return incompleteRefund(operation, cause, reservationBeforeRefund, pendingRefund);
    }

    private RuntimeException incompleteRefund(
            String operation,
            @Nullable Throwable cause,
            double reservationBeforeRefund,
            double rawRemainder) {
        String message = "Energy reservation refund incomplete"
            + " energyReservationId=" + reservationId
            + " operation=" + operation
            + " requested=" + requestedAmount
            + " extracted=" + extractedAmount
            + " committed=" + committedAmount
            + " refundRequested=" + refundRequested
            + " refundAccepted=" + refundAccepted
            + " refundRemainder=" + rawRemainder
            + " pendingRefund=" + pendingRefund
            + " reservationBeforeRefund=" + reservationBeforeRefund
            + " reservationAfterRefund=" + pendingRefund
            + " conservationDelta=" + conservationDelta();
        return cause == null
            ? new IllegalStateException(message)
            : new IllegalStateException(message, cause);
    }

    private double conservationDelta() {
        return extractedAmount - committedAmount - refundAccepted - pendingRefund;
    }

    private static boolean isValidRemainder(double remainder, double requested) {
        return Double.isFinite(remainder)
            && remainder >= -ENERGY_TOLERANCE
            && remainder <= requested + ENERGY_TOLERANCE;
    }

    private static void registerPendingRefund(ECOBatchEnergyReservation reservation, double amount) {
        if (amount <= ENERGY_TOLERANCE) {
            removePendingRefund(reservation);
            return;
        }
        synchronized (PENDING_REFUNDS) {
            for (PendingRefund pending : PENDING_REFUNDS) {
                if (pending.reservationId == reservation.reservationId) {
                    pending.amount = amount;
                    return;
                }
            }
            PENDING_REFUNDS.add(new PendingRefund(
                reservation.reservationId,
                reservation.energyService,
                reservation,
                amount
            ));
        }
    }

    private static void removePendingRefund(ECOBatchEnergyReservation reservation) {
        synchronized (PENDING_REFUNDS) {
            Iterator<PendingRefund> iterator = PENDING_REFUNDS.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().reservationId == reservation.reservationId) {
                    iterator.remove();
                    return;
                }
            }
        }
    }

    private static final class PendingRefund {
        private final long reservationId;
        private final IEnergyService energyService;
        private final ECOBatchEnergyReservation reservation;
        private double amount;
        private PendingRefund(
                long reservationId,
                IEnergyService energyService,
                ECOBatchEnergyReservation reservation,
                double amount) {
            this.reservationId = reservationId;
            this.energyService = energyService;
            this.reservation = reservation;
            this.amount = amount;
        }
    }
}
