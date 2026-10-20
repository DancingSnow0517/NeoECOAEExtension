package cn.dancingsnow.neoecoae.platform.ui;

import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** LDLib2 HOVER_ROLL semantics using LDLib1's real hover-only ROLL renderer. */
public final class NEHoverTextWidget extends Widget {
    private final String translationKey;
    private TextTexture texture;

    public NEHoverTextWidget(String translationKey, int width, int height) {
        super(0, 0, width, height);
        this.translationKey = translationKey;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (texture == null) {
            texture = new TextTexture(translationKey).setType(TextTexture.TextType.ROLL).setSupplier(() -> translationKey);
        }
        if (texture.width != getSize().width) texture.setWidth(getSize().width);
        var position = getPosition();
        texture.draw(graphics, mouseX, mouseY, position.x, position.y, getSize().width, getSize().height);
    }
}
