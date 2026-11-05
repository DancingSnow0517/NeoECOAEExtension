package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.helpers.MultiCraftingTracker;
import cn.dancingsnow.neoecoae.crafting.planner.ECOAutoCraftingPlanning;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiCraftingTracker.class)
public abstract class MultiCraftingTrackerMixin {
    @Shadow
    private Future<ICraftingPlan>[] jobs;

    @WrapOperation(method = "handleCrafting", at = @At(value = "INVOKE", target =
            "Lappeng/api/networking/crafting/ICraftingService;beginCraftingCalculation(Lnet/minecraft/world/level/Level;Lappeng/api/networking/crafting/ICraftingSimulationRequester;Lappeng/api/stacks/AEKey;JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;"))
    private Future<ICraftingPlan> neoecoae$planAutomaticOrder(ICraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey what, long amount, CalculationStrategy strategy,
            Operation<Future<ICraftingPlan>> original) {
        return ECOAutoCraftingPlanning.begin(service, level, requester, what, amount, strategy,
                () -> original.call(service, level, requester, what, amount, strategy));
    }

    @WrapOperation(method = "handleCrafting", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/Future;get()Ljava/lang/Object;"))
    private Object neoecoae$releaseFailedCalculation(Future<?> future, Operation<Object> original,
            @Local(argsOnly = true) int slot) throws Throwable {
        try {
            return original.call(future);
        } catch (Throwable failure) {
            if (!(failure instanceof ExecutionException || failure instanceof CancellationException
                    || failure instanceof InterruptedException)) throw failure;
            jobs[slot] = null;
            boolean hasJob = false;
            for (var job : jobs) if (job != null) { hasJob = true; break; }
            if (!hasJob) jobs = null;
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }
}
