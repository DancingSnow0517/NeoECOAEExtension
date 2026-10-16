package cn.dancingsnow.neoecoae.impl.storage.infinite;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import java.math.BigInteger;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Persisted non-negative, finite storage view of the canonical exact quantity. */
public final class HugeAmount implements Comparable<HugeAmount> {
    public static final HugeAmount ZERO = new HugeAmount(PlannerAmount.ZERO);
    private final PlannerAmount value;

    private HugeAmount(PlannerAmount value) { this.value = value; }

    private static HugeAmount wrap(PlannerAmount value) {
        if (value.infinite() || value.signum() < 0) throw new IllegalArgumentException("Amount must be finite and non-negative");
        return value.isZero() ? ZERO : new HugeAmount(value);
    }

    public static HugeAmount of(long value) { return wrap(PlannerAmount.of(value)); }
    public static HugeAmount of(BigInteger value) { return wrap(PlannerAmount.of(Objects.requireNonNull(value, "value"))); }

    public static HugeAmount read(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return ZERO;
        return tag.contains("big") ? of(new BigInteger(tag.getString("big"))) : of(tag.getLong("long"));
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        if (isBig()) tag.putString("big", value.toString());
        else tag.putLong("long", value.longValueExact());
        return tag;
    }

    public HugeAmount add(HugeAmount other) {
        if (other == null || other.isZero()) return this;
        if (isZero()) return other;
        return wrap(value.add(other.value));
    }
    public HugeAmount add(long other) {
        if (other < 0) throw new IllegalArgumentException("Amount must not be negative");
        return other == 0 ? this : wrap(value.add(other));
    }
    public HugeAmount subtract(HugeAmount other) {
        return other == null || other.isZero() ? this : wrap(value.subtract(other.value));
    }
    public HugeAmount subtract(long other) {
        if (other < 0) throw new IllegalArgumentException("Amount must not be negative");
        return other == 0 ? this : wrap(value.subtract(other));
    }
    public HugeAmount min(HugeAmount other) { return other == null || compareTo(other) <= 0 ? this : other; }
    public boolean isZero() { return value.isZero(); }
    public boolean isBig() { return !value.fitsLong(); }
    public long toLongSaturated() { return isBig() ? Long.MAX_VALUE : value.longValueExact(); }
    public BigInteger toBigInteger() { return value.toBigInteger(); }
    @Override public int compareTo(HugeAmount other) { return other == null ? 1 : value.compareTo(other.value); }
    @Override public boolean equals(Object obj) { return obj instanceof HugeAmount other && value.equals(other.value); }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return value.toString(); }
}
