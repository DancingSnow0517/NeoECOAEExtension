package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import net.minecraft.resources.ResourceLocation;

/** Pixel coordinates in the supplied workstation atlas; all sprites remain resource replaceable. */
public final class WorkstationTextures {
    public static final ResourceLocation WIDGETS = NeoECOAE.id("textures/guis/workstation/integrated_working_station_widget.png");
    public static final ResourceLocation LARGE = NeoECOAE.id("textures/guis/workstation/large_integrated_working_station.png");
    public static final ResourceLocation SMALL = NeoECOAE.id("textures/guis/workstation/integrated_working_station.png");
    static final int CIRCUIT_X = 217;
    static final int CIRCUIT_Y = 37;

    private WorkstationTextures() {}

    public static SpriteTexture widget(int x, int y, int width, int height) {
        return SpriteTexture.of(WIDGETS).setSprite(x, y, width, height);
    }

    public static SpriteTexture module(LargeWorkstationModule module) {
        return widget(moduleColumn(module) * 15, 17, 15, 15);
    }

    public static SpriteTexture circuit(LargeWorkstationModule module) {
        return widget(moduleColumn(module) * 28, 34, 28, 28);
    }

    /** Explicit columns preserve the supplied artwork and existing serialized module values. */
    private static int moduleColumn(LargeWorkstationModule module) {
        return switch (module) {
            case DATA_ENERGISTICS -> 5;
            case APPLIED_GENERATORS -> 6;
            case OTHER -> 7;
            default -> module.ordinal();
        };
    }

    public static SpriteTexture toggle(boolean on, boolean hover) {
        return widget((on ? 0 : 30) + (hover ? 15 : 0), 135, 15, 26);
    }

    public static SpriteTexture taskScrollThumb(boolean active) {
        return widget(active ? 69 : 75, 62, 6, 15);
    }

    /** The labels 4/6/9 name temperature bands, not multipliers or coolant recipe IDs. */
    public static int temperatureBand(double temperature) {
        if (!Double.isFinite(temperature) || temperature < 20) return 0;
        return temperature < 50 ? 4 : temperature < 80 ? 6 : 9;
    }

    public static SpriteTexture temperatureIcon(double temperature) {
        int y = switch (temperatureBand(temperature)) { case 4 -> 99; case 6 -> 111; case 9 -> 123; default -> 87; };
        return widget(0, y, 12, 12);
    }

    public static SpriteTexture coolingIcon(int coolantTier) {
        int y = switch (coolantTier) { case 6 -> 99; case 9 -> 111; default -> 87; };
        return widget(12, y, 12, 12);
    }
}
