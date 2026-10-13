package cn.dancingsnow.neoecoae.integration;

import net.neoforged.fml.ModList;

/** Mod ids of optional integrations, used for load checks, conditions and {@code @Integration}. */
public final class ModIds {
    public static final String ADVANCED_AE = "advanced_ae";
    public static final String AE2LT = "ae2lt";
    public static final String AE2_OMNICELLS = "ae2omnicells";
    public static final String AE2_PATTERN_DISK = "ae2_pattern_disk";
    public static final String APPBOT = "appbot";
    public static final String APPEX = "appex";
    public static final String APPFLUX = "appflux";
    public static final String APPLIED_PNEUMATICS = "appliedpneumatics";
    public static final String APPLIED_SOUL = "appliedsoul";
    public static final String APPMEK = "appmek";
    public static final String ARSENG = "arseng";
    public static final String DATA_ENERGISTICS = "data_energistics";
    public static final String EXTENDEDAE = "extendedae";
    public static final String EXTENDEDAE_PLUS = "extendedae_plus";
    public static final String JEI = "jei";
    public static final String MEGACELLS = "megacells";
    public static final String MEKANISM = "mekanism";
    public static final String MOLECULAR_MANIPULATOR = "molecularmanipulator";
    public static final String SOULPLIED_ENERGISTICS = "soulplied_energistics";

    private ModIds() {}

    /** Null-safe: {@link ModList} is unavailable before the loading phase. */
    public static boolean isLoaded(String modId) {
        var mods = ModList.get();
        return mods != null && mods.isLoaded(modId);
    }
}
