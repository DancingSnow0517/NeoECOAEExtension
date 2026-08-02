package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import java.util.function.LongUnaryOperator;

/** Owns final crafting output between worker return and requester/network delivery. */
final class ECOFinalOutputBuffer {
    private long amount;

    ECOFinalOutputBuffer() {}

    ECOFinalOutputBuffer(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Buffered final output cannot be negative");
        }
        this.amount = amount;
    }

    long amount() {
        return amount;
    }

    long accept(long offered, Actionable mode) {
        if (offered <= 0L) {
            return 0L;
        }
        long accepted = Math.min(offered, Long.MAX_VALUE - amount);
        if (mode == Actionable.MODULATE) {
            amount += accepted;
        }
        return accepted;
    }

    void removeDelivered(long delivered) {
        if (delivered < 0L || delivered > amount) {
            throw new IllegalArgumentException("Invalid delivered final output: " + delivered + " of " + amount);
        }
        amount -= delivered;
    }

    Delivery attemptDelivery(long remainingAmount, LongUnaryOperator target) {
        long deliverable = Math.min(amount, Math.max(0L, remainingAmount));
        if (deliverable <= 0L) {
            return new Delivery(0L, Math.max(0L, remainingAmount));
        }

        long delivered = target.applyAsLong(deliverable);
        if (delivered < 0L || delivered > deliverable) {
            throw new IllegalStateException(
                    "Invalid final-output insertion result: " + delivered + " for " + deliverable);
        }

        return new Delivery(delivered, Math.max(0L, remainingAmount - delivered));
    }

    void completeDelivery(Delivery delivery) {
        removeDelivered(delivery.delivered());
    }

    record Delivery(long delivered, long remainingAmount) {}
}
