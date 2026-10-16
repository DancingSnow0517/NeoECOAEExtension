package cn.dancingsnow.neoecoae.core.content;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class ConfigLanguageContent {
    private ConfigLanguageContent() {}
    private static final List<LanguageEntry> ENTRIES = collect();

    public static List<LanguageEntry> entries() { return ENTRIES; }

    public static void accept(BiConsumer<String, String> output) {
        ENTRIES.forEach(entry -> output.accept(entry.key(), entry.text()));
    }

    private static List<LanguageEntry> collect() {
        var entries = new ArrayList<LanguageEntry>();
        declare((key, text) -> entries.add(new LanguageEntry(key, text)));
        return List.copyOf(entries);
    }

    private static void declare(BiConsumer<String, String> provider) {
        provider.accept("neoecoae.configuration.structure", "Structure");
        provider.accept("neoecoae.configuration.structure.tooltip", "Multiblock structure size limits.");
        provider.accept("neoecoae.configuration.craftingSystemMaxLength", "Max Length of Crafting System");
        provider.accept(
            "neoecoae.configuration.craftingSystemMaxLength.tooltip",
            "Maximum length (in blocks) allowed for the Crafting System multiblock.\n" +
                "Higher values allow longer expansions but may increase structure check cost."
        );
        provider.accept("neoecoae.configuration.computationSystemMaxLength", "Max Length of Computation System");
        provider.accept(
            "neoecoae.configuration.computationSystemMaxLength.tooltip",
            "Maximum length (in blocks) allowed for the Computation System multiblock.\n" +
                "Higher values allow longer expansions but may increase structure check cost."
        );
        provider.accept("neoecoae.configuration.storageSystemMaxLength", "Max Length of Storage System");
        provider.accept(
            "neoecoae.configuration.storageSystemMaxLength.tooltip",
            "Maximum length (in blocks) allowed for the Storage System multiblock.\n" +
                "Higher values allow longer expansions but may increase structure check cost."
        );
        provider.accept("neoecoae.configuration.postCraftingEvent", "Post Crafting Event");
        provider.accept(
            "neoecoae.configuration.postCraftingEvent.tooltip",
            "Post a vanilla crafting event (ItemCraftedEvent) when the Crafting System finishes a recipe.\n" +
                "May introduce extra event/listener overhead; can be more noticeable with mods like Balm installed."
        );
        provider.accept("neoecoae.configuration.largeWorkstationCompatRecipesEnabled", "Enable Large Workstation Recipe Compatibility");
        provider.accept(
            "neoecoae.configuration.largeWorkstationCompatRecipesEnabled.tooltip",
            "Allow the large integrated workstation to process supported mods' recipes. Disabled by default.\n" +
                "When disabled, only native integrated workstation recipes are available.\n" +
                "Reopen the world or restart the server after changing this option to refresh recipe viewers."
        );
        provider.accept("neoecoae.configuration.craftingPatternBusPages", "Crafting Pattern Bus Pages");
        provider.accept(
            "neoecoae.configuration.craftingPatternBusPages.tooltip",
            "Number of pattern pages exposed by one ECO smart pattern bus.\n" +
                "Each page stores 63 encoded patterns."
        );

        provider.accept("neoecoae.configuration.storage", "Storage");
        provider.accept(
            "neoecoae.configuration.storage.tooltip",
            "ECO storage system options."
        );
        provider.accept("neoecoae.configuration.megaBulkAutoMarkThreshold", "Bulk Storage Auto-Mark Threshold");
        provider.accept(
            "neoecoae.configuration.megaBulkAutoMarkThreshold.tooltip",
            "Amount threshold used when an ECO storage host automatically marks compressible items.\n" +
                "Only items with amounts strictly greater than this value are marked.");

        provider.accept("neoecoae.configuration.fastPath", "Fast Path");
        provider.accept(
            "neoecoae.configuration.fastPath.tooltip",
            "ECO AE2 fast path cache and batch crafting limits.\n" +
                "Fast Path is enabled by default and falls back to single-craft execution when Post Crafting Event is enabled."
        );
        provider.accept("neoecoae.configuration.ecoFastPathCacheSize", "Fast Path Cache Size");
        provider.accept(
            "neoecoae.configuration.ecoFastPathCacheSize.tooltip",
            "Maximum recipe entries kept in each ECO fast path cache."
        );
        provider.accept("neoecoae.configuration.ecoPlanningMaxMillis", "ECO Planning Time Budget (ms)");
        provider.accept(
            "neoecoae.configuration.ecoPlanningMaxMillis.tooltip",
            "Shared cooperative time budget in milliseconds for one ECO planning session, including routes, startup seeds and craft-less probes.\n" +
                "Exceeding the budget leaves the result unresolved.");
        provider.accept("neoecoae.configuration.ecoPlanningMaxWork", "ECO Planning Work Budget");
        provider.accept(
            "neoecoae.configuration.ecoPlanningMaxWork.tooltip",
            "Shared checkpoint work budget for one ECO planning session.\n" +
                "Changing routes or startup seeds does not reset it."
        );
        provider.accept("neoecoae.configuration.ecoPlanningMaxMemoryMiB", "ECO Planning Memory Budget (MiB)");
        provider.accept(
            "neoecoae.configuration.ecoPlanningMaxMemoryMiB.tooltip",
            "Estimated retained memory budget in MiB for cycle matrices and search states.\n" +
                "Exceeding the budget leaves the result unresolved.");

        provider.accept("neoecoae.configuration.debug", "Debug");
        provider.accept(
            "neoecoae.configuration.debug.tooltip",
            "Troubleshooting options. Keep these disabled during normal play."
        );
        provider.accept("neoecoae.configuration.calculating", "Calculation and Dispatch Diagnostics");
        provider.accept(
            "neoecoae.configuration.calculating.tooltip",
            "ECO crafting calculation and dispatch diagnostics."
        );
        provider.accept("neoecoae.configuration.ecoPlanningStageDebug", "Enable ECO Fast Planning Stage Logs");
        provider.accept(
            "neoecoae.configuration.ecoPlanningStageDebug.tooltip",
            "Log every ECO fast-planning calculation stage, including acyclic and cyclic planning.\n" +
                "Also logs whether each request selects ECO fast planning or the native AE2 planner, and why.\n" +
                "Includes stage duration, success state, failure reason, target item and requested amount."
        );
        provider.accept("neoecoae.configuration.ecoCraftSubmissionDebug", "Enable ECO Craft Submission Detail Logs");
        provider.accept(
            "neoecoae.configuration.ecoCraftSubmissionDebug.tooltip",
            "Diagnose why the crafting-confirm Start button is unavailable or why submission is delayed or fails.\n" +
                "Includes Start-button state, plan and CPU selection, submission route and elapsed time."
        );
        provider.accept("neoecoae.configuration.ecoDispatchWatchdogDebug", "Log ECO Dispatch Reasons");
        provider.accept(
            "neoecoae.configuration.ecoDispatchWatchdogDebug.tooltip",
            "Log ECO dispatch candidate reasons and task accounting before and after dispatch.\n" +
                "Also logs an aggregated diagnostic after 200 ticks without real progress, then every 1200 ticks.\n" +
                "This option only collects diagnostics and does not change crafting state."
        );
        provider.accept("neoecoae.configuration.ecoCraftingOutputDeliveryDebug", "ECO Output Delivery Debug");
        provider.accept(
            "neoecoae.configuration.ecoCraftingOutputDeliveryDebug.tooltip",
            "Log one aggregated warning per crafting job after its workers have been unable to deliver outputs for 200 ticks, then every 1200 ticks.\n" +
                "A recovery message is logged when the wait ends.\n" +
                "This option only controls logging and never changes output ownership, retry or recovery behavior."
        );
    }
}
