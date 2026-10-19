package cn.dancingsnow.neoecoae.recipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/** Recipe provenance shared by the workstation screen and its formed block model. */
public enum LargeWorkstationModule implements StringRepresentable {
    REGULAR("regular", 0x878FA5, ""),
    ADVANCED_AE("advanced_ae", 0xB668D0, "advanced_ae"),
    EXTENDED_AE("extendedae", 0x45D9A0, "extendedae"),
    CRYSTAL_SCIENCE("ae2cs", 0x7DCBE8, "ae2cs"),
    LIGHTNING_TECH("aelt", 0xED7CCC, "aelt"),
    OTHER("other", 0x3D8FEA, "other");

    private final String name;
    private final int color;
    private final String model;

    LargeWorkstationModule(String name, int color, String model) {
        this.name = name;
        this.color = color;
        this.model = model;
    }

    @Override
    public String getSerializedName() { return name; }

    public int color() { return color; }

    public Component title() {
        return Component.translatable("gui.neoecoae.workstation.module." + name);
    }

    public String modelPath(boolean working) {
        return model.isEmpty() ? "block/large_integrated_working_station_" + (working ? "on" : "off")
            : "block/large_integrated_working_station_compat/" + model + (working ? "_on" : "_off");
    }

    public static LargeWorkstationModule fromSourceType(ResourceLocation type) {
        if (type == null || type.equals(LargeWorkstationRecipe.NATIVE_TYPE)) return REGULAR;
        return switch (type.getNamespace()) {
            case "advanced_ae", "advancedae" -> ADVANCED_AE;
            case "extendedae", "extendedae_plus" -> EXTENDED_AE;
            case "ae2cs" -> CRYSTAL_SCIENCE;
            case "ae2lt" -> LIGHTNING_TECH;
            default -> OTHER;
        };
    }

    public static LargeWorkstationModule byName(String name) {
        for (var module : values()) if (module.name.equals(name)) return module;
        return REGULAR;
    }
}
