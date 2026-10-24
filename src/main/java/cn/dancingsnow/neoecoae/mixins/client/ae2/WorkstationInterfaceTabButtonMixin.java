package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.TabButton;
import cn.dancingsnow.neoecoae.client.gui.workstation.WorkstationInterfaceArtwork;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TabButton.class, remap = false)
public abstract class WorkstationInterfaceTabButtonMixin {
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lappeng/client/gui/style/Blitter;dest(II)Lappeng/client/gui/style/Blitter;",
        ordinal = 1, remap = false), remap = true)
    private Blitter neoecoae$centerWorkstationTabIcon(Blitter foreground, int x, int y,
        Operation<Blitter> original) {
        var button = (TabButton) (Object) this;
        if (WorkstationInterfaceArtwork.active() && button.getStyle() == TabButton.Style.BOX) {
            // The workstation uses a 20px frame instead of AE2 15's 22px tab.
            x = button.getX() + (button.getWidth() - foreground.getSrcWidth()) / 2;
            y = button.getY() + (button.getHeight() - foreground.getSrcHeight()) / 2;
        }
        return original.call(foreground, x, y);
    }
}
