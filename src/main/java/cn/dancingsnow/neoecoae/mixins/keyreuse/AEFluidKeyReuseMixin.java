package cn.dancingsnow.neoecoae.mixins.keyreuse;

import appeng.api.stacks.AEFluidKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEFluidKey.class, remap = false)
public abstract class AEFluidKeyReuseMixin {
    @Inject(method = "of(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEFluidKey;",
        at = @At("HEAD"), cancellable = true)
    private static void neoecoae$reuse(Fluid fluid, CompoundTag tag, CallbackInfoReturnable<AEFluidKey> cir) {
        if (tag == null) {
            AEFluidKey key = (AEFluidKey) KeyReuse.peek(fluid);
            if (key != null) cir.setReturnValue(key);
        }
    }

    @WrapOperation(method = "*", at = @At(value = "NEW",
        target = "(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEFluidKey;"))
    private static AEFluidKey neoecoae$publish(Fluid fluid, CompoundTag tag, Operation<AEFluidKey> original) {
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot;
        if (epoch == null || tag != null || (slot = KeyReuse.slot(fluid)) == null) return original.call(fluid, tag);
        synchronized (slot) {
            AEFluidKey existing = (AEFluidKey) slot.get(epoch);
            if (existing != null) return existing;
            AEFluidKey key = original.call(fluid, tag);
            slot.set(epoch, key);
            return key;
        }
    }
}
