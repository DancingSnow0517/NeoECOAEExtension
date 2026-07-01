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
        provider.add(
                "neoecoae.configuration.postCraftingEvent.tooltip",
                "Whether to fire the vanilla crafting event (ItemCraftedEvent) when the crafting subsystem completes a recipe.\n"
                        + "Enabling this may introduce additional event/listener overhead; it can be noticeable when mods like Balm are installed.");
        provider.add("neoecoae.configuration.fastPath", "Fast Path");
        provider.add(
                "neoecoae.configuration.fastPath.tooltip",
                "ECO AE2 fast path cache and batch crafting options.\n"
                        + "Disable or lower these values if a modpack has recipe compatibility issues.");
        provider.add("neoecoae.configuration.ecoAe2FastPathEnabled", "Enable ECO AE2 Fast Path");
        provider.add(
                "neoecoae.configuration.ecoAe2FastPathEnabled.tooltip",
                "Enable ECO AE2 fast path batch crafting cache.\n"
                        + "This can greatly reduce repeated pattern execution cost. If recipe compatibility issues occur in a modpack, disable this option to fall back to the slow path.\n"
                        + "Fast Path is automatically disabled when Post Crafting Event is enabled to preserve event semantics.");
        provider.add("neoecoae.configuration.debugEcoFastPath", "Debug ECO Fast Path");
        provider.add(
                "neoecoae.configuration.debugEcoFastPath.tooltip",
                "Periodically log ECO fast path cache statistics.");
        provider.add("neoecoae.configuration.ecoCpuPushTickLimit", "CPU Push Tick Limit");
        provider.add(
                "neoecoae.configuration.ecoCpuPushTickLimit.tooltip",
                "Maximum normal crafting pattern pushes a CPU may attempt per tick.\n"
                        + "The effective value is still capped by available co-processors.");
        provider.add("neoecoae.configuration.ecoBatchFastPathLimit", "Batch Fast Path Limit");
        provider.add(
                "neoecoae.configuration.ecoBatchFastPathLimit.tooltip",
                "Maximum crafts merged into a single fast path batch push.");
        provider.add("neoecoae.configuration.ecoBatchFastPathTickLimit", "Batch Fast Path Tick Limit");
        provider.add(
                "neoecoae.configuration.ecoBatchFastPathTickLimit.tooltip",
                "Maximum fast path batch crafts a CPU may push per tick.");
        provider.add("neoecoae.configuration.ecoFastPathCacheSize", "Fast Path Cache Size");
        provider.add(
                "neoecoae.configuration.ecoFastPathCacheSize.tooltip",
                "Maximum recipe entries kept in each ECO fast path cache.");
        provider.add("neoecoae.configuration.craftingPatternBusPages", "Smart Pattern Bus Pages");
        provider.add(
                "neoecoae.configuration.craftingPatternBusPages.tooltip",
                "Number of 63-slot pages per smart pattern bus, range 1-8.\n"
                        + "Changes require re-entering the world or restarting the server to fully apply.");
        provider.add("neoecoae.configuration.increaseStorageCellCapacity", "Increase ECO Capacity");
        provider.add(
                "neoecoae.configuration.increaseStorageCellCapacity.tooltip",
                "Increase ECO storage matrix capacity and enlarge computation flash capacity by 16x.\n"
                        + "Defaults to enabled when GregTech Modern/GTCEu is detected.\n"
                        + "Changes are fully applied after re-entering the world or restarting the server.");
    }
}
