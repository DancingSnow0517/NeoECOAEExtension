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
        provider.add("neoecoae.configuration.storage", "Storage");
        provider.add("neoecoae.configuration.storage.tooltip", "ECO storage system options.");
        provider.add("neoecoae.configuration.megaBulkAutoMarkThreshold", "Bulk Storage Auto-Mark Threshold");
        provider.add(
            "neoecoae.configuration.megaBulkAutoMarkThreshold.tooltip",
            "Amount threshold used when an ECO storage host automatically marks compressible items.\n" +
                "Only items with amounts strictly greater than this value are marked."
        );

        provider.add("neoecoae.configuration.postCraftingEvent", "Post Crafting Event");
        provider.add(
            "neoecoae.configuration.postCraftingEvent.tooltip",
            "Post a vanilla crafting event (ItemCraftedEvent) when the Crafting System finishes a recipe.\n" +
                "May introduce extra event/listener overhead; can be more noticeable with mods like Balm installed."
        );
        provider.add("neoecoae.configuration.largeWorkstationTemperatureEnabled", "Enable Large Workstation Temperature");
        provider.add("neoecoae.configuration.largeWorkstationTemperatureEnabled.tooltip",
            "Enabled by default. Disable temperature, preheating and overheat recovery to use coolant-based parallelism.\n" +
                "Enable overclock and active cooling: water / sodium / cryotheum provide 16,384 / 65,536 / 262,144 parallel; normal is 256.\n" +
                "Consumes 100 mB per advancing overclocked processing tick; all tiers use x1 recipe energy.\n" +
                "Reopen the world or restart the server after changing this option.");
        provider.add("neoecoae.configuration.largeWorkstationCompatRecipesEnabled", "Enable Large Workstation Recipe Compatibility");
        provider.add(
            "neoecoae.configuration.largeWorkstationCompatRecipesEnabled.tooltip",
            "Allow the large integrated workstation to process supported mods' recipes. Disabled by default.\n" +
                "When disabled, only native integrated workstation recipes are available.\n" +
                "Reopen the world or restart the server after changing this option to refresh recipe viewers."
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
            "ECO AE2 fast path cache and batch crafting limits.\n" +
                "Fast Path is enabled by default and falls back to single-craft execution when Post Crafting Event is enabled."
        );

        provider.add("neoecoae.configuration.ecoCpuPushTickLimit", "CPU Push Tick Limit");
        provider.add(
            "neoecoae.configuration.ecoCpuPushTickLimit.tooltip",
            "Maximum normal crafting pattern pushes a CPU may attempt per tick.\n" +
                "Ordinary fallback is capped at 64 per CPU per tick; a lower limit can be configured.\n" +
                "This is independent of co-processors and previous ticks.\n" +
                "The configuration ceiling is 393216. Fast Path batches do not consume this limit."
        );
        provider.add("neoecoae.configuration.ecoFastPathCacheSize", "Fast Path Cache Size");
        provider.add(
            "neoecoae.configuration.ecoFastPathCacheSize.tooltip",
            "Maximum recipe entries kept in each ECO fast path cache."
        );

        provider.add("neoecoae.configuration.ecoPlanningMaxMillis", "ECO Planning Time Budget (ms)");
        provider.add(
            "neoecoae.configuration.ecoPlanningMaxMillis.tooltip",
            "Shared cooperative time budget in milliseconds for one ECO planning session, including routes, startup seeds and craft-less probes.\n" +
                "Exceeding the budget leaves the result unresolved."
        );
        provider.add("neoecoae.configuration.ecoPlanningMaxWork", "ECO Planning Work Budget");
        provider.add(
            "neoecoae.configuration.ecoPlanningMaxWork.tooltip",
            "Shared checkpoint work budget for one ECO planning session.\n" +
                "Changing routes or startup seeds does not reset it."
        );
        provider.add("neoecoae.configuration.ecoPlanningMaxMemoryMiB", "ECO Planning Memory Budget (MiB)");
        provider.add(
            "neoecoae.configuration.ecoPlanningMaxMemoryMiB.tooltip",
            "Estimated retained memory budget in MiB for cycle matrices and search states.\n" +
                "Exceeding the budget leaves the result unresolved."
        );

        provider.add("neoecoae.configuration.debug", "Debug");
        provider.add(
            "neoecoae.configuration.debug.tooltip",
            "Troubleshooting options. Keep these disabled during normal play."
        );
        provider.add("neoecoae.configuration.calculating", "Calculation and Dispatch Diagnostics");
        provider.add(
            "neoecoae.configuration.calculating.tooltip",
            "ECO crafting calculation and dispatch diagnostics."
        );
        provider.add("neoecoae.configuration.ecoPlanningStageDebug", "Enable ECO Fast Planning Stage Logs");
        provider.add(
            "neoecoae.configuration.ecoPlanningStageDebug.tooltip",
            "Log every ECO fast-planning calculation stage, including acyclic and cyclic planning.\n" +
                "Also logs whether each request selects ECO fast planning or the native AE2 planner, and why.\n" +
                "Includes stage duration, success state, failure reason, target item and requested amount."
        );
        provider.add("neoecoae.configuration.ecoCraftSubmissionDebug", "Enable ECO Craft Submission Detail Logs");
        provider.add(
            "neoecoae.configuration.ecoCraftSubmissionDebug.tooltip",
            "Diagnose why the crafting-confirm Start button is unavailable or why submission is delayed or fails.\n" +
                "Includes Start-button state, plan and CPU selection, submission route and elapsed time."
        );
        provider.add("neoecoae.configuration.ecoDispatchWatchdogDebug", "Log ECO Dispatch Reasons");
        provider.add(
            "neoecoae.configuration.ecoDispatchWatchdogDebug.tooltip",
            "Log ECO dispatch candidate reasons and task accounting before and after dispatch.\n" +
                "Also logs an aggregated diagnostic after 200 ticks without real progress, then every 1200 ticks.\n" +
                "This option only collects diagnostics and does not change crafting state."
        );
        provider.add("neoecoae.configuration.ecoCraftingOutputDeliveryDebug", "ECO Output Delivery Debug");
        provider.add(
            "neoecoae.configuration.ecoCraftingOutputDeliveryDebug.tooltip",
            "Log one aggregated warning per crafting job after its workers have been unable to deliver outputs for 200 ticks, then every 1200 ticks.\n" +
                "A recovery message is logged when the wait ends.\n" +
                "This option only controls logging and never changes output ownership, retry or recovery behavior."
        );
    }
}
