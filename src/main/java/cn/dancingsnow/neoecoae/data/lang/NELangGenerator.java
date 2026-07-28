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
        provider.add("jade.neoecoae.worker_tasks", "Crafting Tasks (%d):");
        provider.add("jade.neoecoae.worker_task", "  %s x%s - %s");
        provider.add("jade.neoecoae.worker_task.progress", "%d%%");
        provider.add("jade.neoecoae.worker_task.waiting_output", "Waiting for output");
        provider.add("jade.neoecoae.worker_task.unknown", "Unknown output");
        provider.add("jade.neoecoae.worker_tasks.more", "  ... and %d more");
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
        provider.add("tooltip.neoecoae.storage.infinite_component_locked", "Cannot remove infinite components: stored contents cannot safely fit back into normal matrices");
        provider.add("tooltip.neoecoae.infinite_component.unlock", "Insert 64 components and install 16 L9 storage matrices to unlock infinite storage");
        provider.add("tooltip.neoecoae.storage.infinite_member", "Managed by the storage host");
        provider.add("tooltip.neoecoae.storage.infinite_member_locked", "Infinite storage matrices cannot be removed while the storage host is in infinite mode");

        provider.add("cell_type.neoecoae.omni", "Omni");
        provider.add("cell_type.neoecoae.complex_omni", "Complex Omni");
        provider.add("cell_type.neoecoae.quantum_omni", "Quantum Omni");
        provider.add("item.neoecoae.eco_mana_cell_housing", "ECO Storage Matrix Housing (Mana)");
        provider.add("item.neoecoae.eco_mana_storage_cell_16m", "ECO - LE4 Storage Matrix (Mana)");
        provider.add("item.neoecoae.eco_mana_storage_cell_64m", "ECO - LE6 Storage Matrix (Mana)");
        provider.add("item.neoecoae.eco_mana_storage_cell_256m", "ECO - LE9 Storage Matrix (Mana)");

        provider.add("tooltip.neoecoae.crafting_system", "The core of the crafting subsystem");
        provider.add("tooltip.neoecoae.crafting_network_switch", "Links F9 crafting subsystem hosts on the same ME network");
        provider.add("tooltip.neoecoae.crafting_parallels", "Parallel cores provide processing capacity for overflow overclock calculations");
        provider.add("tooltip.neoecoae.max_parallel_count", "Max parallel count +%d");
        provider.add("tooltip.neoecoae.overclocked", "When enabling overclocking:");
        provider.add("tooltip.neoecoae.active_cooling", "When enabling active cooling:");
        provider.add("tooltip.neoecoae.clear_negative_effect", "Clear the negative effects of overclocking");

        addLangs(provider, "tooltip.neoecoae.crafting_worker",
            "ECO - FX Worker is the main part of the crafting subsystem",
            "Each FX Worker provides 1 thread at x1, or one thread per participating F host while network exchange is active"
        );
        provider.add("tooltip.neoecoae.crafting_jobs_l4", "Store Crafting Jobs: x%d [L4]");
        provider.add("tooltip.neoecoae.crafting_jobs_l6", "Store Crafting Jobs: x%d [L6]");
        provider.add("tooltip.neoecoae.crafting_jobs_l9", "Store Crafting Jobs: x%d [L9]");
        provider.add("tooltip.neoecoae.power_multiply_l4", "Power Multiply: x%d [L4]");
        provider.add("tooltip.neoecoae.power_multiply_l6", "Power Multiply: x%d [L6]");
        provider.add("tooltip.neoecoae.power_multiply_l9", "Power Multiply: x%d [L9]");

        addLangs(provider, "tooltip.neoecoae.crafting_pattern_bus",
            "ECO - FD Smart Pattern Bus is the main part of the crafting subsystem",
            "This bus currently stores %d patterns",
            "When encoding patterns on the ME Encoding Terminal, you can use the adjacent button to upload them quickly"
        );

        provider.add("tooltip.neoecoae.computation_system", "The core of the computation subsystem");
        provider.add("tooltip.neoecoae.computation_network_switch", "Links C9 computation subsystem hosts on the same ME network");
        provider.add("tooltip.neoecoae.network_switch.multiplier", "Each linked host contributes x%d capacity");
        provider.add("tooltip.neoecoae.network_switch.power_multiplier", "Power consumption while linked: x%d");
        provider.add("tooltip.neoecoae.network_switch.requirement", "Requires at least 2 linked hosts; a single host remains at x1");
        provider.add("tooltip.neoecoae.network_switch.computation_cooling", "Requires a cooling controller on this host");
        provider.add("tooltip.neoecoae.network_switch.computation_high_energy_cooling", "Requires a C9 cooling controller on this host");
        provider.add("tooltip.neoecoae.network_switch.computation_ultimate", "8 high-energy C9 hosts with at least 10 Threading Cores and every drive filled raise aggregate parallel count to INT32_MAX and CPU storage to INT64_MAX");
        provider.add("tooltip.neoecoae.network_switch.crafting_cooling", "Shared pool: 4 coolant per active task thread per tick; active exchange continuously draws full rated power");
        provider.add("tooltip.neoecoae.network_switch.crafting_high_energy_cooling", "Highest-tier shared pool: 16 coolant per active task thread per tick; active exchange continuously draws full rated power");
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
