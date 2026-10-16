package cn.dancingsnow.neoecoae.core.planner;

/** Key operations supplied once at the compile boundary; keys themselves are never wrapped. */
public interface KeyOps<K> {
    boolean matches(K template, K candidate);
    long amountPerUnit(K key);
    default K freeze(K key) { return key; }
}
