package cn.dancingsnow.neoecoae.api.me;

import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Exact order accounting; a completed child UUID can debit the order only once. */
public final class ECOBigCraftingLedger {
    private final BigInteger total;
    private BigInteger remaining;
    private UUID jobId;
    private long batch;

    public ECOBigCraftingLedger(BigInteger total) {
        if (total.signum() <= 0) throw new IllegalArgumentException("Order amount must be positive");
        this.total = this.remaining = total;
    }

    public BigInteger total() {
        return total;
    }

    public BigInteger remaining() {
        return remaining;
    }

    public UUID jobId() {
        return jobId;
    }

    public long batch() {
        return batch;
    }

    public long nextBatch(long limit) {
        if (limit <= 0 || jobId != null) throw new IllegalStateException("Cannot start another batch");
        return remaining.min(BigInteger.valueOf(limit)).longValueExact();
    }

    public void bind(UUID id, long amount) {
        if (id == null
                || jobId != null
                || amount <= 0
                || BigInteger.valueOf(amount).compareTo(remaining) > 0)
            throw new IllegalArgumentException("Invalid order batch");
        jobId = id;
        batch = amount;
    }

    public boolean complete(UUID id) {
        if (id == null || !id.equals(jobId)) return false;
        remaining = remaining.subtract(BigInteger.valueOf(batch));
        jobId = null;
        batch = 0;
        return true;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("total", total.toString());
        tag.putString("remaining", remaining.toString());
        if (jobId != null) {
            tag.putUUID("job", jobId);
            tag.putLong("batch", batch);
        }
        return tag;
    }

    public static ECOBigCraftingLedger read(CompoundTag tag) {
        var ledger = new ECOBigCraftingLedger(new BigInteger(tag.getString("total")));
        ledger.remaining = new BigInteger(tag.getString("remaining"));
        if (ledger.remaining.signum() < 0 || ledger.remaining.compareTo(ledger.total) > 0)
            throw new IllegalArgumentException("Invalid remaining amount");
        if (tag.hasUUID("job")) ledger.bind(tag.getUUID("job"), tag.getLong("batch"));
        return ledger;
    }
}
