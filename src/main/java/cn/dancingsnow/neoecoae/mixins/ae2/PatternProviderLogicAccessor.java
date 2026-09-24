package cn.dancingsnow.neoecoae.mixins.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccessor {
    @Invoker(value = "onPushPatternSuccess", remap = false)
    void neoecoae$onPushPatternSuccess(IPatternDetails pattern);
}
