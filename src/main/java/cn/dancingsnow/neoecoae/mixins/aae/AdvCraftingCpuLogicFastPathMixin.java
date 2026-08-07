package cn.dancingsnow.neoecoae.mixins.aae;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.AdvancedAEExternalCpuJobView;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.ECOExternalCpuFastPathExecutor;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.ECOExternalCpuOutputRoutes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = AdvCraftingCPULogic.class, remap = false, priority = 2000)
public abstract class AdvCraftingCpuLogicFastPathMixin implements ECOExternalCpuOutputRoutes.Sink {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    @Final
    AdvCraftingCPU cpu;

    @Shadow
    public abstract long insert(AEKey what, long amount, Actionable type);

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void neoecoae$registerOutputRoute(
            IEnergyService energyService,
            CraftingService craftingService,
            CallbackInfo ci) {
        if (job != null) {
            ECOExternalCpuOutputRoutes.register(
                    ((AdvExecutingCraftingJobAccessor) (Object) job).neoecoae$getLink().getCraftingID(), this);
        }
    }

    @Override
    public boolean neoecoae$ownsJob(java.util.UUID craftingJobId) {
        return job != null && craftingJobId.equals(
                ((AdvExecutingCraftingJobAccessor) (Object) job).neoecoae$getLink().getCraftingID());
    }

    @Override
    public long neoecoae$insertJobOutput(AEKey what, long amount, Actionable type) {
        return insert(what, amount, type);
    }

    @WrapOperation(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;executeCrafting"
                            + "(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;"
                            + "Lnet/minecraft/world/level/Level;)I"))
    private int neoecoae$dispatchFastPath(
            AdvCraftingCPULogic self,
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
                        new AdvancedAEExternalCpuJobView(job, inventory, cpu))) {
            return 1;
        }
        return original.call(self, remainingOperations, craftingService, energyService, level);
    }
}
