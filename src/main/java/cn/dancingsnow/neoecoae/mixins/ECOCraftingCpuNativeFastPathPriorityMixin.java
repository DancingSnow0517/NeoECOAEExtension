package cn.dancingsnow.neoecoae.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
import net.minecraft.world.level.Level;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps ECO CPUs on NeoECO's native verified FastPath instead of an external
 * provider wrapper that treats available threads as a per-lane copy limit.
 */
@Mixin(value = ECOCraftingCPULogic.class, priority = 2000)
public abstract class ECOCraftingCpuNativeFastPathPriorityMixin {
    @WrapOperation(
        method = "tickCraftingLogic",
        at = @At(
            value = "INVOKE",
            target = "Lcn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic;executeCrafting"
                + "(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;"
                + "Lnet/minecraft/world/level/Level;)I"
        ),
        remap = false,
        order = 2000
    )
    private static int neoecoae$runNativeEcoFastPath(
        ECOCraftingCPULogic logic,
        int remainingOperations,
        CraftingService craftingService,
        IEnergyService energyService,
        Level level,
        Operation<Integer> original
    ) {
        // Intentionally bypass lower-priority provider wrappers for ECO's own CPU.
        return logic.executeCrafting(remainingOperations, craftingService, energyService, level);
    }
}
