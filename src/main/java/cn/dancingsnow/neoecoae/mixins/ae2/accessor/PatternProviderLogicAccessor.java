package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes AE2's success-side crafting-lock transition to specialized providers. */
@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccessor {
    @Accessor("sendList")
    List<GenericStack> neoecoae$getSendList();

    @Invoker("onPushPatternSuccess")
    void neoecoae$onPushPatternSuccess(IPatternDetails pattern);
}
