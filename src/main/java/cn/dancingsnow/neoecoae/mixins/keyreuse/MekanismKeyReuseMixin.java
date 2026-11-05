package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.SharedChemicalKey;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

    @Inject(method = "of", at = @At("HEAD"), cancellable = true)
    private static void neoecoae$reuse(ChemicalStack stack, CallbackInfoReturnable<MekanismKey> cir) {
        if (KeyReuse.epoch() != null && !stack.isEmpty()) {
            MekanismKey key = (MekanismKey) KeyReuse.peek(stack.getChemical());
            if (key != null) cir.setReturnValue(key);
        }
    }

    @Inject(method = "lambda$static$1", at = @At("HEAD"), cancellable = true)
    private static void neoecoae$reuseDecoded(net.minecraft.core.Holder<mekanism.api.chemical.Chemical> chemical,
            CallbackInfoReturnable<MekanismKey> cir) {
        MekanismKey key = (MekanismKey) KeyReuse.peek(chemical.value());
        if (key != null) cir.setReturnValue(key);
    }

    @WrapOperation(method = "*", at = @At(value = "NEW",
        target = "(Lmekanism/api/chemical/ChemicalStack;)Lme/ramidzkh/mekae2/ae2/MekanismKey;"))
    private static MekanismKey neoecoae$publish(ChemicalStack stack, Operation<MekanismKey> original) {
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot;
        if (epoch == null || stack.isEmpty() || (slot = KeyReuse.slot(stack.getChemical())) == null)
            return original.call(stack);
        synchronized (slot) {
            MekanismKey existing = (MekanismKey) slot.get(epoch);
            if (existing != null) return existing;
            stack.setAmount(1);
            MekanismKey key = original.call(stack);
            ((SharedChemicalKey) (Object) key).neoecoae$markShared();
            slot.set(epoch, key);
            return key;
        }
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
