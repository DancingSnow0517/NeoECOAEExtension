package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.compat.ae2.NeoECOCraftingServiceBridge;
import com.google.common.collect.ImmutableSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds ECO CPUs after compatibility mods have finished rebuilding AE2's CPU set. */
@Mixin(value = CraftingService.class, priority = 900, remap = false)
public abstract class CraftingServiceCpuListMixin {
    @Shadow
    @Final
    private IGrid grid;

    @Inject(method = "getCpus", at = @At("RETURN"), cancellable = true)
    private void neoecoae$getCpus(CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir) {
        cir.setReturnValue(NeoECOCraftingServiceBridge.getCpus(this.grid, cir.getReturnValue()));
    }
}
