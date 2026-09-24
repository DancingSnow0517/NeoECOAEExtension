package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import java.util.BitSet;
import java.util.function.IntFunction;

/** Tracks per-slot decode work until a bus publishes its next provider snapshot. */
final class PatternSlotCache<T> {
    private final Object[] values;
    private final BitSet dirty = new BitSet();
    private boolean rebuildAll = true;

    PatternSlotCache(int capacity) {
        values = new Object[capacity];
    }

    void mark(int slot) {
        if (slot < 0 || slot >= values.length) rebuildAll = true;
        else dirty.set(slot);
    }

    void invalidateAll() {
        rebuildAll = true;
    }

    void refresh(int activeSlots, IntFunction<T> decoder) {
        int limit = Math.min(activeSlots, values.length);
        if (rebuildAll) dirty.set(0, limit);
        for (int slot = dirty.nextSetBit(0); slot >= 0; slot = dirty.nextSetBit(slot + 1)) {
            if (slot < values.length) values[slot] = slot < limit ? decoder.apply(slot) : null;
        }
        dirty.clear();
        rebuildAll = false;
    }

    @SuppressWarnings("unchecked")
    T get(int slot) {
        return (T) values[slot];
    }

    int capacity() {
        return values.length;
    }
}
