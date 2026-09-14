package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.compat.ae2.NeoECOCraftingServiceBridge;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Adds ECO CPUs after compatibility mods have finished rebuilding AE2's CPU set. */
@Mixin(value = CraftingService.class, priority = 800, remap = false)
public abstract class CraftingServiceCpuListMixin {
    @Shadow
    @Final
    private IGrid grid;

    // A cancellable RETURN injection can skip GTLCore's RETURN handler at the same site.
    // Wrap the completed method, including early returns injected by other CPU providers.
    @WrapMethod(method = "getCpus")
    private ImmutableSet<ICraftingCPU> neoecoae$getCpus(Operation<ImmutableSet<ICraftingCPU>> original) {
        return NeoECOCraftingServiceBridge.getCpus(this.grid, original.call());
    }
}
