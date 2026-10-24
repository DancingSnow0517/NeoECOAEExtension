package cn.dancingsnow.neoecoae.verification.fixture;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Exercises a deferred wrapper and unknown addon entries in the same transformed menu. */
@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class WrappedCraftingEntryMixin {
    @WrapOperation(method = "planJob", at = @At(value = "INVOKE",
        target = "Lappeng/api/networking/crafting/ICraftingService;beginCraftingCalculation(Lnet/minecraft/world/level/Level;Lappeng/api/networking/crafting/ICraftingSimulationRequester;Lappeng/api/stacks/AEKey;JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;"))
    private Future<ICraftingPlan> independentAddonWrapCalculation(ICraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey goal, long amount, CalculationStrategy strategy,
            Operation<Future<ICraftingPlan>> original) {
        return original.call(service, level, requester, goal, amount, strategy);
    }

    @Unique
    public Future<ICraftingPlan> independentAddonCalculation(ICraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey goal, long amount, CalculationStrategy strategy) {
        return service.beginCraftingCalculation(level, requester, goal, amount, strategy);
    }

    @Unique
    public Future<ICraftingPlan> independentConcreteCalculation(CraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey goal, long amount, CalculationStrategy strategy) {
        return service.beginCraftingCalculation(level, requester, goal, amount, strategy);
    }
}
