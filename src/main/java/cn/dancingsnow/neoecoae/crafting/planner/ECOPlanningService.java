package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;

import java.util.concurrent.Future;

import net.minecraft.world.level.Level;

/**
 * ECO-owned planning boundary. It deliberately does not call
 * {@code ICraftingService.beginCraftingCalculation}, keeping this request outside other planner wrappers.
 */
public final class ECOPlanningService {
    private static final ECOCraftingPlannerService PLANNER = new ECOCraftingPlannerService();

    private ECOPlanningService() {
    }

    public static Future<ICraftingPlan> begin(Level level, IGrid grid, IActionSource source, AEKey goal,
                                              long amount, CalculationStrategy strategy, ECOPlannerOptions options) {
        var inventory = ECOPlannerInventory.capture(grid);
        ECOCraftingPlannerService.Session session = PLANNER.createSession(grid.getCraftingService(), goal, inventory,
                options.cyclePlanningEnabled(), options.ignorePatternSubstitutions(), options.fuzzyPlanningItemIds());
        return ECOPlanningExecutor.submit(() -> plan(session, goal, amount, strategy));
    }

    private static ICraftingPlan plan(ECOCraftingPlannerService.Session session, AEKey goal, long requestedAmount,
                                      CalculationStrategy strategy) throws InterruptedException {
        if (requestedAmount <= 0L) {
            return session.plan(requestedAmount, true, ECOPlanningService::checkpoint).plan();
        }

        ECOPlanningResult exact = session.plan(requestedAmount, false, ECOPlanningService::checkpoint);
        if (isExecutableSuccess(exact)) {
            return exact.plan();
        }

        if (strategy == CalculationStrategy.CRAFT_LESS) {
            long bestAmount = 0L;
            ICraftingPlan bestPlan = null;
            for (long step = Long.highestOneBit(requestedAmount); step > 0L; step >>= 1) {
                checkpoint();
                long candidate = bestAmount + step;
                if (candidate >= requestedAmount) {
                    continue;
                }
                ECOPlanningResult probe = session.plan(candidate, false, ECOPlanningService::checkpoint);
                if (isExecutableSuccess(probe)) {
                    bestAmount = candidate;
                    bestPlan = probe.plan();
                }
            }
            if (bestPlan != null) {
                return bestPlan;
            }
        } else if (isReportableDiagnostic(exact)) {
            return exact.plan();
        }

        ECOPlanningResult report = session.plan(requestedAmount, true, ECOPlanningService::checkpoint);
        return report.plan();
    }

    private static boolean isExecutableSuccess(ECOPlanningResult result) {
        return result.status() == PlanningStatus.SUCCESS && result.plan() != null && !result.plan().simulation();
    }

    private static boolean isReportableDiagnostic(ECOPlanningResult result) {
        return result.plan() != null && result.status() != PlanningStatus.MISSING_ITEMS;
    }

    private static void checkpoint() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("ECO planning request cancelled");
        }
    }
}
