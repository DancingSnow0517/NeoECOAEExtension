package cn.dancingsnow.neoecoae.mixins.aae;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.AdvancedAEExternalCpuJobView;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.ECOExternalCpuFastPathExecutor;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.ECOExternalCpuOutputRoutes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false, priority = 2000)
public abstract class AdvCraftingCpuLogicFastPathMixin implements ECOExternalCpuOutputRoutes.Sink {
    @Unique private UUID neoecoae$outputRouteJobId;

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void neoecoae$registerOutputRoute(
            IEnergyService energyService, CraftingService craftingService, CallbackInfo ci) {
        var view = new AdvancedAEExternalCpuJobView(this);
        UUID currentJobId = view.hasJob() ? view.craftingId() : null;
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
        var view = new AdvancedAEExternalCpuJobView(this);
        return view.hasJob() && craftingJobId.equals(view.craftingId());
    }

    @Override
    public long neoecoae$insertJobOutput(AEKey what, long amount, Actionable type) {
        return new AdvancedAEExternalCpuJobView(this).insert(what, amount, type);
    }

    @WrapOperation(
            method = "tickCraftingLogic",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;executeCrafting"
                                    + "(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;"
                                    + "Lnet/minecraft/world/level/Level;)I"))
    private int neoecoae$dispatchFastPath(
            @Coerce Object self,
            int remainingOperations,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            Operation<Integer> original) {
        var view = new AdvancedAEExternalCpuJobView(this);
        if (view.hasJob() && ECOExternalCpuFastPathExecutor.dispatchOne(craftingService, energyService, level, view)) {
            return 1;
        }
        return original.call(self, remainingOperations, craftingService, energyService, level);
    }
}
