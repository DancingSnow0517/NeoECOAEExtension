package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.math.BigInteger;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

public final class HugeAmount implements Comparable<HugeAmount> {
    public static final HugeAmount ZERO = new HugeAmount(0L, null);

    private final long longValue;
    private final BigInteger bigValue;

    private HugeAmount(long longValue, BigInteger bigValue) {
        this.longValue = longValue;
        this.bigValue = bigValue;
    }

    public static HugeAmount of(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        return value == 0L ? ZERO : new HugeAmount(value, null);
    }

    public static HugeAmount of(BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (value.signum() == 0) {
            return ZERO;
        }
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
            return of(value.longValue());
        }
        return new HugeAmount(0L, value);
    }

    public static HugeAmount read(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return ZERO;
        }
        if (tag.contains("big")) {
            return of(new BigInteger(tag.getString("big")));
        }
        return of(tag.getLong("long"));
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        if (isBig()) {
            tag.putString("big", bigValue.toString());
        } else {
            tag.putLong("long", longValue);
        }
        return tag;
    }

    public HugeAmount add(HugeAmount other) {
        if (other == null || other.isZero()) {
            return this;
        }
        if (isZero()) {
            return other;
        }
        if (!isBig() && !other.isBig() && Long.MAX_VALUE - longValue >= other.longValue) {
            return of(longValue + other.longValue);
        }
        return of(toBigInteger().add(other.toBigInteger()));
    }

    public HugeAmount subtract(HugeAmount other) {
        if (other == null || other.isZero()) {
            return this;
        }
        if (compareTo(other) < 0) {
            throw new IllegalArgumentException("Amount subtraction would become negative");
        }
        if (!isBig() && !other.isBig()) {
            return of(longValue - other.longValue);
        }
        return of(toBigInteger().subtract(other.toBigInteger()));
    }

    public HugeAmount min(HugeAmount other) {
        return other == null || compareTo(other) <= 0 ? this : other;
    }

    public boolean isZero() {
        return !isBig() && longValue == 0L;
    }

    public boolean isBig() {
        return bigValue != null;
    }

    public long toLongSaturated() {
        return isBig() ? Long.MAX_VALUE : longValue;
    }

    public BigInteger toBigInteger() {
        return isBig() ? bigValue : BigInteger.valueOf(longValue);
    }

    @Override
    public int compareTo(HugeAmount other) {
        if (other == null) {
            return 1;
        }
        if (!isBig() && !other.isBig()) {
            return Long.compare(longValue, other.longValue);
        }
        return toBigInteger().compareTo(other.toBigInteger());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HugeAmount other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return toBigInteger().hashCode();
    }

    @Override
    public String toString() {
        return isBig() ? bigValue.toString() : Long.toString(longValue);
    }
}
