package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.config.NEConfig;

/** Supplies this version's configuration while retained-size arithmetic lives in core. */
final class CycleMemoryBudget extends cn.dancingsnow.neoecoae.core.planner.cycle.CycleMemoryBudget {
    CycleMemoryBudget() { this(Math.max(1L, NEConfig.ecoPlanningMaxMemoryMiB) * 1024L * 1024L); }
    CycleMemoryBudget(long maximum) { super(maximum); }
    @Override protected Exhausted exhausted(String message) { return new Exhausted(message); }
    static final class Exhausted extends cn.dancingsnow.neoecoae.core.planner.cycle.CycleMemoryBudget.Exhausted {
        Exhausted(String message) { super(message); }
    }
}
