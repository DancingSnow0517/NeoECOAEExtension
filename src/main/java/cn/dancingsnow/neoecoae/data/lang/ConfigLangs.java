package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class ConfigLangs {
    public static void accept(RegistrateLangProvider provider) {
        provider.add("neoecoae.configuration.structure", "Structure");
        provider.add("neoecoae.configuration.structure.tooltip", "Multiblock structure size limits.");
        provider.add("neoecoae.configuration.craftingSystemMaxLength", "Max Length of Crafting System");
        provider.add(
            "neoecoae.configuration.craftingSystemMaxLength.tooltip",
            "Maximum length (in blocks) allowed for the Crafting System multiblock.\n" +
                "Higher values allow longer expansions but may increase structure check cost."
        );
        provider.add("neoecoae.configuration.computationSystemMaxLength", "Max Length of Computation System");
        provider.add(
            "neoecoae.configuration.computationSystemMaxLength.tooltip",
            "Maximum length (in blocks) allowed for the Computation System multiblock.\n" +
                "Higher values allow longer expansions but may increase structure check cost."
        );
        provider.add("neoecoae.configuration.storageSystemMaxLength", "Max Length of Storage System");
        provider.add(
            "neoecoae.configuration.storageSystemMaxLength.tooltip",
            "Maximum length (in blocks) allowed for the Storage System multiblock.\n" +
                "Higher values allow longer expansions but may increase structure check cost."
        );
        provider.add("neoecoae.configuration.postCraftingEvent", "Post Crafting Event");
        provider.add(
            "neoecoae.configuration.postCraftingEvent.tooltip",
            "Post a vanilla crafting event (ItemCraftedEvent) when the Crafting System finishes a recipe.\n" +
                "May introduce extra event/listener overhead; can be more noticeable with mods like Balm installed."
        );

        provider.add("neoecoae.configuration.fastPath", "Fast Path");
        provider.add(
            "neoecoae.configuration.fastPath.tooltip",
            "ECO AE2 fast path cache and batch crafting options.\n" +
                "Disable or lower these values if a modpack has recipe compatibility issues."
        );
        provider.add("neoecoae.configuration.ecoAe2FastPathEnabled", "Enable ECO AE2 Fast Path");
        provider.add(
            "neoecoae.configuration.ecoAe2FastPathEnabled.tooltip",
            "Enable ECO AE2 fast path batch crafting cache.\n" +
                "This can greatly reduce repeated pattern execution cost. If recipe compatibility issues occur in a modpack, disable this option to fall back to the slow path.\n" +
                "Fast Path is automatically disabled when Post Crafting Event is enabled to preserve event semantics."
        );
        provider.add("neoecoae.configuration.debugEcoFastPath", "Debug ECO Fast Path");
        provider.add(
            "neoecoae.configuration.debugEcoFastPath.tooltip",
            "Periodically log ECO fast path cache statistics."
        );
        provider.add("neoecoae.configuration.ecoCpuPushTickLimit", "CPU Push Tick Limit");
        provider.add(
            "neoecoae.configuration.ecoCpuPushTickLimit.tooltip",
            "Maximum normal crafting pattern pushes a CPU may attempt per tick.\n" +
                "The effective value is still capped by available co-processors."
        );
        provider.add("neoecoae.configuration.ecoBatchFastPathLimit", "Batch Fast Path Limit");
        provider.add(
            "neoecoae.configuration.ecoBatchFastPathLimit.tooltip",
            "Maximum crafts merged into a single fast path batch push."
        );
        provider.add("neoecoae.configuration.ecoBatchFastPathTickLimit", "Batch Fast Path Tick Limit");
        provider.add(
            "neoecoae.configuration.ecoBatchFastPathTickLimit.tooltip",
            "Maximum fast path batch crafts a CPU may push per tick."
        );
        provider.add("neoecoae.configuration.ecoFastPathCacheSize", "Fast Path Cache Size");
        provider.add(
            "neoecoae.configuration.ecoFastPathCacheSize.tooltip",
            "Maximum recipe entries kept in each ECO fast path cache."
        );
    }
}
