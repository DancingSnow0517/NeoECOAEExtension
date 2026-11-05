package cn.dancingsnow.neoecoae.mixins.keyreuse;

import appeng.api.stacks.AEFluidKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AEFluidKey.class)
public abstract class AEFluidKeyReuseMixin {
    @Inject(method = "of(Lnet/neoforged/neoforge/fluids/FluidStack;)Lappeng/api/stacks/AEFluidKey;",
        at = @At("HEAD"), cancellable = true)
    private static void neoecoae$reuse(FluidStack stack, CallbackInfoReturnable<AEFluidKey> cir) {
        if (!stack.isEmpty() && stack.getComponentsPatch().isEmpty()) {
            AEFluidKey key = (AEFluidKey) KeyReuse.peek(stack.getFluid());
            if (key != null) cir.setReturnValue(key);
        }
    }

    @Inject(method = "of(Lnet/minecraft/world/level/material/Fluid;)Lappeng/api/stacks/AEFluidKey;",
        at = @At("HEAD"), cancellable = true)
    private static void neoecoae$reuseFluid(Fluid fluid, CallbackInfoReturnable<AEFluidKey> cir) {
        AEFluidKey key = (AEFluidKey) KeyReuse.peek(fluid);
        if (key != null) cir.setReturnValue(key);
    }

    @Inject(method = "lambda$static$4", at = @At("HEAD"), cancellable = true, remap = false)
    private static void neoecoae$reuseDecoded(Holder<Fluid> fluid, DataComponentPatch patch,
            CallbackInfoReturnable<AEFluidKey> cir) {
        if (patch.isEmpty()) {
            AEFluidKey key = (AEFluidKey) KeyReuse.peek(fluid.value());
            if (key != null) cir.setReturnValue(key);
        }
    }

    @WrapOperation(method = "*", at = @At(value = "NEW",
        target = "(Lnet/neoforged/neoforge/fluids/FluidStack;)Lappeng/api/stacks/AEFluidKey;"))
    private static AEFluidKey neoecoae$publish(FluidStack stack, Operation<AEFluidKey> original) {
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot;
        if (epoch == null || stack.isEmpty() || !stack.getComponentsPatch().isEmpty()
                || (slot = KeyReuse.slot(stack.getFluid())) == null) return original.call(stack);
        synchronized (slot) {
            AEFluidKey existing = (AEFluidKey) slot.get(epoch);
            if (existing != null) return existing;
            stack.setAmount(1);
            AEFluidKey key = original.call(stack);
            slot.set(epoch, key);
            return key;
        }
    }
}
