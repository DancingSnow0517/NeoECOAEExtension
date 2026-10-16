package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.core.planner.acyclic.InventorySnapshot;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/** Immutable exact stock shared by calculation probes and candidate retries. */
public final class PlannerInventorySnapshot {
    private final InventorySnapshot<AEKey> snapshot;

    private PlannerInventorySnapshot(InventorySnapshot<AEKey> snapshot) {
        this.snapshot = snapshot;
    }

    public static PlannerInventorySnapshot of(KeyCounter inventory) {
        return of(inventory, java.util.Set.of());
    }

    public static PlannerInventorySnapshot of(KeyCounter inventory, java.util.Set<AEKey> unboundedKeys) {
        var amounts = new java.util.LinkedHashMap<AEKey, PlannerAmount>();
        for (var entry : inventory) {
            if (entry.getLongValue() > 0) amounts.put(entry.getKey(), PlannerAmount.of(entry.getLongValue()));
        }
        return new PlannerInventorySnapshot(new InventorySnapshot<>(amounts, unboundedKeys));
    }

    void initialize(PlannerCounter target) {
        snapshot.initialize(target);
    }

    public boolean isUnbounded(AEKey key) {
        return snapshot.isUnbounded(key);
    }

    /** Independent AE2 counter for APIs that still require long-valued stock. */
    public KeyCounter toKeyCounter() {
        KeyCounter result = new KeyCounter();
        snapshot.visitFinite((key, amount) -> result.set(key, amount.longValueExact()));
        snapshot.visitUnbounded(key -> result.set(key, Long.MAX_VALUE));
        return result;
    }
}
