package cn.dancingsnow.neoecoae.mixins.compat.merequester;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** ME Requester has no confirmation menu through which it could opt into ECO's cycle planner. */
@Pseudo
@Mixin(targets = "com.almostreliable.merequester.requester.status.RequestState", remap = false)
public class MERequesterPlanningMixin {
    @WrapOperation(method = "handle", at = @At(value = "INVOKE", target =
        "Lappeng/api/networking/crafting/ICraftingService;beginCraftingCalculation("
            + "Lnet/minecraft/world/level/Level;Lappeng/api/networking/crafting/ICraftingSimulationRequester;"
            + "Lappeng/api/stacks/AEKey;JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;"))
    Future<ICraftingPlan> neoecoae$planWithEco(ICraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey what, long amount, CalculationStrategy strategy,
            Operation<Future<ICraftingPlan>> original) {
        var source = requester.getActionSource();
        if (source != null && service instanceof ECOCraftingNetworkSettings settings
                && settings.neoecoae$isFastPlannerEnabled() && settings.neoecoae$hasComputationHost(source)) {
            var node = requester.getGridNode();
            if (node != null) {
                var grid = node.getGrid();
                if (grid != null && grid.getCraftingService() == service) {
                    // Preserve CRAFT_LESS and the machine source. Submission and delivery still use
                    // ME Requester's original ICraftingRequester/CraftingLink lifecycle.
                    return ECOPlanningService.begin(level, grid, source, what, amount, strategy,
                        ECOPlannerOptions.from(settings));
                }
            }
        }
        return original.call(service, level, requester, what, amount, strategy);
    }
}
