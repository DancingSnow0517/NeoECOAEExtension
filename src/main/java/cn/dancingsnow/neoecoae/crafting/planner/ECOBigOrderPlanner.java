package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import java.util.concurrent.Future;

/** Each invocation captures fresh inventory and compiles a fresh graph; probes share only that session. */
public final class ECOBigOrderPlanner {
    private ECOBigOrderPlanner() {}

    public static Future<Answer> begin(IGrid grid, AEKey goal, long maximum, long bytes, ECOPlannerOptions options) {
        var inventory = ECOPlannerInventory.capture(grid);
        var session = new ECOCraftingPlannerService().createSession(grid.getCraftingService(), goal, inventory,
                options.cyclePlanningEnabled(), options.ignorePatternSubstitutions(), options.fuzzyPlanningItemIds());
        return ECOPlanningExecutor.submit(() -> search(maximum, bytes,
                amount -> session.plan(amount, false, () -> {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                })));
    }

    @FunctionalInterface
    public interface Probe { ECOPlanningResult plan(long amount) throws InterruptedException; }
    public record Answer(ECOPlanningResult result, boolean capacity, boolean fatal) {}

    public static Answer search(long maximum, long bytes, Probe probe) throws InterruptedException {
        if (maximum <= 0 || bytes < 0) throw new IllegalArgumentException("Invalid segment bounds");
        long candidate = maximum;
        boolean capacity = false;
        ECOPlanningResult last;
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            last = probe.plan(candidate);
            if (last.status() == PlanningStatus.SUCCESS && last.plan() != null && !last.plan().simulation()
                    && last.executionPlanError() == null) {
                if (last.plan().bytes() >= 0 && last.plan().bytes() <= bytes)
                    return new Answer(last, false, false);
                capacity = true;
            } else if (last.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE) {
                capacity = true;
            } else if (last.status() != PlanningStatus.MISSING_ITEMS) {
                return new Answer(last, false, true);
            } else {
                capacity = false;
            }
            if (candidate == 1) return new Answer(last, capacity, false);
            candidate = Math.max(1, candidate / 2);
        }
    }
}
