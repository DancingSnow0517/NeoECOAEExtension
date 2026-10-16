package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.core.planner.RouteAvailability;
import java.util.List;

/** AE inventory adapter for the version-independent reachable-route proof. */
final class RouteAvailabilityProof {
    private RouteAvailabilityProof() {}
    static boolean goalUnreachable(CompiledNetwork network, PlannerInventorySnapshot stock,
            ECOCancellation cancellation) throws InterruptedException {
        var inventory = stock.toKeyCounter();
        var keys = new java.util.ArrayList<appeng.api.stacks.AEKey>();
        for (var entry : inventory) keys.add(entry.getKey());
        return RouteAvailability.goalUnreachable(network.goal(), network.producers().values().stream()
            .flatMap(List::stream).distinct().toList(), network.emittable(), keys, inventory::get, cn.dancingsnow.neoecoae.crafting.planner.compile.AEPlannerKeyOps.INSTANCE, cancellation);
    }
}
