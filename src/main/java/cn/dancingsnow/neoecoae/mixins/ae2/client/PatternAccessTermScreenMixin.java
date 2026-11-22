package cn.dancingsnow.neoecoae.mixins.ae2.client;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = PatternAccessTermScreen.class, remap = false)
public abstract class PatternAccessTermScreenMixin {
    @Shadow @Final private Map<?, ?> cachedSearches;
    @Unique private boolean neoecoae$patternListNeedsRefresh;

    @Shadow
    private void refreshList() {
        throw new AssertionError();
    }

    @WrapOperation(method = {"clear", "postFullUpdate"}, at = @At(value = "INVOKE",
        target = "Lappeng/client/gui/me/patternaccess/PatternAccessTermScreen;refreshList()V"))
    private void neoecoae$deferPatternListRefresh(PatternAccessTermScreen<?> screen, Operation<Void> original) {
        neoecoae$patternListNeedsRefresh = true;
    }

    @Inject(method = "postIncrementalUpdate", at = @At("TAIL"))
    private void neoecoae$refreshCachedSearch(CallbackInfo ci) {
        if (!cachedSearches.isEmpty()) {
            cachedSearches.clear();
            neoecoae$patternListNeedsRefresh = true;
        }
    }

    @Inject(method = "updateBeforeRender", at = @At("HEAD"))
    private void neoecoae$refreshAfterPatternBatches(CallbackInfo ci) {
        if (neoecoae$patternListNeedsRefresh) {
            neoecoae$patternListNeedsRefresh = false;
            refreshList();
        }
    }
}
