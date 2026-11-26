package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

/** Immutable exact stock shared by calculation probes and candidate retries. */
public final class PlannerInventorySnapshot {
    private final AEKey[] keys;
    private final PlannerAmount[] amounts;
    private final java.util.Map<AEKey, PlannerAmount> exactAmounts;
    private final java.util.Set<AEKey> unboundedKeys;
    private final int positiveEntries;
    private final boolean negativeStock;

    private final @Nullable Integer captureServerTick;

    private PlannerInventorySnapshot(java.util.Map<AEKey, PlannerAmount> inventory,
            java.util.Set<AEKey> unboundedKeys, @Nullable Integer captureServerTick) {
        this.captureServerTick = captureServerTick;
        this.keys = new AEKey[inventory.size()];
        this.amounts = new PlannerAmount[inventory.size()];
        this.unboundedKeys = java.util.Set.copyOf(unboundedKeys);
        int index = 0, positive = 0;
        boolean negative = false;
        for (var entry : inventory.entrySet()) {
            keys[index] = entry.getKey();
            PlannerAmount amount = entry.getValue();
            amounts[index++] = amount;
            int sign = amount.signum();
            if (sign > 0) positive++;
            else if (sign < 0) negative = true;
        }
        this.positiveEntries = positive;
        this.negativeStock = negative;
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
        return exact(amounts, unboundedKeys);
    }

    public static PlannerInventorySnapshot exact(java.util.Map<AEKey, PlannerAmount> inventory,
            java.util.Set<AEKey> unboundedKeys) {
        return exact(inventory, unboundedKeys, null);
    }

    public static PlannerInventorySnapshot exact(java.util.Map<AEKey, PlannerAmount> inventory,
            java.util.Set<AEKey> unboundedKeys, @Nullable Integer captureServerTick) {
        return new PlannerInventorySnapshot(inventory, unboundedKeys, captureServerTick);
    }

    /** Tick passed to the inventory sampler (not the last provider update or the confirmation tick). */
    public @Nullable Integer captureServerTick() { return captureServerTick; }
    public java.util.Map<AEKey, PlannerAmount> amounts() { return exactAmounts; }
    public PlannerAmount amountOf(AEKey key) { return exactAmounts.getOrDefault(key, PlannerAmount.ZERO); }
    public java.util.Set<AEKey> unboundedKeys() { return unboundedKeys; }

    /** Same point value as the initialized worker counter, including its unbounded marker. */
    PlannerAmount storedAmount(AEKey key) {
        return isUnbounded(key) ? PlannerCounter.UNBOUNDED_MARKER : amountOf(key);
    }

    // Models that previously initialized a counter must still reject malformed stock, even for unrelated keys.
    void requireValidStock() {
        if (negativeStock) throw new IllegalArgumentException("negative planner counter amount");
    }

    PlannerCounter newStoredCounter() {
        requireValidStock();
        return new PlannerCounter(keys, amounts, unboundedKeys, Math.max(positiveEntries, unboundedKeys.size()));
    }

    java.util.Set<AEKey> copyStoredKeys() {
        requireValidStock();
        var result = new java.util.LinkedHashSet<AEKey>();
        for (int i = 0; i < keys.length; i++) if (!amounts[i].isZero()) result.add(keys[i]);
        result.addAll(unboundedKeys);
        return result;
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
        return new PlannerInventorySnapshot(remaining, unboundedKeys, captureServerTick);
    }

    /** Independent bounded projection for APIs that still require long-valued stock. */
    public KeyCounter toKeyCounter() {
        KeyCounter result = new KeyCounter();
        for (int i = 0; i < keys.length; i++) result.set(keys[i], amounts[i].longValueSaturated());
        unboundedKeys.forEach(key -> result.set(key, Long.MAX_VALUE));
        return result;
    }
}
