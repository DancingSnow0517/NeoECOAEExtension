package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.core.storage.ExactStorageEngine;
import java.math.BigInteger;
import java.util.function.BiConsumer;

/** AE2/persistence representation adapter around the version-independent quantity engine. */
final class InfiniteStorageAmounts {
    private final ExactStorageEngine<AEKey> engine = new ExactStorageEngine<>();
    long visible(AEKey key) { return engine.visible(key); }
    HugeAmount get(AEKey key) { return HugeAmount.of(engine.exact(key)); }
    void set(AEKey key, HugeAmount amount) { engine.set(key, amount.toBigInteger()); }
    void add(AEKey key, long amount) { engine.add(key, amount); }
    void add(AEKey key, long current, long amount) { engine.add(key, current, amount); }
    void add(AEKey key, BigInteger amount) { engine.add(key, amount); }
    void subtract(AEKey key, long amount) { engine.subtract(key, amount); }
    long subtract(AEKey key, long current, long amount) { return engine.subtract(key, current, amount); }
    boolean isEmpty() { return engine.isEmpty(); }
    boolean hasOverflow() { return engine.hasOverflow(); }
    void forEach(BiConsumer<AEKey, HugeAmount> action) { engine.visitExact((key, amount) -> action.accept(key, HugeAmount.of(amount))); }
    void visitExact(BiConsumer<AEKey, BigInteger> action) { engine.visitExact(action); }
    void visitVisible(java.util.function.ObjLongConsumer<AEKey> action) { engine.visitVisible(action); }
}
