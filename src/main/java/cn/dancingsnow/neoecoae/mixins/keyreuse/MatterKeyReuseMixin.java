package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.*;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.matter_fluid.MatterStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lapis256.apprep.api.ae2.stack.MatterKey;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.lapis256.apprep.api.ae2.stack.MatterKey", remap = false)
public abstract class MatterKeyReuseMixin implements SharedMatterKey {
    @Shadow @Final private MatterStack stack;
    @Unique private boolean neoecoae$shared;
    @Override public final void neoecoae$markShared() { neoecoae$shared = true; }

    @Inject(method = "getStack", at = @At("HEAD"), cancellable = true)
    private void neoecoae$protectStack(CallbackInfoReturnable<MatterStack> cir) {
        if (neoecoae$shared) cir.setReturnValue(stack.copy());
    }

    @Inject(method = "MAP_CODEC$lambda$0$_init___proxy", at = @At("HEAD"), cancellable = true)
    private static void neoecoae$reuseDecoded(IMatterType type, CallbackInfoReturnable<MatterKey> cir) {
        MatterKey key = type instanceof MatterKeyOwner ? (MatterKey) KeyReuse.peek(type) : null;
        if (key != null) cir.setReturnValue(key);
    }

    @WrapOperation(method = "MAP_CODEC$lambda$0$_init___proxy", at = @At(value = "NEW",
        target = "(Lcom/buuz135/replication/api/IMatterType;)Ldev/lapis256/apprep/api/ae2/stack/MatterKey;"))
    private static MatterKey neoecoae$publishDecoded(IMatterType type, Operation<MatterKey> original) {
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot;
        if (epoch == null || !(type instanceof MatterKeyOwner) || (slot = KeyReuse.slot(type)) == null) return original.call(type);
        synchronized (slot) {
            MatterKey key = (MatterKey) slot.get(epoch);
            if (key == null) {
                key = original.call(type);
                ((SharedMatterKey) (Object) key).neoecoae$markShared();
                slot.set(epoch, key);
            }
            return key;
        }
    }
}
