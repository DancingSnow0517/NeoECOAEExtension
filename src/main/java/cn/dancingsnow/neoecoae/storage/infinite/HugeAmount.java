package cn.dancingsnow.neoecoae.storage.infinite;

import cn.dancingsnow.neoecoae.crafting.amount.U128;
import cn.dancingsnow.neoecoae.crafting.amount.UnsignedLongArray;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.math.BigInteger;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/**
 * Non-negative exact amount in three tiers, normalized to the smallest that fits: {@code long}, {@link U128} below
 * 2^128, and {@link UnsignedLongArray} beyond that. BigInteger APIs and the serialized form are unchanged.
 */
public final class HugeAmount implements Comparable<HugeAmount> {
    public static final HugeAmount ZERO = new HugeAmount(0L, null, null);

    private final long longValue;
    /** Middle tier: above Long.MAX_VALUE, below 2^128. */
    private final U128 wideValue;
    private final UnsignedLongArray bigValue;

    private HugeAmount(long longValue, U128 wideValue, UnsignedLongArray bigValue) {
        this.longValue = longValue;
        this.wideValue = wideValue;
        this.bigValue = bigValue;
    }

    public static HugeAmount of(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        return value == 0L ? ZERO : new HugeAmount(value, null, null);
    }

    public static HugeAmount of(BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (value.bitLength() < Long.SIZE) {
            return of(value.longValue());
        }
        U128 wide = U128.of(value);
        return wide != null ? new HugeAmount(0L, wide, null)
            : new HugeAmount(0L, null, UnsignedLongArray.fromBigInteger(value));
    }

    public static HugeAmount of(U128 value) {
        Objects.requireNonNull(value, "value");
        return value.fitsLong() ? of(value.longValue()) : new HugeAmount(0L, value, null);
    }

    /** Normalizes an immutable segmented magnitude without a BigInteger round trip. */
    public static HugeAmount of(UnsignedLongArray value) {
        Objects.requireNonNull(value, "value");
        if (value.bitLength() < Long.SIZE) return of(value.toLongSaturated());
        if (value.bitLength() <= 128) return of(U128.ofParts(value.limb(1), value.limb(0)));
        return new HugeAmount(0L, null, value);
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
            tag.putString("big", toBigInteger().toString());
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
        if (bigValue == null && other.bigValue == null) {
            U128 sum = toU128().add(other.toU128());
            if (sum != null) return of(sum);
        }
        return of(toUnsignedLongArray().add(other.toUnsignedLongArray()));
    }

    public HugeAmount add(long other) {
        if (other < 0L) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (other == 0L) {
            return this;
        }
        if (!isBig() && Long.MAX_VALUE - longValue >= other) {
            return of(longValue + other);
        }
        if (bigValue == null) {
            U128 sum = toU128().add(other);
            if (sum != null) return of(sum);
        }
        return of(toUnsignedLongArray().add(other));
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
        if (bigValue == null && other.bigValue == null) {
            return of(toU128().subtract(other.toU128()));
        }
        return of(toUnsignedLongArray().subtract(other.toUnsignedLongArray()));
    }

    public HugeAmount subtract(long other) {
        if (other < 0L) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (other == 0L) {
            return this;
        }
        if (!isBig()) {
            if (longValue < other) {
                throw new IllegalArgumentException("Amount subtraction would become negative");
            }
            return of(longValue - other);
        }
        // Big values exceed Long.MAX_VALUE, so subtracting a long can never become negative.
        return wideValue != null ? of(wideValue.subtract(other)) : of(bigValue.subtract(other));
    }

    public HugeAmount min(HugeAmount other) {
        return other == null || compareTo(other) <= 0 ? this : other;
    }

    public boolean isZero() {
        return !isBig() && longValue == 0L;
    }

    /** Whether the amount exceeds {@code Long.MAX_VALUE}. */
    public boolean isBig() {
        return wideValue != null || bigValue != null;
    }

    public long toLongSaturated() {
        return isBig() ? Long.MAX_VALUE : longValue;
    }

    /** {@code null} only for values of 2^128 and above. */
    public U128 toU128() {
        if (bigValue != null) return null;
        return wideValue != null ? wideValue : U128.of(longValue);
    }

    public BigInteger toBigInteger() {
        if (bigValue != null) return bigValue.toBigInteger();
        return wideValue != null ? wideValue.toBigInteger() : BigInteger.valueOf(longValue);
    }

    /** Shares the immutable overflow magnitude; fixed-width values convert only when crossing tiers. */
    public UnsignedLongArray toUnsignedLongArray() {
        if (bigValue != null) return bigValue;
        return wideValue == null ? UnsignedLongArray.fromLong(longValue)
            : UnsignedLongArray.fromParts(wideValue.highWord(), wideValue.lowWord());
    }

    /** Native arithmetic view for byte accounting and mixed quantity calculations. */
    public PlannerAmount quantity() {
        if (bigValue != null) return PlannerAmount.of(bigValue.toBigInteger());
        return wideValue != null ? PlannerAmount.of(wideValue) : PlannerAmount.of(longValue);
    }

    @Override
    public int compareTo(HugeAmount other) {
        if (other == null) {
            return 1;
        }
        if (!isBig() && !other.isBig()) {
            return Long.compare(longValue, other.longValue);
        }
        if (isBig() != other.isBig()) {
            return isBig() ? 1 : -1;
        }
        // Normalized tiers: every segmented value is larger than every U128 value.
        if ((bigValue == null) != (other.bigValue == null)) {
            return bigValue != null ? 1 : -1;
        }
        return bigValue != null ? bigValue.compareTo(other.bigValue) : wideValue.compareTo(other.wideValue);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HugeAmount other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        if (bigValue != null) return bigValue.hashCode();
        return wideValue == null ? U128.hashWords(0L, longValue) : wideValue.hashCode();
    }

    @Override
    public String toString() {
        return isBig() ? toBigInteger().toString() : Long.toString(longValue);
    }
}
