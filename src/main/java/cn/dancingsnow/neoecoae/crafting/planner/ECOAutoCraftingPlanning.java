package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;

/** Explicit planning boundary for crafting cards and ME Requester; other service callers keep their planner. */
public final class ECOAutoCraftingPlanning {
    private ECOAutoCraftingPlanning() {}

    public static Future<ICraftingPlan> begin(ICraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey what, long amount, CalculationStrategy strategy,
            Supplier<Future<ICraftingPlan>> fallback) {
        var source = requester.getActionSource();
        var node = requester.getGridNode();
        var grid = node == null ? null : node.getGrid();
        if (source == null || node == null || !node.isActive() || grid == null
                || grid.getCraftingService() != service
                || !(service instanceof ECOCraftingNetworkSettings settings)
                || !settings.neoecoae$isFastPlannerEnabled()
                || !settings.neoecoae$hasComputationHost(source)) {
            return fallback.get();
        }
        try {
            return ECOPlanningService.begin(level, grid, source, what, amount, strategy,
                    ECOPlannerOptions.from(settings));
        } catch (RejectedExecutionException busy) {
            // Both callers already consume failed futures on subsequent ticks; do not abort their machine tick.
            return CompletableFuture.failedFuture(busy);
        }
    }
}
