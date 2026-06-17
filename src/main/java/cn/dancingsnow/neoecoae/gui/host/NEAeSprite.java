package cn.dancingsnow.neoecoae.gui.host;

import appeng.core.AppEng;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.resources.ResourceLocation;

public record NEAeSprite(int x, int y, int width, int height) {
    static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/states.png");
    static final ResourceLocation BACKGROUND_TEXTURE = AppEng.makeId("textures/guis/background.png");
    static final int TEXTURE_WIDTH = 256;
    static final int TEXTURE_HEIGHT = 256;

    static final NEAeSprite TYPE_FILTER_ALL = new NEAeSprite(160, 16);
    public static final NEAeSprite HELP = new NEAeSprite(176, 0);
    static final NEAeSprite BACK = new NEAeSprite(96, 16);
    static final NEAeSprite BACKGROUND_DUST = new NEAeSprite(240, 32);
    static final NEAeSprite SUBSTITUTION_ENABLED = new NEAeSprite(64, 48);
    static final NEAeSprite SUBSTITUTION_DISABLED = new NEAeSprite(112, 48);
    static final NEAeSprite FLUID_SUBSTITUTION_ENABLED = new NEAeSprite(128, 48);
    static final NEAeSprite FLUID_SUBSTITUTION_DISABLED = new NEAeSprite(144, 48);
    public static final NEAeSprite PRIORITY = new NEAeSprite(144, 64);
    static final NEAeSprite BACKGROUND_WIRELESS_TERM = new NEAeSprite(240, 64);
    static final NEAeSprite LEVEL_ENERGY = new NEAeSprite(48, 80);
    static final NEAeSprite BACKGROUND_TRASH = new NEAeSprite(240, 80);
    static final NEAeSprite CONDENSER_OUTPUT_TRASH = new NEAeSprite(0, 112);
    static final NEAeSprite TOOLBAR_BUTTON_BACKGROUND = new NEAeSprite(176, 128, 18, 20);
    static final NEAeSprite TOOLBAR_BUTTON_BACKGROUND_FOCUS = new NEAeSprite(194, 128, 18, 20);
    static final NEAeSprite TOOLBAR_BUTTON_BACKGROUND_HOVER = new NEAeSprite(212, 128, 18, 20);
    static final NEAeSprite CRAFT_HAMMER = new NEAeSprite(48, 144);
    static final NEAeSprite POWER_UNIT_AE = new NEAeSprite(0, 160);
    static final NEAeSprite TAB_BUTTON_BACKGROUND = new NEAeSprite(160, 192, 20, 20);
    static final NEAeSprite TAB_BUTTON_BACKGROUND_FOCUS = new NEAeSprite(160, 224, 22, 22);
    static final NEAeSprite SLOT_BACKGROUND = new NEAeSprite(192, 192, 18, 18);

    NEAeSprite(int x, int y) {
        this(x, y, 16, 16);
    }

    public IGuiTexture texture() {
        return SpriteTexture.of(TEXTURE).setSprite(x, y, width, height);
    }

    void draw(GUIContext context, float x, float y) {
        context.graphics.blit(
            TEXTURE,
            Math.round(x),
            Math.round(y),
            this.x,
            this.y,
            width,
            height,
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT
        );
    }

    void draw(GUIContext context, float x, float y, int width, int height) {
        context.graphics.blit(
            TEXTURE,
            Math.round(x),
            Math.round(y),
            width,
            height,
            this.x,
            this.y,
            this.width,
            this.height,
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT
        );
    }
}
