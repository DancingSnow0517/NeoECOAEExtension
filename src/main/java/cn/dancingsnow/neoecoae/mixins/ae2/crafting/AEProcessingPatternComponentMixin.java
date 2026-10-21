package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AEProcessingPattern.class, remap = false)
public abstract class AEProcessingPatternComponentMixin {
    @Inject(method = "pushInputsToExternalInventory", at = @At("HEAD"), cancellable = true)
    private void neoecoae$pushConcreteInputs(KeyCounter[] inputs, IPatternDetails.PatternInputSink sink,
            CallbackInfo ci) {
        if (ECOProcessingComponentMatching.pushInputs((AEProcessingPattern) (Object) this, inputs, sink)) {
            ci.cancel();
        }
    }
}
