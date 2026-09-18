package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact planner-side counter. Conversion to AE2's long counter is explicit. */
final class PlannerCounter implements Iterable<Map.Entry<AEKey, PlannerAmount>> {
    private final Map<AEKey, PlannerAmount> values = new LinkedHashMap<>();
    private final java.util.Set<AEKey> unbounded = new java.util.LinkedHashSet<>();

    void setUnbounded(AEKey key) {
        values.put(key, PlannerAmount.of(Long.MAX_VALUE));
        unbounded.add(key);
    }

    boolean isUnbounded(AEKey key) { return unbounded.contains(key); }

    java.util.Set<AEKey> unboundedKeys() { return java.util.Set.copyOf(unbounded); }

    PlannerAmount available(AEKey key, PlannerAmount requested) {
        return isUnbounded(key) ? requested : requested.min(get(key));
    }

    PlannerAmount get(AEKey key) {
        return values.getOrDefault(key, PlannerAmount.ZERO);
    }

    void set(AEKey key, PlannerAmount amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("negative planner counter amount");
        unbounded.remove(key);
        if (amount.isZero()) values.remove(key);
        else values.put(key, amount);
    }

    void add(AEKey key, PlannerAmount amount) {
        set(key, get(key).add(amount));
    }

    void add(AEKey key, long amount) {
        add(key, PlannerAmount.of(amount));
    }

    void remove(AEKey key, PlannerAmount amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("negative removal");
        if (isUnbounded(key)) return;
        PlannerAmount current = get(key);
        if (current.compareTo(amount) < 0) throw new IllegalArgumentException("counter underflow");
        set(key, current.subtract(amount));
    }

    boolean isEmpty() {
        return values.isEmpty();
    }

    Map<AEKey, PlannerAmount> asMap() {
        return Collections.unmodifiableMap(values);
    }

    PlannerCounter copy() {
        PlannerCounter copy = new PlannerCounter();
        copy.values.putAll(values);
        copy.unbounded.addAll(unbounded);
        return copy;
    }

    void replaceFrom(PlannerCounter source) {
        values.clear();
        values.putAll(source.values);
        unbounded.clear();
        unbounded.addAll(source.unbounded);
    }

    KeyCounter toKeyCounterExact(String stage) {
        KeyCounter result = new KeyCounter();
        for (var entry : values.entrySet()) {
            if (!entry.getValue().fitsLong()) {
                throw new ArithmeticException(stage + " exceeds AE2 long range for " + entry.getKey()
                    + ": " + entry.getValue());
            }
            result.set(entry.getKey(), entry.getValue().longValueExact());
        }
        return result;
    }

    @Override
    public java.util.Iterator<Map.Entry<AEKey, PlannerAmount>> iterator() {
        return values.entrySet().iterator();
    }
}
