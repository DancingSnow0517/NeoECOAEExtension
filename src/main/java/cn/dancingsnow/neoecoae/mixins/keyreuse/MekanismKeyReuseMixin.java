package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.SharedChemicalKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.ramidzkh.mekae2.ae2.MekanismKey", remap = false)
public abstract class MekanismKeyReuseMixin implements SharedChemicalKey {
    @Shadow @Final private ChemicalStack stack;
    @Unique private boolean neoecoae$shared;
    @Unique private int neoecoae$hash;

    @Override
    public final void neoecoae$markShared() {
        neoecoae$hash = 31 + stack.getChemical().hashCode();
        neoecoae$shared = true;
    }

    @Inject(method = "getStack", at = @At("HEAD"), cancellable = true)
    private void neoecoae$protectSharedStack(CallbackInfoReturnable<ChemicalStack> cir) {
        if (neoecoae$shared) cir.setReturnValue(stack.copy());
    }

    @Inject(method = "hashCode", at = @At("HEAD"), cancellable = true)
    private void neoecoae$cachedHash(CallbackInfoReturnable<Integer> cir) {
        if (neoecoae$shared) cir.setReturnValue(neoecoae$hash);
    }
}
