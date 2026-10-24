package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import cn.dancingsnow.neoecoae.client.gui.crafting.ECOCraftConfirmScreen;
import cn.dancingsnow.neoecoae.client.gui.workstation.WorkstationInterfaceArtwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IconButton.class, remap = false)
public abstract class WorkstationInterfaceIconButtonMixin extends Button {
    protected WorkstationInterfaceIconButtonMixin() {
        super(0, 0, 16, 16, Component.empty(), ignored -> {}, DEFAULT_NARRATION);
    }

    @Shadow protected abstract Icon getIcon();
    @Shadow protected abstract Item getItemOverlay();

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, remap = true)
    private void neoecoae$drawModernWorkstationButton(GuiGraphics graphics, int mouseX, int mouseY,
        float partialTick, CallbackInfo callback) {
        boolean craftingHelp = Minecraft.getInstance().screen instanceof ECOCraftConfirmScreen
            && getIcon() == Icon.HELP;
        if (!WorkstationInterfaceArtwork.active() && !craftingHelp) return;
        var button = (IconButton) (Object) this;
        if (button.isHalfSize()) { width = 8; height = 8; }
        WorkstationInterfaceArtwork.renderButton(button, getIcon(), getItemOverlay(), graphics, mouseX, mouseY);
        callback.cancel();
    }
}
