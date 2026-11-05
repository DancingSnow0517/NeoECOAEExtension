package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.key.FluxKey", remap = false)
public abstract class FluxKeyReuseMixin {
    @Inject(method = "of", at = @At("HEAD"), cancellable = true)
    private static void neoecoae$reuse(EnergyType type, CallbackInfoReturnable<FluxKey> cir) {
        FluxKey key = (FluxKey) KeyReuse.peek(type);
        if (key != null) cir.setReturnValue(key);
    }

    @WrapOperation(method = "of", at = @At(value = "NEW",
        target = "(Lcom/glodblock/github/appflux/common/me/key/type/EnergyType;)Lcom/glodblock/github/appflux/common/me/key/FluxKey;"))
    private static FluxKey neoecoae$publish(EnergyType type, Operation<FluxKey> original) {
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot;
        if (epoch == null || (slot = KeyReuse.slot(type)) == null) return original.call(type);
        synchronized (slot) {
            FluxKey existing = (FluxKey) slot.get(epoch);
            if (existing != null) return existing;
            FluxKey key = original.call(type);
            slot.set(epoch, key);
            return key;
        }
    }

}
