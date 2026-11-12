package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.*;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.matter_fluid.MatterStack;
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

}
