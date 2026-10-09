package cn.dancingsnow.neoecoae.gui.theme;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.texture.UIResourceTexture;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.math.Size;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;


@SuppressWarnings("unused")
public class NETextures {
    private static final Logger LOGGER = LoggerFactory.getLogger(NETextures.class);
    public static IGuiTexture BACKGROUND = SpriteTexture.of(NeoECOAE.id("textures/gui/common/background.png"))
        .setSpriteSize(Size.of(16, 16))
        .setBorder(2, 2, 2, 4);
    public static IGuiTexture INVENTORY_BORDER = SpriteTexture.of(NeoECOAE.id("textures/gui/common/inventory_border.png"))
        .setSpriteSize(Size.of(16, 16))
        .setBorder(1, 1, 1, 1);
    public static IGuiTexture CARD_BACKGROUND = SpriteTexture.of(NeoECOAE.id("textures/gui/common/card_background.png"))
        .setSpriteSize(Size.of(16, 16))
        .setBorder(3, 3, 3, 3);
    public static IGuiTexture NBT_BENCH = SpriteTexture.of(NeoECOAE.id("textures/gui/computation/nbtbench.png"))
        .setSprite(0, 0, 176, 253);

    private static final net.minecraft.resources.ResourceLocation LDLIB_ATLAS =
        NeoECOAE.id("textures/gui/vendor/ldlib/gdp_styles.png");
    public static IGuiTexture RECT_RD = SpriteTexture.of(LDLIB_ATLAS)
        .setSprite(1, 29, 13, 13).setBorder(4, 4, 4, 4);
    public static IGuiTexture RECT_RD_LIGHT = SpriteTexture.of(LDLIB_ATLAS)
        .setSprite(1, 15, 13, 13).setBorder(4, 4, 4, 4);
    public static IGuiTexture RECT_RD_DARK = SpriteTexture.of(LDLIB_ATLAS)
        .setSprite(1, 43, 13, 13).setBorder(4, 4, 4, 4);
    public static IGuiTexture RECT_RD_T = SpriteTexture.of(LDLIB_ATLAS)
        .setSprite(15, 29, 13, 13).setBorder(4, 4, 4, 4);
    public static IGuiTexture HOST_PANEL_BORDER = SpriteTexture.of(LDLIB_ATLAS)
        .setSprite(205, 154, 16, 16).setBorder(6, 6, 6, 6);

    public static IGuiTexture BUTTON = SpriteTexture.of(NeoECOAE.id("textures/gui/common/button.png"))
        .setSpriteSize(Size.of(20, 20))
        .setBorder(2, 2, 2, 5);
    public static IGuiTexture BUTTON_HIGHLIGHTED = SpriteTexture.of(NeoECOAE.id("textures/gui/common/button_highlighted.png"))
        .setSpriteSize(Size.of(20, 20))
        .setBorder(2, 3, 2, 5);

    // Priority controls use the bundled AE2 artwork in our own namespace.
    public static IGuiTexture AE2_BUTTON = SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/sprites/button.png"))
        .setSprite(0, 0, 200, 20)
        .setBorder(3);
    public static IGuiTexture AE2_BUTTON_HIGHLIGHTED =
        SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/sprites/button_highlighted.png"))
            .setSprite(0, 0, 200, 20)
            .setBorder(3);
    public static IGuiTexture AE2_BUTTON_DISABLED =
        SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/sprites/button_disabled.png"))
            .setSprite(0, 0, 200, 20)
            .setBorder(3);
    public static IGuiTexture PRIORITY_BACKGROUND =
        SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/priority.png"))
            .setSprite(0, 0, 176, 125);
    public static IGuiTexture PRIORITY_TEXT_FIELD =
        SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/text_field.png"))
            .setSprite(0, 0, 128, 12)
            .setBorder(1, 0, 1, 0);
    public static IGuiTexture PRIORITY_TEXT_FIELD_DISABLED =
        SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/text_field.png"))
            .setSprite(0, 12, 128, 12)
            .setBorder(1, 0, 1, 0);
    public static IGuiTexture PRIORITY_TEXT_FIELD_FOCUS =
        SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/text_field.png"))
            .setSprite(0, 24, 128, 12)
            .setBorder(1, 0, 1, 0);
    public static IGuiTexture AE2_TOOLBOX =
        SpriteTexture.of(NeoECOAE.id("textures/gui/workstation/eco_extra_panels.png"))
            .setSprite(0, 0, 61, 66);

