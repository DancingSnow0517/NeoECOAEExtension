package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;

/** Immutable exact stock shared by calculation probes and candidate retries. */
public final class PlannerInventorySnapshot {
    private final AEKey[] keys;
    private final PlannerAmount[] amounts;
    private final java.util.Set<AEKey> unboundedKeys;
    private volatile it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<AEKey> lookup;

    private PlannerInventorySnapshot(AEKey[] keys, PlannerAmount[] amounts, java.util.Set<AEKey> unboundedKeys) {
        this.keys = keys;
        this.amounts = amounts;
        this.unboundedKeys = java.util.Set.copyOf(unboundedKeys);
    }

    public static PlannerInventorySnapshot of(KeyCounter inventory) {
        return of(inventory, java.util.Set.of());
    }

    public static PlannerInventorySnapshot of(KeyCounter inventory, java.util.Set<AEKey> unboundedKeys) {
        java.util.List<AEKey> keys = new java.util.ArrayList<>();
        java.util.List<PlannerAmount> amounts = new java.util.ArrayList<>();
        for (var entry : inventory) {
            if (entry.getLongValue() <= 0) continue;
            keys.add(entry.getKey());
            amounts.add(PlannerAmount.of(entry.getLongValue()));
        }
        return new PlannerInventorySnapshot(keys.toArray(AEKey[]::new), amounts.toArray(PlannerAmount[]::new), unboundedKeys);
    }

    /** Freeze a server-thread quantity view into owned arrays, with sparse exact overrides. */
    public static PlannerInventorySnapshot of(Object2LongMap<AEKey> visible,
            java.util.Map<AEKey, ExactAmount> exact,
            java.util.Set<AEKey> unboundedKeys) {
        int capacity = visible.size() + exact.size();
        AEKey[] keys = new AEKey[capacity];
        PlannerAmount[] amounts = new PlannerAmount[capacity];
        int index = 0;
        for (var entry : Object2LongMaps.fastIterable(visible)) {
            long value = entry.getLongValue();
            if (value <= 0) continue;
            AEKey key = entry.getKey();
            int position = index++;
            keys[position] = key;
            var override = exact.get(key);
            amounts[position] = override != null && !override.infinite()
                ? PlannerAmount.of(override.value()) : PlannerAmount.of(value);
        }
        for (var entry : exact.entrySet()) {
            if (!entry.getValue().infinite() && visible.getLong(entry.getKey()) <= 0) {
                int position = index++;
                keys[position] = entry.getKey();
                amounts[position] = PlannerAmount.of(entry.getValue().value());
            }
        }
        return new PlannerInventorySnapshot(index == capacity ? keys : java.util.Arrays.copyOf(keys, index),
            index == capacity ? amounts : java.util.Arrays.copyOf(amounts, index), unboundedKeys);
    }

    /** Captured exact quantities, without retaining a second full map alongside the owned arrays. */
    public static PlannerInventorySnapshot exact(java.util.Map<AEKey, PlannerAmount> inventory,
            java.util.Set<AEKey> unboundedKeys) {
        AEKey[] keys = new AEKey[inventory.size()];
        PlannerAmount[] amounts = new PlannerAmount[inventory.size()];
        int index = 0;
        for (var entry : inventory.entrySet()) {
            keys[index] = entry.getKey();
            amounts[index++] = entry.getValue();
        }
        return new PlannerInventorySnapshot(keys, amounts, unboundedKeys);
    }

    public java.util.Set<AEKey> unboundedKeys() { return unboundedKeys; }

    /** Read-only map view over the captured arrays; no second quantity map is materialized. */
    public java.util.Map<AEKey, PlannerAmount> amounts() {
        return new java.util.AbstractMap<>() {
            @Override public int size() { return keys.length; }
            @Override public PlannerAmount get(Object key) {
                int index = key instanceof AEKey value ? indexOf(value) : -1;
                return index < 0 ? null : amounts[index];
            }
            @Override public boolean containsKey(Object key) { return key instanceof AEKey value && indexOf(value) >= 0; }
            @Override public void forEach(java.util.function.BiConsumer<? super AEKey, ? super PlannerAmount> action) {
                for (int i = 0; i < keys.length; i++) action.accept(keys[i], amounts[i]);
            }
            @Override public java.util.Set<java.util.Map.Entry<AEKey, PlannerAmount>> entrySet() {
                return new java.util.AbstractSet<>() {
                    @Override public int size() { return keys.length; }
                    @Override public java.util.Iterator<java.util.Map.Entry<AEKey, PlannerAmount>> iterator() {
                        return new java.util.Iterator<>() {
                            private int index;
                            @Override public boolean hasNext() { return index < keys.length; }
                            @Override public java.util.Map.Entry<AEKey, PlannerAmount> next() {
                                if (!hasNext()) throw new java.util.NoSuchElementException();
                                int current = index++;
                                return java.util.Map.entry(keys[current], amounts[current]);
                            }
                        };
                    }
                };
            }
        };
    }

    /** Visit the owned arrays directly, preserving cancellation checks without copying a second amount map. */
    public void visitAmounts(cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation cancellation,
            java.util.function.BiConsumer<AEKey, PlannerAmount> visitor) throws InterruptedException {
        for (int i = 0; i < keys.length; i++) {
            cancellation.checkpoint();
            visitor.accept(keys[i], amounts[i]);
        }
    }

    /** Build a compact key index only for routes that need random stock access. */
    public PlannerAmount amountOf(AEKey key) {
        int position = indexOf(key);
        return position < 0 ? PlannerAmount.ZERO : amounts[position];
    }

    private int indexOf(AEKey key) {
        var index = lookup;
        if (index == null) {
            index = new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<>(keys.length);
            index.defaultReturnValue(-1);
            for (int i = 0; i < keys.length; i++) index.put(keys[i], i);
            lookup = index;
        }
        return index.getInt(key);
    }

    void initialize(PlannerCounter target) {
        for (int i = 0; i < keys.length; i++) target.set(keys[i], amounts[i]);
        unboundedKeys.forEach(target::setUnbounded);
    }

    public boolean isUnbounded(AEKey key) {
        return unboundedKeys.contains(key);
    }

    /** The same stock with {@code reserved} amounts set aside; unbounded keys stay unbounded. */
    PlannerInventorySnapshot withoutReserved(java.util.Map<AEKey, PlannerAmount> reserved) {
        if (reserved.isEmpty()) return this;
        PlannerAmount[] remaining = amounts.clone();
        for (int i = 0; i < keys.length; i++) {
            PlannerAmount held = reserved.get(keys[i]);
            if (held != null && !unboundedKeys.contains(keys[i])) {
                remaining[i] = remaining[i].subtract(held).max(PlannerAmount.ZERO);
            }
        }
        return new PlannerInventorySnapshot(keys.clone(), remaining, unboundedKeys);
    }

    /** Independent bounded projection; the exact quantities stay in this snapshot. */
    public KeyCounter toKeyCounter() {
        KeyCounter result = new KeyCounter();
        for (int i = 0; i < keys.length; i++) result.set(keys[i],
            amounts[i].fitsLong() ? amounts[i].longValueExact() : Long.MAX_VALUE);
        unboundedKeys.forEach(key -> result.set(key, Long.MAX_VALUE));
        return result;
    }
}
