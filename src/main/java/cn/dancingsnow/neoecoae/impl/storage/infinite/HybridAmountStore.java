package cn.dancingsnow.neoecoae.impl.storage.infinite;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Stores positive quantities on the primitive path until an individual key exceeds {@link Long#MAX_VALUE}.
 *
 * <p>The wide map is deliberately sparse. Normal AE I/O only calls {@link #add(Object, long)} and
 * {@link #subtractAtMost(Object, long)}, neither of which allocates a {@link BigInteger} for a long-backed key.</p>
 */
final class HybridAmountStore<K> {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final Object2LongOpenHashMap<K> longAmounts = new Object2LongOpenHashMap<>();
    private final Object2ObjectOpenHashMap<K, BigInteger> wideAmounts = new Object2ObjectOpenHashMap<>();

    HybridAmountStore() {
        longAmounts.defaultReturnValue(0L);
    }

    int size() {
        return longAmounts.size() + wideAmounts.size();
    }

    boolean isEmpty() {
        return longAmounts.isEmpty() && wideAmounts.isEmpty();
    }

    boolean contains(K key) {
        return longAmounts.containsKey(key) || wideAmounts.containsKey(key);
    }

    boolean isWide(K key) {
        return wideAmounts.containsKey(key);
    }

    HugeAmount get(K key) {
        BigInteger wide = wideAmounts.get(key);
        return wide == null ? HugeAmount.of(longAmounts.getLong(key)) : HugeAmount.of(wide);
    }

    long getSaturated(K key) {
        return wideAmounts.containsKey(key) ? Long.MAX_VALUE : longAmounts.getLong(key);
    }

    /** Adds a positive long amount and returns the resulting exact amount. */
    HugeAmount add(K key, long amount) {
        requirePositive(amount);
        Objects.requireNonNull(key, "key");

        BigInteger wide = wideAmounts.get(key);
        if (wide != null) {
            BigInteger next = wide.add(BigInteger.valueOf(amount));
            wideAmounts.put(key, next);
            return HugeAmount.of(next);
        }

        long current = longAmounts.getLong(key);
        if (Long.MAX_VALUE - current >= amount) {
            long next = current + amount;
            longAmounts.put(key, next);
            return HugeAmount.of(next);
        }

        BigInteger next = BigInteger.valueOf(current).add(BigInteger.valueOf(amount));
        longAmounts.removeLong(key);
        wideAmounts.put(key, next);
        return HugeAmount.of(next);
    }

    /**
     * Removes at most {@code amount}, returning the actual removed quantity. A wide value can always satisfy a
     * positive long request, but it may demote back to the primitive map after the subtraction.
     */
    long subtractAtMost(K key, long amount) {
        requirePositive(amount);
        Objects.requireNonNull(key, "key");

        BigInteger wide = wideAmounts.get(key);
        if (wide != null) {
            BigInteger delta = BigInteger.valueOf(amount);
            BigInteger next = wide.subtract(delta);
            if (next.signum() < 0) {
                throw new IllegalStateException("Wide amount was smaller than a long extraction request");
            }
            wideAmounts.remove(key);
            if (next.signum() > 0) {
                if (next.compareTo(LONG_MAX) <= 0) {
                    longAmounts.put(key, next.longValueExact());
                } else {
                    wideAmounts.put(key, next);
                }
            }
            return amount;
        }

        long current = longAmounts.getLong(key);
        long removed = Math.min(current, amount);
        if (removed == 0L) {
            return 0L;
        }
        long next = current - removed;
        if (next == 0L) {
            longAmounts.removeLong(key);
        } else {
            longAmounts.put(key, next);
        }
        return removed;
    }

    /** Replaces one quantity while retaining the canonical long/wide representation. */
    void set(K key, HugeAmount amount) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(amount, "amount");
        longAmounts.removeLong(key);
        wideAmounts.remove(key);
        if (amount.isZero()) {
            return;
        }
        if (amount.isBig()) {
            BigInteger value = amount.toBigInteger();
            if (value.compareTo(LONG_MAX) <= 0) {
                longAmounts.put(key, value.longValueExact());
            } else {
                wideAmounts.put(key, value);
            }
        } else {
            longAmounts.put(key, amount.toLongSaturated());
        }
    }

    void clear() {
        longAmounts.clear();
        wideAmounts.clear();
    }

    void forEach(BiConsumer<? super K, ? super HugeAmount> consumer) {
        longAmounts.object2LongEntrySet().forEach(entry -> consumer.accept(entry.getKey(), HugeAmount.of(entry.getLongValue())));
        wideAmounts.object2ObjectEntrySet().forEach(entry -> consumer.accept(entry.getKey(), HugeAmount.of(entry.getValue())));
    }

    private static void requirePositive(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
