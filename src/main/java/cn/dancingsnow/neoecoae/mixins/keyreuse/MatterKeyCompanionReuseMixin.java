package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.*;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.matter_fluid.MatterStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lapis256.apprep.api.ae2.stack.MatterKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.lapis256.apprep.api.ae2.stack.MatterKey$Companion", remap = false)
public abstract class MatterKeyCompanionReuseMixin {
    @Inject(method = "of(Lcom/buuz135/replication/api/IMatterType;)Ldev/lapis256/apprep/api/ae2/stack/MatterKey;",
        at = @At("HEAD"), cancellable = true)
    private void neoecoae$reuseType(IMatterType type, CallbackInfoReturnable<MatterKey> cir) {
        MatterKey key = type instanceof MatterKeyOwner ? (MatterKey) KeyReuse.peek(type) : null;
        if (key != null) cir.setReturnValue(key);
    }

    @Inject(method = "of(Lcom/buuz135/replication/api/matter_fluid/MatterStack;)Ldev/lapis256/apprep/api/ae2/stack/MatterKey;",
        at = @At("HEAD"), cancellable = true)
    private void neoecoae$reuseStack(MatterStack stack, CallbackInfoReturnable<MatterKey> cir) {
        if (KeyReuse.epoch() != null && stack != null && !stack.isEmpty()) {
            MatterKey key = stack.getMatterType() instanceof MatterKeyOwner ? (MatterKey) KeyReuse.peek(stack.getMatterType()) : null;
            if (key != null) cir.setReturnValue(key);
        }
    }

    @WrapOperation(method = "of", at = @At(value = "NEW", target =
        "(Lcom/buuz135/replication/api/IMatterType;Lkotlin/jvm/internal/DefaultConstructorMarker;)Ldev/lapis256/apprep/api/ae2/stack/MatterKey;"))
    private MatterKey neoecoae$publish(IMatterType type, @Coerce Object marker, Operation<MatterKey> original) {
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot;
        if (epoch == null || !(type instanceof MatterKeyOwner) || (slot = KeyReuse.slot(type)) == null) return original.call(type, marker);
        synchronized (slot) {
            MatterKey key = (MatterKey) slot.get(epoch);
            if (key == null) {
                key = original.call(type, marker);
                ((SharedMatterKey) (Object) key).neoecoae$markShared();
                slot.set(epoch, key);
            }
            return key;
        }
    }
}
