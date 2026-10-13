package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.grid.NEMachineLookup;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import org.jetbrains.annotations.Nullable;

/**
 * The single definition of which computation host may serve an ECO planning request. Routing, the source-aware
 * crafting service check and the screen-routing diagnostics all use it, so the logged eligible count always
 * explains the route that was actually taken.
 */
public final class ECOComputationHostRouting {
    private ECOComputationHostRouting() {
    }

    public static boolean isEligibleHost(ECOComputationSystemBlockEntity host, IActionSource actionSource) {
        NEComputationCluster cluster = host.getCluster();
        return host.isFormed() && host.getMainNode().isOnline()
            && cluster != null && cluster.isActive()
            && cluster.canBeAutoSelectedFor(actionSource);
    }

    public static boolean hasEligibleHost(IGrid grid, IActionSource actionSource) {
        for (ECOComputationSystemBlockEntity host : NEMachineLookup.getMachines(grid, ECOComputationSystemBlockEntity.class)) {
            if (isEligibleHost(host, actionSource)) {
                return true;
            }
        }
        return false;
    }

    /** Reason for a request that did not take the ECO route, as logged by the planner routing. */
    public static String routeReason(@Nullable ECOCraftingNetworkSettings settings, boolean fastPlannerEnabled,
                                     boolean eligibleHostPresent) {
        if (settings == null) return "CRAFTING_SERVICE_HAS_NO_ECO_SETTINGS";
        if (!fastPlannerEnabled) {
            return eligibleHostPresent ? "FAST_PLANNER_DISABLED" : "FAST_PLANNER_DISABLED_AND_NO_ELIGIBLE_HOST";
        }
        if (!eligibleHostPresent) {
            return settings.neoecoae$hasComputationHost()
                ? "NO_HOST_ACCEPTS_REQUEST_SOURCE" : "NO_FORMED_ONLINE_COMPUTATION_HOST";
        }
        return "ELIGIBLE";
    }
}
