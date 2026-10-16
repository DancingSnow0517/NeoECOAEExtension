package cn.dancingsnow.neoecoae.core.planner;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.Collections;
import java.util.Map;

/** Exact version-independent planner counter; unbounded working stock has an explicit finite compatibility projection. */
public class PlannerCounter<K> implements Iterable<Map.Entry<K, PlannerAmount>> {
    private final Map<K, PlannerAmount> values = new Object2ObjectLinkedOpenHashMap<>();
    private final java.util.Set<K> unbounded = new java.util.LinkedHashSet<>();

    public void setUnbounded(K key) {
        values.put(key, PlannerAmount.of(Long.MAX_VALUE));
        unbounded.add(key);
    }

    public boolean isUnbounded(K key) { return unbounded.contains(key); }

    public java.util.Set<K> unboundedKeys() { return unbounded; }

    public PlannerAmount available(K key, PlannerAmount requested) {
        return isUnbounded(key) ? requested : requested.min(get(key));
    }

    public PlannerAmount get(K key) {
        return values.getOrDefault(key, PlannerAmount.ZERO);
    }

    public void set(K key, PlannerAmount amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("negative planner counter amount");
        unbounded.remove(key);
        if (amount.isZero()) values.remove(key);
        else values.put(key, amount);
    }

    public void add(K key, PlannerAmount amount) {
        set(key, get(key).add(amount));
    }

    public void add(K key, long amount) {
        add(key, PlannerAmount.of(amount));
    }

    public void remove(K key, PlannerAmount amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("negative removal");
        if (isUnbounded(key)) return;
        PlannerAmount current = get(key);
        if (current.compareTo(amount) < 0) throw new IllegalArgumentException("counter underflow");
        set(key, current.subtract(amount));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<K, PlannerAmount> asMap() {
        return values;
    }

    /**
     * Stable keys for loops that consume this counter. Fastutil entries refer to table slots and become
     * invalid when removal shifts or rehashes the table; copying the entry list does not detach them.
     * Read quantities through available/get at the time of consumption instead.
     */
    public java.util.List<K> keysSnapshot() {
        return new java.util.ArrayList<>(values.keySet());
    }

    public PlannerCounter<K> copy() {
        PlannerCounter<K> copy = new PlannerCounter<>();
        copy.values.putAll(values);
        copy.unbounded.addAll(unbounded);
        return copy;
    }

    public void replaceFrom(PlannerCounter<K> source) {
        values.clear();
        values.putAll(source.values);
        unbounded.clear();
        unbounded.addAll(source.unbounded);
    }


    @Override
    public java.util.Iterator<Map.Entry<K, PlannerAmount>> iterator() {
        return values.entrySet().iterator();
    }
}
