package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/** AE counter projection at the platform boundary; all quantity bookkeeping lives in core. */
final class PlannerCounter extends cn.dancingsnow.neoecoae.core.planner.PlannerCounter<AEKey> {
    @Override public PlannerCounter copy() {
        PlannerCounter copy = new PlannerCounter();
        copy.replaceFrom(this);
        return copy;
    }
    KeyCounter toKeyCounterExact(String stage) {
        KeyCounter result = new KeyCounter();
        for (var entry : this) {
            if (!entry.getValue().fitsLong()) throw new ArithmeticException(stage
                + " exceeds AE2 long range for " + entry.getKey() + ": " + entry.getValue());
            result.set(entry.getKey(), entry.getValue().longValueExact());
        }
        return result;
    }
}
