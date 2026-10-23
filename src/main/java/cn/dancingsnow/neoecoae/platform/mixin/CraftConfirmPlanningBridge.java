package cn.dancingsnow.neoecoae.platform.mixin;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;

/** The confirmation menu owns report state for every calculation started through it. */
public interface CraftConfirmPlanningBridge {
    Future<ICraftingPlan> neoecoae$routeEcoPlanningRequest(ICraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey what, long amount, CalculationStrategy strategy);

    static Future<ICraftingPlan> routePlanningRequest(ICraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey what, long amount, CalculationStrategy strategy,
            CraftConfirmPlanningBridge menu) {
        return menu.neoecoae$routeEcoPlanningRequest(service, level, requester, what, amount, strategy);
    }
}
