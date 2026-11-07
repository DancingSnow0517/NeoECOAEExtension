package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import cn.dancingsnow.neoecoae.crafting.ECOComponentInsensitiveProcessingPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PatternDetailsHelper.class)
public abstract class PatternDetailsComponentPolicyMixin {
    @Inject(method = {
        "decodePattern(Lappeng/api/stacks/AEItemKey;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;",
        "decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;"
    }, at = @At("RETURN"), cancellable = true)
    private static void neoecoae$restoreComponentPolicy(CallbackInfoReturnable<IPatternDetails> cir) {
        try {
            var restored = ECOComponentInsensitiveProcessingPattern.restore(cir.getReturnValue());
            if (restored != cir.getReturnValue()) cir.setReturnValue(restored);
        } catch (IllegalArgumentException malformed) {
            cir.setReturnValue(null);
        }
    }
}
