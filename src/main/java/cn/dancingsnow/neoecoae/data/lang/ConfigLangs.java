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
        provider.add("neoecoae.configuration.ecoCpuPushTickLimit", "CPU Push Tick Limit");
        provider.add(
            "neoecoae.configuration.ecoCpuPushTickLimit.tooltip",
            "Maximum normal crafting pattern pushes a CPU may attempt per tick.\n" +
                "The temporary hard ceiling is 393216 until adaptive dispatch is integrated.\n" +
                "The effective value is still capped by available co-processors."
        );
        provider.add("neoecoae.configuration.ecoFastPathCacheSize", "Fast Path Cache Size");
        provider.add(
            "neoecoae.configuration.ecoFastPathCacheSize.tooltip",
            "Maximum recipe entries kept in each ECO fast path cache."
        );
        provider.add("neoecoae.configuration.ecoGenericResolutionsPerTick", "Generic Resolutions Per Tick");
        provider.add("neoecoae.configuration.ecoGenericResolutionsPerTick.tooltip",
            "Maximum AE2 generic input resolutions per ECO CPU per tick. Direct and cached dispatch are not charged.");
        provider.add("neoecoae.configuration.ecoDispatchSafetyLimitPerTick", "Dispatch Safety Fuse");
        provider.add("neoecoae.configuration.ecoDispatchSafetyLimitPerTick.tooltip",
            "Emergency dispatch fuse only. Normal FastPath throughput is limited naturally by available workers.");
        provider.add("neoecoae.configuration.ecoGenericCpuNanosPerTick", "Generic CPU Time Budget");
        provider.add("neoecoae.configuration.ecoGenericCpuNanosPerTick.tooltip",
            "Sustained AE2 generic-resolution budget per ECO CPU; unused credit may burst up to three ticks.");
        provider.add("neoecoae.configuration.ecoGenericServerNanosPerTick", "Generic Server Time Budget");
        provider.add("neoecoae.configuration.ecoGenericServerNanosPerTick.tooltip",
            "Shared sustained generic-resolution budget; direct dispatch never consumes it.");

        provider.add("neoecoae.configuration.debug", "Debug");
        provider.add(
            "neoecoae.configuration.debug.tooltip",
            "Troubleshooting options. Keep these disabled during normal play."
        );
        provider.add("neoecoae.configuration.ecoDispatchWatchdogDebug", "ECO Dispatch Watchdog Debug");
        provider.add(
            "neoecoae.configuration.ecoDispatchWatchdogDebug.tooltip",
            "Log detailed crafting dispatch diagnostics after 200 ticks without real progress, then every 1200 ticks.\n" +
                "Includes phase, task, input, power and pattern-provider state.\n" +
                "This option never resynchronizes the scheduler, replays inputs or changes crafting state."
        );
        provider.add("neoecoae.configuration.ecoCraftingOutputDeliveryDebug", "ECO Output Delivery Debug");
        provider.add(
            "neoecoae.configuration.ecoCraftingOutputDeliveryDebug.tooltip",
            "Log one aggregated warning per crafting job after its workers have been unable to deliver outputs for 200 ticks, then every 1200 ticks.\n" +
                "A recovery message is logged when the wait ends.\n" +
                "This option only controls logging and never changes output ownership, retry or recovery behavior."
        );
        provider.add("neoecoae.configuration.ecoCraftConfirmDebug", "Craft Confirm Start Debug");
        provider.add(
            "neoecoae.configuration.ecoCraftConfirmDebug.tooltip",
            "Log one diagnostic report when a completed crafting plan cannot be started.\n" +
                "Includes the plan state, selected CPU, all CPUs advertised by AE2, and ECO computation-cluster selection reasons.\n" +
                "Submission failures are logged separately."
        );
    }
}
