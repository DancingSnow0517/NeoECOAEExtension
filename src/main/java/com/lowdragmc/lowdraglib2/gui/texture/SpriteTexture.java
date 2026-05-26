package com.lowdragmc.lowdraglib2.gui.texture;

import com.lowdragmc.lowdraglib2.math.Size;
import net.minecraft.resources.ResourceLocation;

public class SpriteTexture implements IGuiTexture {
    public enum WrapMode {
        REPEAT,
        CLAMP
    }

    private final ResourceLocation texture;

    private SpriteTexture(ResourceLocation texture) {
        this.texture = texture;
    }

    public static SpriteTexture of(ResourceLocation texture) {
        return new SpriteTexture(texture);
    }

    public SpriteTexture setSpriteSize(Size size) {
        return this;
    }

    public SpriteTexture setBorder(int left, int top, int right, int bottom) {
        return this;
    }

    public SpriteTexture setWrapMode(WrapMode mode) {
        return this;
    }

    public SpriteTexture setSprite(int x, int y, int width, int height) {
        return this;
    }

    public ResourceLocation texture() {
        return texture;
    }
}
