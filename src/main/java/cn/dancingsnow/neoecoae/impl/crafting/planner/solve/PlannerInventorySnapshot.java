package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/** Immutable exact stock shared by calculation probes and candidate retries. */
public final class PlannerInventorySnapshot {
    private final AEKey[] keys;
    private final PlannerAmount[] amounts;

    private PlannerInventorySnapshot(AEKey[] keys, PlannerAmount[] amounts) {
        this.keys = keys;
        this.amounts = amounts;
    }

    public static PlannerInventorySnapshot of(KeyCounter inventory) {
        java.util.List<AEKey> keys = new java.util.ArrayList<>();
        java.util.List<PlannerAmount> amounts = new java.util.ArrayList<>();
        for (var entry : inventory) {
            if (entry.getLongValue() <= 0) continue;
            keys.add(entry.getKey());
            amounts.add(PlannerAmount.of(entry.getLongValue()));
        }
        return new PlannerInventorySnapshot(keys.toArray(AEKey[]::new), amounts.toArray(PlannerAmount[]::new));
    }

    void initialize(PlannerCounter target) {
        for (int i = 0; i < keys.length; i++) target.set(keys[i], amounts[i]);
    }

    /** Independent AE2 counter for APIs that still require long-valued stock. */
    public KeyCounter toKeyCounter() {
        KeyCounter result = new KeyCounter();
        for (int i = 0; i < keys.length; i++) result.set(keys[i], amounts[i].longValueExact());
        return result;
    }
}
