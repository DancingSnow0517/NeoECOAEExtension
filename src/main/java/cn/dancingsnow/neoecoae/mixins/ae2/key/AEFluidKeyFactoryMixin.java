package cn.dancingsnow.neoecoae.mixins.ae2.key;

import appeng.api.stacks.AEFluidKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuses only null-tag keys, with the same public factory and content semantics as AE2. */
@Mixin(value = AEFluidKey.class, remap = false)
public abstract class AEFluidKeyFactoryMixin {
    @Inject(method = "of(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEFluidKey;",
        at = @At("HEAD"), cancellable = true)
    private static void neoecoae$cached(Fluid fluid, CompoundTag tag, CallbackInfoReturnable<AEFluidKey> callback) {
        if (tag != null) return;
        Object epoch = KeyReuse.epoch();
        if (epoch == null) return;
        KeyReuseSlot slot = KeyReuse.fluidSlot(epoch, fluid);
        if (slot != null && slot.get(epoch) instanceof AEFluidKey key) callback.setReturnValue(key);
    }

    @Inject(method = "of(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEFluidKey;",
        at = @At("RETURN"), cancellable = true)
    private static void neoecoae$publish(Fluid fluid, CompoundTag tag, CallbackInfoReturnable<AEFluidKey> callback) {
        if (tag != null) return;
        Object epoch = KeyReuse.epoch();
        if (epoch == null) return;
        KeyReuseSlot slot = KeyReuse.fluidSlot(epoch, fluid);
        if (slot == null) return;
        synchronized (slot) {
            if (slot.get(epoch) instanceof AEFluidKey key) callback.setReturnValue(key);
            else slot.set(epoch, callback.getReturnValue());
        }
    }
}
