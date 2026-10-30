package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;

import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/** Recipe provenance shared by the workstation screen and its formed block model. Atlas columns are mapped by WorkstationTextures. */
public enum LargeWorkstationModule implements StringRepresentable {
    REGULAR("regular", 0x878FA5, ""),
    ADVANCED_AE("advanced_ae", 0xB668D0, "advanced_ae"),
    EXTENDED_AE("extendedae", 0x45D9A0, "extendedae"),
    CRYSTAL_SCIENCE("ae2cs", 0x7DCBE8, "ae2cs"),
    LIGHTNING_TECH("aelt", 0xED7CCC, "aelt"),
    OTHER("other", 0x3D8FEA, "other"),
    DATA_ENERGISTICS("data_energistics", 0x49CDBF, "data_energistics"),
    APPLIED_GENERATORS("appgen", 0xF5A23C, "appgen");

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

    public Component shortTitle() {
        return Component.translatable("gui.neoecoae.workstation.module_short." + name);
    }

    public ResourceLocation upgradeCardId() {
        return ResourceLocation.fromNamespaceAndPath("neoecoae", "integrated_working_station_module_"
            + (this == REGULAR ? "empty" : name));
    }

    public boolean hasUpgradeCard() { return this != OTHER; }

    public static boolean isUpgradeCard(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (ECOWorkstationModules.customFromCard(stack) != null) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (var module : values()) if (module.hasUpgradeCard() && module.upgradeCardId().equals(id)) return true;
        return false;
    }

    public static LargeWorkstationModule fromUpgradeCard(ItemStack stack) {
        if (!stack.isEmpty()) {
            if (ECOWorkstationModules.customFromCard(stack) != null) return OTHER;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            for (var module : values()) if (module.hasUpgradeCard() && module.upgradeCardId().equals(id)) return module;
        }
        return REGULAR;
    }

    public boolean unlocks(ResourceLocation sourceType) {
        LargeWorkstationModule source = fromSourceType(sourceType);
        // Adapters without a registered card remain available alongside native recipes.
        return source == REGULAR || source == OTHER || source == this;
    }

    public String modelPath(boolean working) {
        return model.isEmpty() ? "block/large_integrated_working_station_" + (working ? "on" : "off")
            : "block/large_integrated_working_station_compat/" + model + (working ? "_on" : "_off");
    }

    public String modelPath(boolean working, boolean smartCooling) {
        return smartCooling ? modelPath(working).replace("large_integrated_working_station",
            "large_integrated_working_station_smart_cooling") : modelPath(working);
    }

    public static LargeWorkstationModule fromSourceType(ResourceLocation type) {
        if (type == null || type.equals(LargeWorkstationRecipe.NATIVE_TYPE)) return REGULAR;
        return switch (type.getNamespace()) {
            case "advanced_ae", "advancedae" -> ADVANCED_AE;
            case "extendedae", "extendedae_plus" -> EXTENDED_AE;
            case "ae2cs" -> CRYSTAL_SCIENCE;
            case "ae2lt" -> LIGHTNING_TECH;
            case "data_energistics" -> DATA_ENERGISTICS;
            case "appgen" -> APPLIED_GENERATORS;
            default -> OTHER;
        };
    }

    public static LargeWorkstationModule byName(String name) {
        for (var module : values()) if (module.name.equals(name)) return module;
        return REGULAR;
    }
}
