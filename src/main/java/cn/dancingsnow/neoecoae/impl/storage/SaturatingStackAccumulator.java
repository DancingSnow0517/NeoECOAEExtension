package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/** Combines storage-listing results without allowing signed {@code long} wraparound. */
public final class SaturatingStackAccumulator {
    private SaturatingStackAccumulator() {}

    public static void addAll(KeyCounter target, KeyCounter contribution) {
        for (var entry : contribution) {
            add(target, entry.getKey(), entry.getLongValue());
        }
    }

    static void add(KeyCounter target, AEKey key, long amount) {
        long current = target.get(key);
        if (amount > 0L && current > Long.MAX_VALUE - amount) {
            target.set(key, Long.MAX_VALUE);
        } else if (amount < 0L && current < Long.MIN_VALUE - amount) {
            target.set(key, Long.MIN_VALUE);
        } else {
            target.set(key, current + amount);
        }
    }
}
