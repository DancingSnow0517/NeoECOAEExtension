package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.VerticalButtonBar;
import cn.dancingsnow.neoecoae.client.ECOCraftConfirmScreen;
import cn.dancingsnow.neoecoae.client.ECOGrayVerticalButtonBar;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AEBaseScreen.class)
public abstract class AEBaseScreenGrayToolbarMixin {
    @ModifyExpressionValue(method = "<init>",
        at = @At(value = "NEW", target = "appeng/client/gui/widgets/VerticalButtonBar"), remap = false)
    private VerticalButtonBar neoecoae$useGrayCraftConfirmToolbar(VerticalButtonBar original) {
        return (Object) this instanceof ECOCraftConfirmScreen ? new ECOGrayVerticalButtonBar() : original;
    }
}
