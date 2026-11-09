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

    private PlannerInventorySnapshot(java.util.Map<AEKey, PlannerAmount> inventory,
            java.util.Set<AEKey> unboundedKeys) {
        this.keys = new AEKey[inventory.size()];
        this.amounts = new PlannerAmount[inventory.size()];
        this.unboundedKeys = java.util.Set.copyOf(unboundedKeys);
        int index = 0;
        for (var entry : inventory.entrySet()) {
            keys[index] = entry.getKey();
            amounts[index++] = entry.getValue();
        }
        this.exactAmounts = java.util.Map.copyOf(inventory);
    }

    public static PlannerInventorySnapshot of(KeyCounter inventory) {
        return of(inventory, java.util.Set.of());
    }

    public static PlannerInventorySnapshot of(KeyCounter inventory, java.util.Set<AEKey> unboundedKeys) {
        var amounts = new java.util.LinkedHashMap<AEKey, PlannerAmount>();
        for (var entry : inventory) {
            if (entry.getLongValue() <= 0) continue;
            amounts.put(entry.getKey(), PlannerAmount.of(entry.getLongValue()));
        }
        return new PlannerInventorySnapshot(amounts, unboundedKeys);
    }

    public static PlannerInventorySnapshot exact(java.util.Map<AEKey, PlannerAmount> inventory,
            java.util.Set<AEKey> unboundedKeys) {
        return new PlannerInventorySnapshot(inventory, unboundedKeys);
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
        var remaining = new java.util.LinkedHashMap<AEKey, PlannerAmount>();
        for (int i = 0; i < keys.length; i++) {
            PlannerAmount held = reserved.get(keys[i]);
            PlannerAmount amount = amounts[i];
            if (held != null && !unboundedKeys.contains(keys[i])) {
                amount = amount.subtract(held).max(PlannerAmount.ZERO);
            }
            remaining.put(keys[i], amount);
        }
        return new PlannerInventorySnapshot(remaining, unboundedKeys);
    }

    /** Independent bounded projection for APIs that still require long-valued stock. */
    public KeyCounter toKeyCounter() {
        KeyCounter result = new KeyCounter();
        for (int i = 0; i < keys.length; i++) result.set(keys[i], amounts[i].longValueSaturated());
        unboundedKeys.forEach(key -> result.set(key, Long.MAX_VALUE));
        return result;
    }
}
