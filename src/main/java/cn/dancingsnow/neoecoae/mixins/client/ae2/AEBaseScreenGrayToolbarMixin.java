package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.VerticalButtonBar;
import cn.dancingsnow.neoecoae.client.gui.crafting.ECOCraftConfirmScreen;
import cn.dancingsnow.neoecoae.client.gui.widget.ECOGrayVerticalButtonBar;
import cn.dancingsnow.neoecoae.client.gui.widget.WorkstationInterfaceButtonBar;
import cn.dancingsnow.neoecoae.client.gui.workstation.LargeIntegratedWorkingStationPatternProviderScreen;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class AEBaseScreenGrayToolbarMixin {
    @ModifyExpressionValue(method = "<init>",
        at = @At(value = "NEW", target = "appeng/client/gui/widgets/VerticalButtonBar"), remap = false)
    private VerticalButtonBar neoecoae$useGrayCraftConfirmToolbar(VerticalButtonBar original) {
        if ((Object) this instanceof LargeIntegratedWorkingStationPatternProviderScreen) return new WorkstationInterfaceButtonBar();
        return (Object) this instanceof ECOCraftConfirmScreen ? new ECOGrayVerticalButtonBar() : original;
    }
}
