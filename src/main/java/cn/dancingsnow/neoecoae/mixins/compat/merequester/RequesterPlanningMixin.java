package cn.dancingsnow.neoecoae.mixins.compat.merequester;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOAutoCraftingPlanning;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.almostreliable.merequester.requester.status.RequestState", remap = false)
public abstract class RequesterPlanningMixin {
    @WrapOperation(method = "handle", at = @At(value = "INVOKE", target =
            "Lappeng/api/networking/crafting/ICraftingService;beginCraftingCalculation(Lnet/minecraft/world/level/Level;Lappeng/api/networking/crafting/ICraftingSimulationRequester;Lappeng/api/stacks/AEKey;JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;"))
    private Future<ICraftingPlan> neoecoae$planAutomaticOrder(ICraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey what, long amount, CalculationStrategy strategy,
            Operation<Future<ICraftingPlan>> original) {
        return ECOAutoCraftingPlanning.begin(service, level, requester, what, amount, strategy,
                () -> original.call(service, level, requester, what, amount, strategy));
    }
}
