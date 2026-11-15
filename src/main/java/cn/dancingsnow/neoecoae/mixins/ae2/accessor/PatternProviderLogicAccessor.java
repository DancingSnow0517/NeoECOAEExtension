package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes AE2's success-side crafting-lock transition to specialized providers. */
@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccessor {
    @Accessor("host")
    PatternProviderLogicHost neoecoae$getHost();

    @Accessor("sendList")
    List<GenericStack> neoecoae$getSendList();

    @Invoker("getActiveSides")
    Set<Direction> neoecoae$getActiveSides();

    @Invoker("onPushPatternSuccess")
    void neoecoae$onPushPatternSuccess(IPatternDetails pattern);
}
