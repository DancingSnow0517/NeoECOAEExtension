package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.texture.UIResourceTexture;
import com.lowdragmc.lowdraglib2.math.Size;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;


@SuppressWarnings("unused")
public class NETextures {
    public static final IGuiTexture BACKGROUND = SpriteTexture.of(NeoECOAE.id("textures/gui/background.png"))
        .setSpriteSize(Size.of(16, 16))
        .setBorder(2, 2, 2, 4);
    public static final IGuiTexture INVENTORY_BORDER =
        SpriteTexture.of(NeoECOAE.id("textures/gui/inventory_border.png"))
            .setSpriteSize(Size.of(16, 16))
            .setBorder(1, 1, 1, 1);

    public static final IGuiTexture BUTTON = SpriteTexture.of(NeoECOAE.id("textures/gui/button.png"))
        .setSpriteSize(Size.of(20, 20))
        .setBorder(2,2,2,4);
    public static final IGuiTexture BUTTON_DISABLED = SpriteTexture.of(NeoECOAE.id("textures/gui/button_disabled.png"))
        .setSpriteSize(Size.of(20, 20))
        .setBorder(2,4,2,4);
    public static final IGuiTexture BUTTON_HIGHLIGHTED = SpriteTexture.of(NeoECOAE.id("textures/gui/button_highlighted.png"))
        .setSpriteSize(Size.of(20, 20))
        .setBorder(2,3,2,4);

    public static final IGuiTexture ITEM_SLOT = SpriteTexture.of(NeoECOAE.id("textures/gui/slot.png"))
        .setSpriteSize(Size.of(18, 18))
        .setBorder(1, 2, 1, 1);

    public static final IGuiTexture BAR_CONTAINER = SpriteTexture.of(NeoECOAE.id("textures/gui/bar_container.png"))
        .setSpriteSize(Size.of(6, 18));

    public static final IGuiTexture BAR = SpriteTexture.of(NeoECOAE.id("textures/gui/bar.png"))
        .setSpriteSize(Size.of(6, 18));

    public static final IGuiTexture PATTERN_OVERLAY = widgetTexture("pattern_overlay.png");

    public static final IGuiTexture COOLING_OFF = widgetTexture("crafting/cooling_off.png");
    public static final IGuiTexture COOLING_OFF_DOWN = widgetTexture("crafting/cooling_off_down.png");
    public static final IGuiTexture COOLING_ON = widgetTexture("crafting/cooling_on.png");
    public static final IGuiTexture COOLING_ON_DOWN = widgetTexture("crafting/cooling_on_down.png");

    public static final IGuiTexture OVERCLOCK_OFF = widgetTexture("crafting/overclock_off.png");
    public static final IGuiTexture OVERCLOCK_OFF_DOWN = widgetTexture("crafting/overclock_off_down.png");
    public static final IGuiTexture OVERCLOCK_ON = widgetTexture("crafting/overclock_on.png");
    public static final IGuiTexture OVERCLOCK_ON_DOWN = widgetTexture("crafting/overclock_on_down.png");

    public static final IGuiTexture PROGRESS_BAR_COOLANT =
            SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/coolant_progress.png"));
    public static final IGuiTexture PROGRESS_BAR_HOT_COOLANT =
            SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/hot_coolant_progress.png"));
    public static final IGuiTexture PROGRESS_BAR_CRAFTING =
            SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/crafting_progress.png"));
    public static final IGuiTexture PROGRESS_BAR_LIMIT =
            SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/limit_progress.png"));

    private static IGuiTexture widgetTexture(String path) {
        return SpriteTexture.of(NeoECOAE.id("textures/gui/widget/" + path));
    }

    public static class Crafting {
        public static final IGuiTexture BACKGROUND_DARK =
            SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/background_dark.png"))
                .setSpriteSize(Size.of(32, 32))
                .setBorder(6, 12, 6, 6);
        public static final IGuiTexture BACKGROUND_LIGHT =
            SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/background_light.png"))
                .setSpriteSize(Size.of(32, 32))
                .setBorder(6, 12, 6, 6);
        public static final IGuiTexture STATUS_BACKGROUND =
                SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/status_background.png"));
        public static final IGuiTexture UNAVAILABLE_STATUS =
                SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/unavailable_status.png"));


        public static final IGuiTexture F0 = SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/f0.png"));
        public static final IGuiTexture F4 = SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/f4.png"));
        public static final IGuiTexture F6 = SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/f6.png"));
        public static final IGuiTexture F9 = SpriteTexture.of(NeoECOAE.id("textures/gui/crafting/f9.png"));

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
                    System.out.println("Registering builtin texture: " + path);
                    IGuiTexture builtinTexture = new UIResourceTexture(path);
                    field.set(null, builtinTexture);
                } catch (Exception ignore) {}
            }
        }
        for (Class<?> declaredClass : cls.getDeclaredClasses()) {
            if (Modifier.isStatic(declaredClass.getModifiers())) {
                addTextures(provider, declaredClass, pathPrefix + declaredClass.getSimpleName() + "-");
            }
        }
    }
}
