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
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 2000)
public abstract class CraftingCpuLogicFastPathMixin implements ECOExternalCpuOutputRoutes.Sink {
    @Unique private UUID neoecoae$outputRouteJobId;

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
            IEnergyService energyService, CraftingService craftingService, CallbackInfo ci) {
        UUID currentJobId = job == null
                ? null
                : ((ExecutingCraftingJobAccessor) (Object) job)
                        .neoecoae$getLink()
                        .getCraftingID();
        if (neoecoae$outputRouteJobId != null && !neoecoae$outputRouteJobId.equals(currentJobId)) {
            ECOExternalCpuOutputRoutes.unregister(neoecoae$outputRouteJobId, this);
        }
        neoecoae$outputRouteJobId = currentJobId;
        if (currentJobId != null) {
            ECOExternalCpuOutputRoutes.register(currentJobId, this);
        }
    }

    @Override
    public boolean neoecoae$ownsJob(java.util.UUID craftingJobId) {
        return job != null
                && craftingJobId.equals(((ExecutingCraftingJobAccessor) (Object) job)
                        .neoecoae$getLink()
                        .getCraftingID());
    }

    @Override
    public long neoecoae$insertJobOutput(AEKey what, long amount, Actionable type) {
        return insert(what, amount, type);
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoecoae$dispatchFastPath(
            int remainingOperations,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            CallbackInfoReturnable<Integer> cir) {
        if (job != null
                && ECOExternalCpuFastPathExecutor.dispatchOne(
                        craftingService,
                        energyService,
                        level,
                        new AE2ExternalCpuJobView(job, getInventory(), cluster))) {
            cir.setReturnValue(1);
        }
    }
}
