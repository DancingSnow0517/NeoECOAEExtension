package cn.dancingsnow.neoecoae.mixins.aae;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.AdvancedAEExternalCpuJobView;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.ECOExternalCpuFastPathExecutor;
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

@Pseudo
@Mixin(value = AdvCraftingCPULogic.class, remap = false, priority = 2000)
public abstract class AdvCraftingCpuLogicFastPathMixin {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    @Final
    AdvCraftingCPU cpu;

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
