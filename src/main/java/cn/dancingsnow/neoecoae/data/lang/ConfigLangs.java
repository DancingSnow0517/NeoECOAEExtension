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
        provider.add("neoecoae.configuration.craftingPatternBusPages", "Crafting Pattern Bus Pages");
        provider.add(
            "neoecoae.configuration.craftingPatternBusPages.tooltip",
            "Number of pattern pages exposed by one ECO smart pattern bus.\n" +
                "Each page stores 63 encoded patterns."
        );

        provider.add("neoecoae.configuration.debug", "Debug");
        provider.add("neoecoae.configuration.debug.tooltip", "ECO crafting planner diagnostic options.");

        provider.add("neoecoae.configuration.fastPath", "Fast Path");
        provider.add(
            "neoecoae.configuration.fastPath.tooltip",
            "ECO AE2 fast path cache and batch crafting options. The cache is shared by the physical crafting controller/network cluster.\n" +
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
            "Log each pattern that does not use ECO FastPath, including the reason, and periodically log cache statistics.\n" +
                "Messages are deduplicated and rate-limited. Enable only while diagnosing issues."
        );
        provider.add("neoecoae.configuration.debugECOPlanner", "Debug ECO Planner");
        provider.add(
            "neoecoae.configuration.debugECOPlanner.tooltip",
            "Log the exact ECO planning failure stage, reason, request, solver result and fallback context.\n" +
                "Messages are deduplicated and rate-limited. Enable only while diagnosing planning issues."
        );
        provider.add("neoecoae.configuration.debugInfiniteStorageMigration", "Debug Infinite Storage Migration");
        provider.add(
            "neoecoae.configuration.debugInfiniteStorageMigration.tooltip",
            "Log detailed reasons when an ECO storage matrix cannot migrate into infinite storage, including the source, transaction, and rejected AE key.\n" +
                "Messages are rate-limited. Enable only while diagnosing storage migration issues."
        );
        provider.add("neoecoae.configuration.ecoCpuPushTickLimit", "CPU Push Tick Limit");
        provider.add(
            "neoecoae.configuration.ecoCpuPushTickLimit.tooltip",
            "Maximum crafting operations a CPU may schedule per tick.\n" +
                "A safety cap for both batch fast paths and regular paths; the effective value is still capped by available co-processors."
        );
        provider.add("neoecoae.configuration.ecoCpuSlowPathPushTickLimit", "CPU Slow-Path Push Tick Limit");
        provider.add(
            "neoecoae.configuration.ecoCpuSlowPathPushTickLimit.tooltip",
            "Hard limit for non-batch provider calls across one AE2 network per tick.\n" +
                "Every attempt counts, including rejection and exceptions; batch fast paths do not."
        );
        provider.add("neoecoae.configuration.ecoCpuSlowPathTimeBudgetMicros", "CPU Slow-Path Time Budget");
        provider.add(
            "neoecoae.configuration.ecoCpuSlowPathTimeBudgetMicros.tooltip",
            "Shared time budget in microseconds per AE2 network and tick for non-batch provider dispatch.\n" +
                "Set to 0 to use only the hard attempt limit."
        );
        provider.add("neoecoae.configuration.ecoFastPathCacheSize", "Fast Path Cache Size");
        provider.add(
            "neoecoae.configuration.ecoFastPathCacheSize.tooltip",
            "Maximum recipe entries kept in the FastPath cache shared by one physical crafting controller/network cluster."
        );
    }
}
