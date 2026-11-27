package cn.dancingsnow.neoecoae.api;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** A 13x18 CPU tier glyph for crafting confirmation and status headers, independent of the 10x10 CPU overlay. */
public record ECOCpuTierGlyph(ResourceLocation texture, int u, int v, int textureWidth, int textureHeight) {
    public static final int WIDTH = 13;
    public static final int HEIGHT = 18;

    /** Uses an entire standalone 13x18 texture. */
    public ECOCpuTierGlyph(ResourceLocation texture) {
        this(texture, 0, 0, WIDTH, HEIGHT);
    }

    public ECOCpuTierGlyph {
        Objects.requireNonNull(texture, "texture");
        if (u < 0 || v < 0 || textureWidth < WIDTH || textureHeight < HEIGHT
                || u > textureWidth - WIDTH || v > textureHeight - HEIGHT) {
            throw new IllegalArgumentException("CPU tier glyph must fit a 13x18 region inside its texture");
        }
    }
}
