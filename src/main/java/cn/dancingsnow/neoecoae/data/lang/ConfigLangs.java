package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class ConfigLangs {
    public static void accept(RegistrateLangProvider provider) {
        provider.add("neoecoae.configuration.structure", "Structure");
        provider.add("neoecoae.configuration.structure.tooltip", "Multiblock structure size limits.");
        provider.add("neoecoae.configuration.craftingSystemMaxLength", "Crafting System Max Length");
        provider.add(
                "neoecoae.configuration.craftingSystemMaxLength.tooltip",
                "Maximum allowed length (in blocks) for crafting subsystem multiblock structures.\n"
                        + "Higher values allow longer scalable modules/components but may increase structure detection overhead.");
        provider.add("neoecoae.configuration.computationSystemMaxLength", "Computation System Max Length");
        provider.add(
                "neoecoae.configuration.computationSystemMaxLength.tooltip",
                "Maximum allowed length (in blocks) for computation subsystem multiblock structures.\n"
                        + "Higher values allow longer scalable modules/components but may increase structure detection overhead.");
        provider.add("neoecoae.configuration.storageSystemMaxLength", "Storage System Max Length");
        provider.add(
                "neoecoae.configuration.storageSystemMaxLength.tooltip",
                "Maximum allowed length (in blocks) for storage subsystem multiblock structures.\n"
                        + "Higher values allow longer scalable modules/components but may increase structure detection overhead.");
        provider.add("neoecoae.configuration.postCraftingEvent", "Trigger Crafting Event");
        provider.add("neoecoae.configuration.ecoMissingCraftingEnabled", "Enable ECO Missing-Ingredient Crafting");
        provider.add(
                "neoecoae.configuration.ecoMissingCraftingEnabled.tooltip",
                "Allow ECO CPUs to start jobs with missing ingredients. Disabled by default.\n"
                        + "Jobs wait for materials to enter the ME network; no materials are generated.\n"
                        + "Does not affect GTL's own setting or jobs that have already started.");
        provider.add(
                "neoecoae.configuration.postCraftingEvent.tooltip",
                "Whether to fire the vanilla crafting event (ItemCraftedEvent) when the crafting subsystem completes a recipe.\n"
                        + "Enabling this may introduce additional event/listener overhead; it can be noticeable when mods like Balm are installed.");
        // Legacy configuration labels retained for resource compatibility.
        provider.add("neoecoae.configuration.ecoAggressiveFastPathEnabled", "Enable Aggressive Fast Path");
        provider.add(
                "neoecoae.configuration.ecoAggressiveFastPathEnabled.tooltip",
                "Allow ECO pattern buses to push larger batches while retaining the regular Fast Path safety checks.\n"
                        + "Enabled by default. Disable this if a modpack has recipe compatibility issues.");
        provider.add("neoecoae.configuration.ecoAggressiveFastPathTickLimit", "Aggressive Fast Path Tick Limit");
        provider.add(
                "neoecoae.configuration.ecoAggressiveFastPathTickLimit.tooltip",
                "Maximum new aggressive Fast Path crafts a CPU may schedule per tick. This also adjusts the simulated crafting power limit, allowing batches within this limit to advance at the host's full progress rate when sufficient AE energy is available. The host's dynamic FX capacity still limits the total number of concurrent crafts.");
        provider.add("neoecoae.configuration.ecoBatchFastPathTickLimit", "Batch Fast Path Tick Limit");
        provider.add(
                "neoecoae.configuration.ecoBatchFastPathTickLimit.tooltip",
                "Maximum number of crafts a CPU may push through Fast Path batches per tick.");
        provider.add("neoecoae.configuration.ecoCraftingOutputDeliveryDebug", "ECO Output Delivery Diagnostics");
        provider.add(
                "neoecoae.configuration.ecoCraftingOutputDeliveryDebug.tooltip",
                "Log a summary warning when workers on the same crafting job cannot deliver outputs for 200 consecutive ticks, then repeat every 1200 ticks.\n"
                        + "Log a recovery message when the wait ends.\n"
                        + "This only controls logging; it does not change output ownership, retries or recovery behavior.");
        provider.add("neoecoae.configuration.ecoDispatchWatchdogDebug", "ECO Dispatch Watchdog Debugging");
        provider.add(
                "neoecoae.configuration.ecoDispatchWatchdogDebug.tooltip",
                "Log detailed dispatch diagnostics after 200 ticks without actual crafting progress, then repeat every 1200 ticks.\n"
                        + "Logs include the phase, job, materials, power and pattern provider status.\n"
                        + "This does not resynchronize the scheduler, replay inputs or modify crafting state.");
        provider.add("neoecoae.configuration.enableInfiniteStorage", "Enable Infinite Storage");
        provider.add(
                "neoecoae.configuration.enableInfiniteStorage.tooltip",
                "Enable infinite storage for ECO storage controllers.\n"
                        + "Requires 64 infinite components and 16 L9 storage matrices; disabling this prevents new migrations without deleting existing storage domain files.");

        provider.add("neoecoae.configuration.debug", "Debug");
        provider.add("neoecoae.configuration.debug.tooltip", "ECO crafting planner diagnostic options.");
        provider.add("neoecoae.configuration.debugECOPlanner", "Debug ECO Planner");
        provider.add(
                "neoecoae.configuration.debugECOPlanner.tooltip",
                "Log the stage, reason, request, solver state and fallback details for ECO planning failures.\n"
                        + "Messages are deduplicated and rate-limited. Enable only while diagnosing planning issues.");
        provider.add("neoecoae.configuration.fastPath", "Fast Path");
        provider.add(
                "neoecoae.configuration.fastPath.tooltip",
                "ECO AE2 fast path cache and batch crafting options.\n"
                        + "Disable Fast Path if a modpack has recipe compatibility issues.");
        provider.add("neoecoae.configuration.ecoAe2FastPathEnabled", "Enable ECO AE2 Fast Path");
        provider.add(
                "neoecoae.configuration.ecoAe2FastPathEnabled.tooltip",
                "Enable the ECO AE2 fast-path batch crafting cache.\n"
                        + "Disable it to fall back to the slow path if a modpack has recipe compatibility issues.\n"
                        + "Fast Path is automatically disabled when crafting events are enabled.");
        provider.add("neoecoae.configuration.debugEcoFastPath", "Debug ECO Fast Path");
        provider.add(
                "neoecoae.configuration.debugEcoFastPath.tooltip", "Periodically log ECO fast path cache statistics.");
        provider.add("neoecoae.configuration.ecoCpuPushTickLimit", "CPU Push Tick Limit");
        provider.add(
                "neoecoae.configuration.ecoCpuPushTickLimit.tooltip",
                "Maximum crafting operations a CPU may schedule per tick.\n"
                        + "A safety cap for both batch fast paths and regular paths; the effective value is still capped by available co-processors.");
        provider.add("neoecoae.configuration.ecoFastPathCacheSize", "Fast Path Cache Size");
        provider.add(
                "neoecoae.configuration.ecoFastPathCacheSize.tooltip",
                "Maximum recipe entries kept in each ECO fast path cache.");
        provider.add("neoecoae.configuration.craftingPatternBusPages", "Smart Pattern Bus Pages");
        provider.add(
                "neoecoae.configuration.craftingPatternBusPages.tooltip",
                "Number of 63-slot pages per smart pattern bus, range 1-8.\n"
                        + "Changes require re-entering the world or restarting the server to fully apply.");
        provider.add("neoecoae.configuration.megaBulkAutoMarkThreshold", "Bulk Storage Auto-Mark Threshold");
        provider.add(
                "neoecoae.configuration.megaBulkAutoMarkThreshold.tooltip",
                "Minimum stored amount for automatically marking compressible items in ECO bulk storage.\n"
                        + "Only items with an amount strictly greater than this threshold are marked.");

        provider.add("neoecoae.configuration.increaseStorageCellCapacity", "Increase ECO Capacity");
        provider.add(
                "neoecoae.configuration.increaseStorageCellCapacity.tooltip",
                "Increase ECO storage matrix capacity and enlarge computation flash capacity by 16x.\n"
                        + "Defaults to enabled when GregTech Modern/GTCEu is detected.\n"
                        + "Changes are fully applied after re-entering the world or restarting the server.");
    }
}
