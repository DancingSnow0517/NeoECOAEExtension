package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/** Immutable exact stock shared by calculation probes and candidate retries. */
public final class PlannerInventorySnapshot {
    private final AEKey[] keys;
    private final PlannerAmount[] amounts;
    private final java.util.Set<AEKey> unboundedKeys;

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

    void initialize(PlannerCounter target) {
        for (int i = 0; i < keys.length; i++) target.set(keys[i], amounts[i]);
        unboundedKeys.forEach(target::setUnbounded);
    }

    public boolean isUnbounded(AEKey key) {
        return unboundedKeys.contains(key);
    }

    /** Independent AE2 counter for APIs that still require long-valued stock. */
    public KeyCounter toKeyCounter() {
        KeyCounter result = new KeyCounter();
        for (int i = 0; i < keys.length; i++) result.set(keys[i], amounts[i].longValueExact());
        unboundedKeys.forEach(key -> result.set(key, Long.MAX_VALUE));
        return result;
    }
}
