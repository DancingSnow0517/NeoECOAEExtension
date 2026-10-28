package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Shared empty texture metadata; rendering exists only on the physical client. */
final class EmptyTexture implements IGuiTexture {
    @Override
    @OnlyIn(Dist.CLIENT)
    public void draw(GuiGraphics graphics, float mouseX, float mouseY, float x,
        float y, float width, float height, float partialTicks) {}
}
