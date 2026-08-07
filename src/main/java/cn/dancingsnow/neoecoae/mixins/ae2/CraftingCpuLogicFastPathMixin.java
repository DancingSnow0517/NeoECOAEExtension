package cn.dancingsnow.neoecoae.mixins.ae2;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.AE2ExternalCpuJobView;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.external.ECOExternalCpuFastPathExecutor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicFastPathMixin {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @Shadow
    public abstract ListCraftingInventory getInventory();

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
                && !ModList.get().isLoaded("thunderbolt")
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
