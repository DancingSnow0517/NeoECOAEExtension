package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternAccessTermScreen.class)
public abstract class PatternAccessTermScreenMixin {
    @Unique
    private boolean neoecoae$patternListNeedsRefresh;

    @Shadow
    private void refreshList() {
        throw new AssertionError();
    }

    @WrapOperation(method = {"clear", "postFullUpdate"}, at = @At(value = "INVOKE",
        target = "Lappeng/client/gui/me/patternaccess/PatternAccessTermScreen;refreshList()V"))
    private void neoecoae$deferPatternListRefresh(PatternAccessTermScreen<?> screen, Operation<Void> original) {
        neoecoae$patternListNeedsRefresh = true;
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void neoecoae$refreshAfterPatternBatches(CallbackInfo ci) {
        if (neoecoae$patternListNeedsRefresh) {
            neoecoae$patternListNeedsRefresh = false;
            refreshList();
        }
    }
}
