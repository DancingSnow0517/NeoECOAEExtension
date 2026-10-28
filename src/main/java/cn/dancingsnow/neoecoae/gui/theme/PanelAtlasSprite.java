package cn.dancingsnow.neoecoae.gui.theme;

import cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource.BuiltinPath;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;

/** Resolves the provider on draw so even cached widget sprites observe addResource replacements. */
final class PanelAtlasSprite extends SpriteTexture {
    private final BuiltinPath atlasPath;
    private final Supplier<IECOTier> tier;
    private final String atlasName;

    PanelAtlasSprite(BuiltinPath atlasPath, ResourceLocation fallback, Supplier<IECOTier> tier, String atlasName) {
        this.atlasPath = atlasPath;
        this.tier = tier;
        this.atlasName = atlasName;
        setImageLocation(fallback);
    }

    SpriteTexture resolve() {
        IECOTier current = tier.get();
        ResourceLocation image = current == null ? null : current.getPanelAtlas(atlasName);
        if (image == null) {
            var resource = NETextures.panelAtlas(atlasPath);
            image = resource != null && resource.getRawTexture() instanceof SpriteTexture sprite
                && !(sprite instanceof PanelAtlasSprite) ? sprite.getImageLocation() : getImageLocation();
        }
        return SpriteTexture.of(image)
            .setSprite(spritePosition.x, spritePosition.y, spriteSize.width, spriteSize.height)
            .setBorder(borderLT.x, borderLT.y, borderRB.x, borderRB.y)
            .setColor(color).setWrapMode(wrapMode);
    }

    @Override
    public SpriteTexture copy() {
        var copy = new PanelAtlasSprite(atlasPath, getImageLocation(), tier, atlasName);
        copy.setSprite(spritePosition.x, spritePosition.y, spriteSize.width, spriteSize.height)
            .setBorder(borderLT.x, borderLT.y, borderRB.x, borderRB.y)
            .setColor(color).setWrapMode(wrapMode);
        copy.copyTransform(this);
        return copy;
    }

    @Override
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY,
        float x, float y, float width, float height, float partialTicks) {
        resolve().draw(graphics, mouseX, mouseY, x, y, width, height, partialTicks);
    }
}
