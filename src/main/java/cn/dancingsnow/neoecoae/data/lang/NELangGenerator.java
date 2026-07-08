package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class NELangGenerator {
    public static void accept(RegistrateLangProvider provider) {
        GuiLangs.accept(provider);
        ConfigLangs.accept(provider);

        // jade
        provider.add("config.jade.plugin_neoecoae.eco_drive", "ECO Drive");
        provider.add("config.jade.plugin_neoecoae.eco_crafting_worker", "ECO Crafting Worker");
        provider.add("config.jade.plugin_neoecoae.eco_crafting_system", "ECO Crafting System");

        provider.add("jade.neoecoae.drive_mounted", "ECO Drive Mounted");
        provider.add("jade.neoecoae.drive_unmounted", "ECO Drive Unmounted");
        provider.add("jade.neoecoae.worker_threads", "Threads: %d/%d");
        provider.add("jade.neoecoae.overclocked", "Overclock Enabled");
        provider.add("jade.neoecoae.activeCooling", "Active Cooling Enabled");
        provider.add("jade.neoecoae.coolant", "Coolant: %d");
        provider.add("jade.neoecoae.coolant_max_overclock", "Coolant Max Overclock: %d");
        provider.add("jade.neoecoae.coolant_max_overclock.none", "Coolant Max Overclock: None");
        provider.add("jade.neoecoae.overclock_status", "Theoretical/Effective Overclock: %d/%d");

        provider.add("neoecoae.tooltip.upload_pattern", "Upload Pattern into available ECO Crafting System");

        provider.add("category.neoecoae.cooling", "Cooling");
        provider.add("category.neoecoae.cooling.coolant", "Coolant: %d");
        provider.add("category.neoecoae.cooling.max_overclock", "Max Overclock: %d");
        provider.add("category.neoecoae.multiblock", "ECO Multiblock Info");
        provider.add("category.neoecoae.integrated_working_station", "Integrated Working Station");

        provider.add("emi.category.neoecoae.multiblock", "ECO Multiblock Info");
        provider.add("emi.category.neoecoae.integrated_working_station", "Integrated Working Station");
        provider.add("emi.category.neoecoae.cooling", "Cooling");
        provider.add("tag.item.ae2.inscriber_presses", "Inscriber Presses");
        provider.add("tag.item.ae2.metal_ingots", "Metal Ingots");
        provider.add("tag.item.c.budding_blocks", "Budding Blocks");
        provider.add("tag.item.c.clusters", "Crystal Clusters");
        provider.add("tag.item.c.dusts.aluminum", "Aluminum Dusts");
        provider.add("tag.item.c.dusts.aluminum_alloy", "Aluminum Alloy Dusts");
        provider.add("tag.item.c.dusts.black_tungsten_alloy", "Black Tungsten Alloy Dusts");
        provider.add("tag.item.c.dusts.energized_crystal", "Energized Crystal Dusts");
        provider.add("tag.item.c.dusts.energized_fluix_crystal", "Energized Fluix Crystal Dusts");
        provider.add("tag.item.c.dusts.tungsten", "Tungsten Dusts");
        provider.add("tag.item.c.gems.energized_crystal", "Energized Crystals");
        provider.add("tag.item.c.gems.energized_fluix_crystal", "Energized Fluix Crystals");
        provider.add("tag.item.c.ingots.aluminum", "Aluminum Ingots");
        provider.add("tag.item.c.ingots.aluminum_alloy", "Aluminum Alloy Ingots");
        provider.add("tag.item.c.ingots.black_tungsten_alloy", "Black Tungsten Alloy Ingots");
        provider.add("tag.item.c.ingots.tungsten", "Tungsten Ingots");
        provider.add("tag.item.c.ores.aluminum", "Aluminum Ores");
        provider.add("tag.item.c.ores.tungsten", "Tungsten Ores");
        provider.add("tag.item.c.raw_materials.aluminum", "Raw Aluminum");
        provider.add("tag.item.c.raw_materials.tungsten", "Raw Tungsten");
        provider.add("tag.item.c.storage_blocks.aluminum", "Aluminum Storage Blocks");
        provider.add("tag.item.c.storage_blocks.aluminum_alloy", "Aluminum Alloy Storage Blocks");
        provider.add("tag.item.c.storage_blocks.black_tungsten_alloy", "Black Tungsten Alloy Storage Blocks");
        provider.add("tag.item.c.storage_blocks.energized_crystal", "Energized Crystal Storage Blocks");
        provider.add("tag.item.c.storage_blocks.energized_fluix_crystal", "Energized Fluix Crystal Storage Blocks");
        provider.add("tag.item.c.storage_blocks.raw_aluminum", "Raw Aluminum Storage Blocks");
        provider.add("tag.item.c.storage_blocks.raw_tungsten", "Raw Tungsten Storage Blocks");
        provider.add("tag.item.c.storage_blocks.tungsten", "Tungsten Storage Blocks");
        provider.add("tag.item.c.tools.mining_tool", "Mining Tools");
        provider.add("tag.item.neoecoae.crystal_ingot_base", "Crystal Ingot Base");
        provider.add("tag.item.neoecoae.superconductive_ingot_base", "Superconductive Ingot Base");

        provider.add("tooltip.neoecoae.holdshift", "Hold [Shift] to show more info");
        provider.add("tooltip.neoecoae.max_lenth", "Maximum length of structure: %d");

        provider.add("tooltip.neoecoae.storage_system", "The core of the storage subsystem");
        addLangs(provider, "tooltip.neoecoae.storage_dirve",
            "Can drive storage matrix",
            "The drivable storage matrix tier depends on the storage subsystem host controller"
        );

        provider.add("tooltip.neoecoae.crafting_system", "The core of the crafting subsystem");
        provider.add("tooltip.neoecoae.crafting_parallels", "Parallel core provides parallel count to the crafting subsystem");
        provider.add("tooltip.neoecoae.max_parallel_count", "Max parallel count +%d");
        provider.add("tooltip.neoecoae.overclocked", "When enabling overclocking:");
        provider.add("tooltip.neoecoae.active_cooling", "When enabling active cooling:");
        provider.add("tooltip.neoecoae.clear_negative_effect", "Clear the negative effects of overclocking");

        addLangs(provider, "tooltip.neoecoae.crafting_worker",
            "ECO - FX Worker is the main part of the crafting subsystem",
            "ECO - FX Worker can store 32 crafting jobs, processing 1 crafting job per crafting"
        );
        provider.add("tooltip.neoecoae.crafting_jobs_l4", "Store Crafting Jobs: x%d [L4]");
        provider.add("tooltip.neoecoae.crafting_jobs_l6", "Store Crafting Jobs: x%d [L6]");
        provider.add("tooltip.neoecoae.crafting_jobs_l9", "Store Crafting Jobs: x%d [L9]");
        provider.add("tooltip.neoecoae.power_multiply_l4", "Power Multiply: x%d [L4]");
        provider.add("tooltip.neoecoae.power_multiply_l6", "Power Multiply: x%d [L6]");
        provider.add("tooltip.neoecoae.power_multiply_l9", "Power Multiply: x%d [L9]");

        addLangs(provider, "tooltip.neoecoae.crafting_pattern_bus",
            "ECO - FD Smart Pattern Bus is the main part of the crafting subsystem",
            "Each bus can store 63 patten",
            "When encoding patten on the ME Encoding Terminal, you can use the adjacent button to quickly upload them"
        );

        provider.add("tooltip.neoecoae.computation_system", "The core of the computation subsystem");
        addLangs(provider, "tooltip.neoecoae.computation_system_desc",
            "The computation subsystem introduces virtual Crafting Processors (vCPUs):",
            "The host provides only one vCPU to the ME network at a time, with capacity equal to all currently available bytes in the subsystem",
            "When a user assigns a crafting task to a vCPU, the host automatically adjusts the vCPU's byte allocation to the task's requirements before assigning it to a Threading Core",
            "New vCPUs can be allocated continuously until the total allocated vCPUs reach the max thread count",
            "vCPUs are immediately destroyed when the crafting task completes and all items are returned"
        );

        addLangs(provider, "tooltip.neoecoae.computation_drive",
            "Can drive flash crystal matrix",
                "The drivable flash crystal matrix tier depends on the computation subsystem host controller"
        );
        addLangs(provider, "tooltip.neoecoae.computation_threading_core",
            "Threading Core is the main part of the computation subsystem, providing thread count to the host controller",
            "Threads determine the maximum virtual CPUs for the computation subsystem",
            "When destroyed, compressed CPU data will be directly saved to the dropped item"
        );
        provider.add("tooltip.neoecoae.max_thread_count", "Max thread count +%d");
        addLangs(provider, "tooltip.neoecoae.computation_parallel_core",
            "Parallel Core provides parallel count to the computation subsystem",
            "Parallel count increases the processing numbers per crafting task for all threading cores"
        );
        provider.add("tooltip.neoecoae.computation_cell", "Provides %s bytes to the computation subsystem");

        provider.add("neoecoae.classic_pack", "Neo ECO AE Extension Classic Textures");

        provider.add("tooltip.neoecoae.budding_energized_crystal_block", "Obtained by striking Budding Certus Quartz with lightning");
    }

    private static void addLangs(RegistrateLangProvider provider, String key, String... langs) {
        for (int i = 0; i < langs.length; i++) {
            provider.add(key + "." + i, langs[i]);
        }
    }
}
