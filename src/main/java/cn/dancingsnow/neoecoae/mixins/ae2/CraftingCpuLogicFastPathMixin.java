package cn.dancingsnow.neoecoae.mixins.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.AE2ExternalCpuJobView;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.ECOExternalCpuFastPathExecutor;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.ECOExternalCpuOutputRoutes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 2000)
public abstract class CraftingCpuLogicFastPathMixin implements ECOExternalCpuOutputRoutes.Sink {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @Shadow
    public abstract ListCraftingInventory getInventory();

    @Shadow
    public abstract long insert(AEKey what, long amount, Actionable type);

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void neoecoae$registerOutputRoute(
            IEnergyService energyService,
            CraftingService craftingService,
            CallbackInfo ci) {
        if (job != null) {
            ECOExternalCpuOutputRoutes.register(
                    ((ExecutingCraftingJobAccessor) (Object) job).neoecoae$getLink().getCraftingID(), this);
        }
    }

    @Override
    public boolean neoecoae$ownsJob(java.util.UUID craftingJobId) {
        return job != null && craftingJobId.equals(
                ((ExecutingCraftingJobAccessor) (Object) job).neoecoae$getLink().getCraftingID());
    }

    @Override
    public long neoecoae$insertJobOutput(AEKey what, long amount, Actionable type) {
        return insert(what, amount, type);
    }

    @WrapOperation(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/execution/CraftingCpuLogic;executeCrafting"
                            + "(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;"
                            + "Lnet/minecraft/world/level/Level;)I"))
    private int neoecoae$dispatchFastPath(
            CraftingCpuLogic self,
            int remainingOperations,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            Operation<Integer> original) {
        if (job != null
                && ECOExternalCpuFastPathExecutor.dispatchOne(
                        craftingService,
                        energyService,
                        level,
                        new AE2ExternalCpuJobView(job, getInventory(), cluster))) {
            return 1;
        }
        return original.call(self, remainingOperations, craftingService, energyService, level);
    }
}
