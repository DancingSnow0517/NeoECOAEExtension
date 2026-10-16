package cn.dancingsnow.neoecoae.core.planner.acyclic;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.core.planner.PlannerCounter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Exact immutable stock shared by candidate retries; unbounded quantities remain explicit. */
public final class InventorySnapshot<K> {
    private final Map<K, PlannerAmount> amounts;
    private final Set<K> unbounded;

    public InventorySnapshot(Map<K, PlannerAmount> amounts, Set<K> unbounded) {
        var positive = new LinkedHashMap<K, PlannerAmount>();
        amounts.forEach((key, amount) -> {
            if (amount.signum() < 0 || amount.infinite()) throw new IllegalArgumentException("Invalid finite stock");
            if (amount.signum() > 0) positive.put(key, amount);
        });
        this.amounts = java.util.Collections.unmodifiableMap(positive);
        this.unbounded = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(unbounded));
    }

    public void initialize(PlannerCounter<K> target) {
        amounts.forEach(target::set);
        unbounded.forEach(target::setUnbounded);
    }
    public boolean isUnbounded(K key) { return unbounded.contains(key); }
    public void visitFinite(BiConsumer<K, PlannerAmount> visitor) { amounts.forEach(visitor); }
    public void visitUnbounded(java.util.function.Consumer<K> visitor) { unbounded.forEach(visitor); }
}
