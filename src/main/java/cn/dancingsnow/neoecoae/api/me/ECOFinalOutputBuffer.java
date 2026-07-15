package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;

/** Owns final crafting output between Worker return and requester/network delivery. */
final class ECOFinalOutputBuffer {
    private long amount;

    ECOFinalOutputBuffer() {
    }

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
}
