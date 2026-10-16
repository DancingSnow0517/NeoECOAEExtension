package cn.dancingsnow.neoecoae.impl.storage.infinite;

import cn.dancingsnow.neoecoae.core.storage.ExactStorageTotal;
import java.math.BigInteger;

/** Adapts the pure statistic to the persisted amount representation. */
final class MutableInfiniteStorageTotal {
    private final ExactStorageTotal total = new ExactStorageTotal();
    void add(long amount) { total.add(amount); }
    void subtract(long amount) { total.subtract(amount); }
    void add(BigInteger amount) { total.add(amount); }
    void subtract(BigInteger amount) { total.subtract(amount); }
    HugeAmount snapshot() { return HugeAmount.of(total.snapshot()); }
}
