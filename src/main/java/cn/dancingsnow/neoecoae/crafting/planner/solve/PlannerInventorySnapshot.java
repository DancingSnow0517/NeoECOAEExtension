package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/** Immutable exact stock shared by calculation probes and candidate retries. */
public final class PlannerInventorySnapshot {
    private final AEKey[] keys;
    private final PlannerAmount[] amounts;
    private final java.util.Map<AEKey, PlannerAmount> exactAmounts;
    private final java.util.Set<AEKey> unboundedKeys;

    private PlannerInventorySnapshot(AEKey[] keys, PlannerAmount[] amounts, java.util.Set<AEKey> unboundedKeys) {
        this.keys = keys;
        this.amounts = amounts;
        this.unboundedKeys = java.util.Set.copyOf(unboundedKeys);
        var exact = new java.util.LinkedHashMap<AEKey, PlannerAmount>();
        for (int i = 0; i < keys.length; i++) exact.put(keys[i], amounts[i]);
        this.exactAmounts = java.util.Map.copyOf(exact);
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

    public static PlannerInventorySnapshot exact(java.util.Map<AEKey, PlannerAmount> inventory,
            java.util.Set<AEKey> unboundedKeys) {
        AEKey[] keys = inventory.keySet().toArray(AEKey[]::new);
        PlannerAmount[] amounts = new PlannerAmount[keys.length];
        for (int i = 0; i < keys.length; i++) amounts[i] = inventory.get(keys[i]);
        return new PlannerInventorySnapshot(keys, amounts, unboundedKeys);
    }

    public java.util.Map<AEKey, PlannerAmount> amounts() { return exactAmounts; }
    public PlannerAmount amountOf(AEKey key) { return exactAmounts.getOrDefault(key, PlannerAmount.ZERO); }
    public java.util.Set<AEKey> unboundedKeys() { return unboundedKeys; }

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

    /** Independent bounded projection for APIs that still require long-valued stock. */
    public KeyCounter toKeyCounter() {
        KeyCounter result = new KeyCounter();
        for (int i = 0; i < keys.length; i++) result.set(keys[i], amounts[i].longValueSaturated());
        unboundedKeys.forEach(key -> result.set(key, Long.MAX_VALUE));
        return result;
    }
}
