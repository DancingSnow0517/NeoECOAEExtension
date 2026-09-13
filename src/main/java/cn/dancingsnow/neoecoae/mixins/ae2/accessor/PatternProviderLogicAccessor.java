package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes AE2's success-side crafting-lock transition to specialized providers. */
@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccessor {
    @Invoker("onPushPatternSuccess")
    void neoecoae$onPushPatternSuccess(IPatternDetails pattern);
}
