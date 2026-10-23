package cn.dancingsnow.neoecoae.api.extension;

import java.util.Set;

/** Version of the documented addon contracts, independent of the Minecraft/mod version. */
public final class ECOAddonApi {
    public static final int VERSION = 1;
    public static final Set<String> FEATURES = Set.of("power_profiles", "multiblock_definitions", "multiblock_parts",
        "multiblock_requirements", "computation_cells", "host_cable_models", "tier_features",
        "storage_channel_displays", "workstation_adapters", "workstation_modules");
    private ECOAddonApi() {}
    public static boolean supports(String feature) { return FEATURES.contains(feature); }
}
