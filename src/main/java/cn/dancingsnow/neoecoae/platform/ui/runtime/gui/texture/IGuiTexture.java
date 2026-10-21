package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import net.minecraft.client.gui.GuiGraphics;

public interface IGuiTexture {
    void draw(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width, float height, float partialTicks);
    default void draw(GUIContext context, float x, float y, float width, float height) {
        draw(context.graphics, context.mouseX, context.mouseY, x, y, width, height, context.partialTick);
    }
    default IGuiTexture getRawTexture() { return this; }
    default IGuiTexture copy() { return this; }
    IGuiTexture EMPTY = (graphics, mouseX, mouseY, x, y, width, height, partialTicks) -> {};
    static IGuiTexture group(IGuiTexture... textures) {
        return (graphics, mouseX, mouseY, x, y, width, height, partialTicks) -> {
            for (var texture : textures) if (texture != null) texture.draw(graphics, mouseX, mouseY, x, y, width, height, partialTicks);
        };
    }
}
