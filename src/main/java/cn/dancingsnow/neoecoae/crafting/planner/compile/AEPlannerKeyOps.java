package cn.dancingsnow.neoecoae.crafting.planner.compile;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.core.planner.KeyOps;

/** AEKey is already immutable. Legacy mutable stacks must be frozen in their own version adapter. */
public enum AEPlannerKeyOps implements KeyOps<AEKey> {
    INSTANCE;
    @Override public boolean matches(AEKey template, AEKey candidate) { return template.equals(candidate); }
    @Override public long amountPerUnit(AEKey key) { return key.getAmountPerUnit(); }
}
