package cn.dancingsnow.neoecoae.core.content;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class LanguageContent {
    private LanguageContent() {}
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
        provider.accept("gui.neoecoae.big_order.requested", "Requested: %s");
        provider.accept("gui.neoecoae.big_order.requires_cpu", "BigInt orders require an ECO CPU");
        provider.accept("gui.neoecoae.big_order.single_task", "ECO CPU: execute the complete order as one task");
        provider.accept("gui.neoecoae.storage_interface.ignore_creative.on", "Ignore creative cell input: On");
        provider.accept("gui.neoecoae.storage_interface.ignore_creative.off", "Ignore creative cell input: Off");
        provider.accept("gui.neoecoae.storage_interface.ignore_creative.description", "Input mode only: skip resources whose stored amount does not change after extraction");
        provider.accept("tooltip.neoecoae.big_order.requires_cpu", "This order uses an extended quantity; an idle, online ECO crafting CPU is required");
        provider.accept("gui.neoecoae.import_jei_bookmarks", "★");
        provider.accept("gui.neoecoae.import_jei_bookmarks.tooltip", "Import all JEI bookmarks into the component configuration");
        GuiLanguageContent.accept(provider);
        ConfigLanguageContent.accept(provider);
        provider.accept("gui.neoecoae.crafting.ui.batch_parallel", "Batch");
        provider.accept("gui.neoecoae.crafting.smart_doubling", "ECO Smart Doubling");
        provider.accept("gui.neoecoae.crafting.mode_switch_blocked", "Cannot switch while a crafting task is running");
        provider.accept("gui.neoecoae.crafting.ui.recipe_slots", "Slots");
        provider.accept("gui.ae2.ConfirmCraftCpuStatus", "Storage: %s; Co-Processing Units: %s");
        provider.accept("gui.ae2.ConfirmCraftNoCpu", "Storage: N/A; Co-Processing Units: N/A");
        provider.accept("gui.neoecoae.crafting.cycle_planning.off", "Cycle Planning: Disabled");
        provider.accept("gui.neoecoae.crafting.cycle_planning.on", "Cycle Planning: Enabled");
        provider.accept("gui.neoecoae.force_start", "Force Start");
        provider.accept("tooltip.neoecoae.storage.bulk_unresolved", "%s bulk entries are preserved but unavailable. Restore missing items or the original recipes before recovery.");
        provider.accept("gui.neoecoae.storage.mega_filter.cutoff", "Compression display: %s");
        provider.accept("gui.neoecoae.storage.mega_filter.cutoff_hint", "Shift + left-click raises, Shift + right-click lowers the compression display; the marker changes too");
        provider.accept("gui.neoecoae.storage.mega_filter.duplicate_chain", "This compression chain is already configured in another marking slot of this storage host");
        provider.accept("gui.neoecoae.storage.mega_filter.not_compressible", "Only items belonging to a MEGA compression chain can be marked");
        provider.accept("jade.neoecoae.overclock_status", "Theoretical / Effective Overclock: %d/%d");
        provider.accept("tooltip.neoecoae.force_start", "Ignore missing materials and submit the crafting job");

        // jade
        provider.accept("config.jade.plugin_neoecoae.eco_drive", "ECO Drive");
        provider.accept("config.jade.plugin_neoecoae.eco_computation_drive", "ECO Computation Drive");
        provider.accept("config.jade.plugin_neoecoae.eco_computation_system", "ECO Computation System");
        provider.accept("config.jade.plugin_neoecoae.eco_crafting_worker", "ECO Crafting Worker");
        provider.accept("config.jade.plugin_neoecoae.eco_crafting_system", "ECO Crafting System");
        provider.accept("config.jade.plugin_neoecoae.eco_storage_interface", "ECO Storage Interface");
        provider.accept("config.jade.plugin_neoecoae.eco_storage_system", "ECO Storage System");
        provider.accept("config.jade.plugin_neoecoae.large_workstation_current_inputs", "Large Workstation Current Inputs");
        provider.accept("jade.neoecoae.large_workstation.current_inputs", "Current inputs:");
        provider.accept("jade.neoecoae.large_workstation.current_inputs.empty", "Current inputs: empty");

        provider.accept("jade.neoecoae.drive_mounted", "ECO Drive Mounted");
        provider.accept("jade.neoecoae.drive_unmounted", "ECO Drive Unmounted");
        provider.accept("jade.neoecoae.storage_matrix_tier_too_high", "Storage matrix tier exceeds host tier");
        provider.accept("jade.neoecoae.computation_cell_tier_too_high", "Computation cell tier exceeds host tier");
        provider.accept("jade.neoecoae.worker_threads", "Threads: %d/%d");
        provider.accept("jade.neoecoae.worker_network_x2", "Connected to %d Network Exchange x2 host(s)");
        provider.accept("jade.neoecoae.worker_network_x8", "Connected to %d Network Exchange x8 host(s)");
        provider.accept("jade.neoecoae.worker_capacity_formula", "Capacity formula: %s");
        provider.accept("jade.neoecoae.overclocked", "Overclock Enabled");
        provider.accept("jade.neoecoae.overclock_disabled", "Overclock Disabled");
        provider.accept("jade.neoecoae.activeCooling", "Active Cooling Enabled");
        provider.accept("jade.neoecoae.active_cooling_disabled", "Active Cooling Disabled");
        provider.accept("jade.neoecoae.coolant", "Coolant %s");
        provider.accept("jade.neoecoae.coolant_max_overclock", "Coolant Max Overclock: %d");
        provider.accept("jade.neoecoae.coolant_max_overclock.none", "Coolant Max Overclock: None");
        provider.accept("jade.neoecoae.network_composition", "Network: %d %s+%d %s");
        provider.accept("jade.neoecoae.normal_switch", "normal exchange(s)");
        provider.accept("jade.neoecoae.high_energy_switch", "high-energy exchange(s)");
        provider.accept("jade.neoecoae.total_crafting_capacity", "Total Crafting Capacity: %s");
        provider.accept("jade.neoecoae.crafting_capacity", "Crafting Capacity: %s");
        provider.accept("jade.neoecoae.fast_planner.enabled", "ECO Fast Planning Enabled");
        provider.accept("jade.neoecoae.fast_planner.disabled", "ECO Fast Planning Disabled");
        provider.accept("jade.neoecoae.cycle_planning.enabled", "ECO Cycle Planning Enabled");
        provider.accept("jade.neoecoae.cycle_planning.disabled", "ECO Cycle Planning Disabled");

        provider.accept("neoecoae.tooltip.upload_pattern", "Upload Pattern into available ECO Crafting System");

        provider.accept("category.neoecoae.cooling", "Cooling");
        provider.accept("category.neoecoae.cooling.coolant", "Coolant: %d");
        provider.accept("category.neoecoae.cooling.max_overclock", "Max Overclock: %d");
        provider.accept("category.neoecoae.multiblock", "ECO Multiblock Info");
        provider.accept("category.neoecoae.integrated_working_station", "Integrated Working Station");

        provider.accept("cell_type.neoecoae.complex_omni", "Complex Omni");
        provider.accept("cell_type.neoecoae.lightning", "Lightning");
        provider.accept("cell_type.neoecoae.data", "Data");
        provider.accept("cell_type.neoecoae.mega_chemical", "MEGA Chemical");
        provider.accept("cell_type.neoecoae.mega_energy", "MEGA Energy");
        provider.accept("cell_type.neoecoae.mega_fluid", "MEGA Fluid");
        provider.accept("cell_type.neoecoae.mega_item", "MEGA Item");
        provider.accept("cell_type.neoecoae.omni", "Omni");
        provider.accept("cell_type.neoecoae.quantum_omni", "Quantum Omni");
        provider.accept("cell_type.neoecoae.spent_nuclear_waste", "Spent Nuclear Waste");
        provider.accept("block.neoecoae.large_integrated_working_station", "Large Integrated Working Station");
        provider.accept("item.neoecoae.eco_mana_cell_housing", "ECO Mana Storage Matrix Housing");
        provider.accept("item.neoecoae.eco_mana_storage_cell_16m", "ECO - LE4 Mana Storage Matrix");
        provider.accept("item.neoecoae.eco_mana_storage_cell_64m", "ECO - LE6 Mana Storage Matrix");
        provider.accept("item.neoecoae.eco_mana_storage_cell_256m", "ECO - LE9 Mana Storage Matrix");
        provider.accept("item.neoecoae.eco_data_cell_housing", "ECO Storage Matrix Housing (Data)");
        provider.accept("item.neoecoae.eco_data_storage_cell_16m", "ECO - LE4 Storage Matrix (Data)");
        provider.accept("item.neoecoae.eco_data_storage_cell_64m", "ECO - LE6 Storage Matrix (Data)");
        provider.accept("item.neoecoae.eco_data_storage_cell_256m", "ECO - LE9 Storage Matrix (Data)");
        provider.accept("cell_type.neoecoae.mega_mana", "MEGA Mana");
        provider.accept("item.neoecoae.mega_mana_cell_housing", "ECO MEGA Storage Matrix Housing (Mana)");
        provider.accept("item.neoecoae.eco_mega_mana_cell_4g", "ECO - LE9 Storage Matrix (Mega Mana)");

        provider.accept("emi.category.neoecoae.multiblock", "ECO Multiblock Info");
        provider.accept("emi.category.neoecoae.integrated_working_station", "Integrated Working Station");
        provider.accept("emi.category.neoecoae.cooling", "Cooling");
        provider.accept("tag.item.ae2.inscriber_presses", "Inscriber Presses");
        provider.accept("tag.item.ae2.metal_ingots", "Metal Ingots");
        provider.accept("tag.item.c.budding_blocks", "Budding Blocks");
        provider.accept("tag.item.c.clusters", "Crystal Clusters");
        provider.accept("tag.item.c.dusts.aluminum", "Aluminum Dusts");
        provider.accept("tag.item.c.dusts.aluminum_alloy", "Aluminum Alloy Dusts");
        provider.accept("tag.item.c.dusts.black_tungsten_alloy", "Black Tungsten Alloy Dusts");
        provider.accept("tag.item.c.dusts.energized_crystal", "Energized Crystal Dusts");
        provider.accept("tag.item.c.dusts.energized_fluix_crystal", "Energized Fluix Crystal Dusts");
        provider.accept("tag.item.c.dusts.tungsten", "Tungsten Dusts");
        provider.accept("tag.item.c.gems.energized_crystal", "Energized Crystals");
        provider.accept("tag.item.c.gems.energized_fluix_crystal", "Energized Fluix Crystals");
        provider.accept("tag.item.c.ingots.aluminum", "Aluminum Ingots");
        provider.accept("tag.item.c.ingots.aluminum_alloy", "Aluminum Alloy Ingots");
        provider.accept("tag.item.c.ingots.black_tungsten_alloy", "Black Tungsten Alloy Ingots");
        provider.accept("tag.item.c.ingots.tungsten", "Tungsten Ingots");
        provider.accept("tag.item.c.ores.aluminum", "Aluminum Ores");
        provider.accept("tag.item.c.ores.tungsten", "Tungsten Ores");
        provider.accept("tag.item.c.raw_materials.aluminum", "Raw Aluminum");
        provider.accept("tag.item.c.raw_materials.tungsten", "Raw Tungsten");
        provider.accept("tag.item.c.storage_blocks.aluminum", "Aluminum Storage Blocks");
        provider.accept("tag.item.c.storage_blocks.aluminum_alloy", "Aluminum Alloy Storage Blocks");
        provider.accept("tag.item.c.storage_blocks.black_tungsten_alloy", "Black Tungsten Alloy Storage Blocks");
        provider.accept("tag.item.c.storage_blocks.energized_crystal", "Energized Crystal Storage Blocks");
        provider.accept("tag.item.c.storage_blocks.energized_fluix_crystal", "Energized Fluix Crystal Storage Blocks");
        provider.accept("tag.item.c.storage_blocks.raw_aluminum", "Raw Aluminum Storage Blocks");
        provider.accept("tag.item.c.storage_blocks.raw_tungsten", "Raw Tungsten Storage Blocks");
        provider.accept("tag.item.c.storage_blocks.tungsten", "Tungsten Storage Blocks");
        provider.accept("tag.item.c.tools.mining_tool", "Mining Tools");
        provider.accept("tag.item.neoecoae.crystal_ingot_base", "Crystal Ingot Base");
        provider.accept("tag.item.neoecoae.superconductive_ingot_base", "Superconductive Ingot Base");

        provider.accept("tooltip.neoecoae.holdshift", "Hold [Shift] to show more info");
        provider.accept("tooltip.neoecoae.max_length", "Maximum length of structure: %d");

        provider.accept("tooltip.neoecoae.storage_system", "The core of the storage subsystem");
        addLangs(provider, "tooltip.neoecoae.storage_drive",
            "Can drive storage matrix",
            "The drivable storage matrix tier depends on the storage subsystem host controller"
        );
        provider.accept("tooltip.neoecoae.storage.infinite_component_locked", "Cannot remove infinite components: stored contents cannot safely fit back into normal matrices");
        provider.accept("tooltip.neoecoae.storage.infinite_member_locked", "Infinite storage matrices cannot be removed while the storage host is in infinite mode");
        provider.accept("gui.neoecoae.storage.members_missing", "Missing %s member matrices");
        provider.accept("gui.neoecoae.storage.members_return", "Please insert them again");
        provider.accept("jade.neoecoae.storage.infinite_enabled", "Infinite storage enabled");
        provider.accept("tooltip.neoecoae.storage.infinite_migration_locked", "Cannot remove this matrix while infinite storage migration is in progress");
        provider.accept("tooltip.neoecoae.infinite_component.header", "Infinite Storage: Activation Requirements");
        provider.accept("tooltip.neoecoae.infinite_component.components",
            "Component slots: 64 infinite storage components in the storage host");
        provider.accept("tooltip.neoecoae.infinite_component.matrices",
            "Drives: any 12 L9 storage matrices in the same storage host");
        provider.accept("tooltip.neoecoae.storage.infinite_member", "Managed by the storage host");
        provider.accept("tooltip.neoecoae.infinite_resource.contents", "Infinite supply: Water / Cobblestone / Lava");
        provider.accept("tooltip.neoecoae.infinite_resource.unbounded", "Can be extracted directly by the ME network and never runs out");
        provider.accept("tooltip.neoecoae.infinite_resource.sink", "Accepts only the three listed resources; inserted resources are destroyed");

        provider.accept("tooltip.neoecoae.crafting_system", "The core of the crafting subsystem");
        provider.accept("tooltip.neoecoae.large_integrated_working_station",
            "Large integrated workstation overclocking requires both overclock and active cooling");
        provider.accept("tooltip.neoecoae.large_integrated_working_station.water", "Water: 16,384 parallel, x8 recipe energy");
        provider.accept("tooltip.neoecoae.large_integrated_working_station.sodium", "Sodium: 65,536 parallel, x32 recipe energy");
        provider.accept("tooltip.neoecoae.large_integrated_working_station.cryotheum", "Cryotheum solution: 262,144 parallel, x64 recipe energy");
        provider.accept("tooltip.neoecoae.large_integrated_working_station.coolant_tick", "Consumes 100 mB coolant for each processing tick");
        provider.accept("tooltip.neoecoae.crafting_parallels", "Parallel core provides parallel count to the crafting subsystem");
        provider.accept("tooltip.neoecoae.max_parallel_count", "Max parallel count +%d");
        provider.accept("tooltip.neoecoae.overclocked", "When enabling overclocking:");
        provider.accept("tooltip.neoecoae.active_cooling", "When enabling active cooling:");
        provider.accept("tooltip.neoecoae.clear_negative_effect", "Clear the negative effects of overclocking");

        addLangs(provider, "tooltip.neoecoae.crafting_worker",
            "ECO - FX Worker is the main part of the crafting subsystem",
            "ECO - FX Worker can store 32 crafting jobs, processing 1 crafting job per crafting"
        );
        addLangs(provider, "tooltip.neoecoae.fx_monitor_core",
            "ECO - FX Monitor Core includes a ME-style crafting monitor",
            "Displays the crafting jobs handled by the FX crafting subsystem"
        );
        provider.accept("tooltip.neoecoae.crafting_jobs_l4", "Store Crafting Jobs: x%d [L4]");
        provider.accept("tooltip.neoecoae.crafting_jobs_l6", "Store Crafting Jobs: x%d [L6]");
        provider.accept("tooltip.neoecoae.crafting_jobs_l9", "Store Crafting Jobs: x%d [L9]");
        provider.accept("tooltip.neoecoae.power_multiply_l4", "Power Multiply: x%d [L4]");
        provider.accept("tooltip.neoecoae.power_multiply_l6", "Power Multiply: x%d [L6]");
        provider.accept("tooltip.neoecoae.power_multiply_l9", "Power Multiply: x%d [L9]");

        addLangs(provider, "tooltip.neoecoae.crafting_pattern_bus",
            "ECO - FD Smart Pattern Bus is the main part of the crafting subsystem",
            "Each bus can store 63 patterns",
            "When encoding patterns on the ME Encoding Terminal, you can use the adjacent button to quickly upload them"
        );

        provider.accept("tooltip.neoecoae.computation_system", "The core of the computation subsystem");
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
        provider.accept("tooltip.neoecoae.max_thread_count", "Max thread count +%d");
        addLangs(provider, "tooltip.neoecoae.computation_parallel_core",
            "Parallel Core provides parallel count to the computation subsystem",
            "Parallel count increases the processing numbers per crafting task for all threading cores"
        );
        provider.accept("tooltip.neoecoae.computation_cell", "Provides %s bytes to the computation subsystem");
        provider.accept("tooltip.neoecoae.computation_cell.install",
            "Right-click a computation subsystem host to install directly");

        provider.accept("neoecoae.classic_pack", "Neo ECO AE Extension Classic Textures");
        provider.accept("neoecoae.unknown_cell_type", "Unknown cell type");

        provider.accept("tooltip.neoecoae.budding_energized_crystal_block", "Obtained by striking Budding Certus Quartz with lightning");
    }

    private static void addLangs(BiConsumer<String, String> provider, String key, String... langs) {
        for (int i = 0; i < langs.length; i++) {
            provider.accept(key + "." + i, langs[i]);
        }
    }
}
