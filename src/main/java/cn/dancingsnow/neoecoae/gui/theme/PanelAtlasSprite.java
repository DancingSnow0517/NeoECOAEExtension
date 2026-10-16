package cn.dancingsnow.neoecoae.gui.theme;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Resolves the provider on draw so even cached widget sprites observe addResource replacements. */
final class PanelAtlasSprite extends SpriteTexture {
    private final BuiltinPath atlasPath;

    PanelAtlasSprite(BuiltinPath atlasPath, ResourceLocation fallback) {
        this.atlasPath = atlasPath;
        setImageLocation(fallback);
    }

    SpriteTexture resolve() {
        var resource = NETextures.panelAtlas(atlasPath);
        var image = resource != null && resource.getRawTexture() instanceof SpriteTexture sprite
            && !(sprite instanceof PanelAtlasSprite) ? sprite.getImageLocation() : getImageLocation();
        return SpriteTexture.of(image)
            .setSprite(spritePosition.x, spritePosition.y, spriteSize.width, spriteSize.height)
            .setBorder(borderLT.x, borderLT.y, borderRB.x, borderRB.y)
            .setColor(color).setWrapMode(wrapMode);
    }

    @Override
    public SpriteTexture copy() {
        var copy = new PanelAtlasSprite(atlasPath, getImageLocation());
        copy.setSprite(spritePosition.x, spritePosition.y, spriteSize.width, spriteSize.height)
            .setBorder(borderLT.x, borderLT.y, borderRB.x, borderRB.y)
            .setColor(color).setWrapMode(wrapMode);
        copy.copyTransform(this);
        return copy;
    }

    @Override
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY,
        float x, float y, float width, float height, float partialTicks) {
        resolve().draw(graphics, mouseX, mouseY, x, y, width, height, partialTicks);
    }
}