    public static IGuiTexture SWITCH_OFF = SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/checkbox.png"))
        .setSprite(0,28, 22, 12);
    public static IGuiTexture SWITCH_OFF_HOVER = SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/checkbox.png"))
        .setSprite(22, 28, 22, 12);
    public static IGuiTexture SWITCH_ON = SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/checkbox.png"))
        .setSprite(0, 40, 22, 12);
    public static IGuiTexture SWITCH_ON_HOVER = SpriteTexture.of(NeoECOAE.id("textures/gui/vendor/ae2/checkbox.png"))
        .setSprite(22, 40, 22, 12);

    public static IGuiTexture AE_SCROLLBAR_TRACK = CARD_BACKGROUND;
    public static IGuiTexture AE_SCROLLBAR_THUMB = BUTTON;

    public static IGuiTexture ITEM_SLOT = SpriteTexture.of(NeoECOAE.id("textures/gui/common/slot.png"))
        .setSpriteSize(Size.of(18, 18))
        .setBorder(1, 2, 1, 1);

    public static IGuiTexture AE2_SLOT_HIGHLIGHT = new IGuiTexture() {
        @Override
        public void draw(
            GuiGraphics graphics,
            float mouseX,
            float mouseY,
            float x,
            float y,
            float width,
            float height,
            float partialTicks
        ) {
            drawAE2SlotHighlight(graphics, x, y, width, height);
        }

        @Override
        public void draw(GUIContext context, float x, float y, float width, float height) {
            // LDLib renders each slot independently. Drawing an outline outside the
            // current 16x16 content area immediately would let adjacent slots cover it.
            context.postRendering(postContext ->
                drawAE2SlotHighlight(postContext.graphics, x, y, width, height));
        }

        @Override
        public IGuiTexture copy() {
            return this;
        }
    };

    private static void drawAE2SlotHighlight(
        GuiGraphics graphics,
        float x,
        float y,
        float width,
        float height
    ) {
        int left = Mth.floor(x);
        int top = Mth.floor(y);
        int right = Mth.floor(x + width);
        int bottom = Mth.floor(y + height);

        graphics.hLine(left, right, top - 1, 0xFFDAFFFF);
        graphics.hLine(left - 1, right, bottom, 0xFFDAFFFF);
        graphics.vLine(left - 1, top - 2, bottom, 0xFFDAFFFF);
        graphics.vLine(right, top - 2, bottom, 0xFFDAFFFF);
        graphics.fillGradient(
            RenderType.guiOverlay(),
            left,
            top,
            right,
            bottom,
            0x669CD3FF,
            0x669CD3FF,
            0
        );
    }

    public static IGuiTexture PATTERN_OVERLAY = guiTexture("widget/pattern_overlay.png");
    public static IGuiTexture OUTPUTS = guiTexture("widget/outputs.png");

    private static IGuiTexture guiTexture(String path) {
        return SpriteTexture.of(NeoECOAE.id("textures/gui/" + path));
    }

    public static class Crafting {
        public static IGuiTexture F0 = SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/f0.png"));
        public static IGuiTexture F4 = SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/f4.png"));
        public static IGuiTexture F6 = SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/f6.png"));
        public static IGuiTexture F9 = SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/f9.png"));

    }

    public static void init(ResourceInstance<IGuiTexture> instance) {
        BuiltinResourceProvider<IGuiTexture> provider = new BuiltinResourceProvider<>("ui-eco", instance);
        addTextures(provider, NETextures.class, "");
        instance.addBuiltinProvider(provider);
    }


    private static void addTextures(BuiltinResourceProvider<IGuiTexture> provider, Class<?> cls, String pathPrefix) {
        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && IGuiTexture.class.isAssignableFrom(field.getType())) {
                try {
                    IGuiTexture texture = (IGuiTexture) field.get(null);
                    String name = pathPrefix + field.getName();
                    provider.addResource(name, texture);
                    BuiltinPath path = new BuiltinPath("ui-eco:" + name);
                    LOGGER.debug("Registering builtin texture: {}", path);
                    IGuiTexture builtinTexture = new UIResourceTexture(path);
                    field.set(null, builtinTexture);
                } catch (Exception e) {
                    LOGGER.warn("Failed to register builtin texture field {}.{}", cls.getName(), field.getName(), e);
                }
            }
        }
        for (Class<?> declaredClass : cls.getDeclaredClasses()) {
            if (Modifier.isStatic(declaredClass.getModifiers())) {
                addTextures(provider, declaredClass, pathPrefix + declaredClass.getSimpleName() + "-");
            }
        }
    }
}
