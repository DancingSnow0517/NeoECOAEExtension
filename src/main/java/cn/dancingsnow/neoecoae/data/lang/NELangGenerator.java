package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class NELangGenerator {
    public static void accept(RegistrateLangProvider provider) {
        provider.add("gui.neoecoae.exact_stored_amount", "Stored: %s");
        GuiLangs.accept(provider);
        ConfigLangs.accept(provider);

        // jade
        provider.add("config.jade.plugin_neoecoae.eco_drive", "ECO Drive");
        provider.add("config.jade.plugin_neoecoae.eco_crafting_worker", "ECO Crafting Worker");
        provider.add("config.jade.plugin_neoecoae.eco_crafting_system", "ECO Crafting System");
        provider.add("config.jade.plugin_neoecoae.eco_computation_system", "ECO Computation System");

        provider.add("jade.neoecoae.drive_mounted", "ECO drive mounted");
        provider.add("jade.neoecoae.drive_unmounted", "ECO drive unmounted");
        provider.add("jade.neoecoae.drive_input_mode", "Input mode");
        provider.add("jade.neoecoae.drive_output_mode", "Output mode");
        provider.add("jade.neoecoae.worker_threads", "Threads: %s/%s");
        provider.add("jade.neoecoae.formed", "Formed: %s");
        provider.add("jade.neoecoae.running", "Running: %s");
        provider.add("jade.neoecoae.yes", "Yes");
        provider.add("jade.neoecoae.no", "No");
        provider.add("jade.neoecoae.thread_label", "Threads: ");
        provider.add("jade.neoecoae.storage_label", "Storage: ");
        provider.add("jade.neoecoae.energy_per_tick_label", "Energy: ");
        provider.add("jade.neoecoae.time_multiplier_label", "Recipe time ratio (lower is faster): ");
        provider.add("jade.neoecoae.effective_throughput_label", "Effective throughput: ");
        provider.add("jade.neoecoae.crafts_per_tick", "%s crafts/t");
        provider.add("jade.neoecoae.virtual_throughput", "Virtual ledger");
        provider.add("jade.neoecoae.local_processing", "Local processing: x1 (1 host)");
        provider.add("jade.neoecoae.network_exchange", "Network exchange: x%s (%s hosts)");
        provider.add(
                "jade.neoecoae.network_exchange_rule",
                "Each host contributes independent lanes; x%s multiplies the batch per lane.");
        provider.add("jade.neoecoae.overclock_multiplier_label", "Overclock multiplier: ");
        provider.add("jade.neoecoae.recipes_per_operation_label", "Recipes per operation: ");
        provider.add("jade.neoecoae.active_craft", "Crafting: %s x%s (%s/%s)");
        provider.add("jade.neoecoae.more_active_crafts", "+%s more active crafts");
        provider.add("jade.neoecoae.working_crafts_label", "Working crafts: ");
        provider.add("jade.neoecoae.recipes_suffix", " recipes");
        provider.add("jade.neoecoae.overclocked", "Overclock enabled");
        provider.add("jade.neoecoae.activeCooling", "Active cooling enabled");
        provider.add("jade.neoecoae.coolant", "Coolant: %s");
        provider.add("jade.neoecoae.coolant_max_overclock", "Coolant max overclock: %s");
        provider.add("jade.neoecoae.coolant_max_overclock.none", "Coolant max overclock: None");
        provider.add("jade.neoecoae.overclock_status", "Theoretical/Effective overclock: %s/%s");
        provider.add("jade.neoecoae.crafting.worker_count", "Worker core count: %s");
        provider.add("jade.neoecoae.crafting.thread_usage", "Working threads: %s/%s");
        provider.add("jade.neoecoae.crafting.progress", "Batch progress: %s / %s t");
        provider.add("jade.neoecoae.crafting.progress_value", "Single-thread progress: %s / 100");
        provider.add("jade.neoecoae.crafting.avg_progress", "Average progress: %s / 100");
        provider.add("jade.neoecoae.crafting.speed", "Single-thread speed: %s progress/t");
        provider.add("jade.neoecoae.crafting.duration", "Theoretical single-run time: %s tick / %s s");
        provider.add("jade.neoecoae.crafting.batch_slots", "Current batch slots: %s");
        provider.add("jade.neoecoae.crafting.queue_per_worker", "Queue per worker: %s");
        provider.add("jade.neoecoae.computation.accelerators", "Accelerator count: %s");
        provider.add("jade.neoecoae.computation.dispatch_limit", "CPU dispatch limit: %s patterns/t");
        provider.add("jade.neoecoae.computation.thread_usage", "Threads used: %s/%s");
        provider.add("jade.neoecoae.computation.storage_usage", "Storage used: %s / %s bytes");

        provider.add("jade.neoecoae.worker_capacity_formula", "Capacity Formula: %s");
        provider.add("jade.neoecoae.worker_network_x2", "Connected to %d x2 network exchange hosts");
        provider.add("jade.neoecoae.worker_network_x8", "Connected to %d x8 network exchange hosts");
        provider.add("jade.neoecoae.worker_task", "  %s x%s - %s");
        provider.add("jade.neoecoae.worker_task.progress", "%d%%");
        provider.add("jade.neoecoae.worker_task.unknown", "Unknown output");
        provider.add("jade.neoecoae.worker_task.waiting_output", "Waiting for output");
        provider.add("jade.neoecoae.worker_tasks", "Active crafting tasks (%d):");
        provider.add("jade.neoecoae.worker_tasks.more", "  ...and %d more tasks");

        provider.add("neoecoae.tooltip.upload_pattern", "Upload Pattern");
        provider.add(
                "neoecoae.pattern_upload.toggle_hint",
                "Shift-click: toggle automatic upload after encoding (this terminal session).");
        provider.add(
                "neoecoae.pattern_upload.auto_enabled", "Automatic pattern upload enabled for this terminal session.");
        provider.add("neoecoae.pattern_upload.auto_disabled", "Automatic pattern upload disabled.");
        provider.add("neoecoae.pattern_upload.inserted", "Pattern uploaded to an ECO fabricator.");
        provider.add(
                "neoecoae.pattern_upload.already_present", "This pattern already exists. The source pattern was kept.");
        provider.add(
                "neoecoae.pattern_upload.no_space", "Compatible ECO pattern buses are full. The pattern was kept.");
        provider.add(
                "neoecoae.pattern_upload.incompatible",
                "Unsupported pattern: ECO fabricators accept assembler-compatible crafting patterns, not processing patterns.");
        provider.add(
                "neoecoae.pattern_upload.no_target", "No compatible active ECO pattern bus found on this ME network.");
        provider.add(
                "neoecoae.pattern_upload.unavailable",
                "Upload unavailable: check the ME connection and access permissions.");
        provider.add("neoecoae.pattern_upload.empty", "Encode a pattern before uploading.");

        provider.add("cell_type.neoecoae.chemical", "Chemical");
        provider.add("cell_type.neoecoae.chemicals", "Chemical");
        provider.add("cell_type.neoecoae.complex_omni", "Complex Omni");
        provider.add("cell_type.neoecoae.flux", "FE");
        provider.add("cell_type.neoecoae.mana", "Mana");
        provider.add("cell_type.neoecoae.omni", "Omni");
        provider.add("cell_type.neoecoae.quantum_omni", "Quantum Omni");
        provider.add("cell_type.neoecoae.source", "Source");

        provider.add("cell_type.neoecoae.lightning", "Lightning");
        provider.add("cell_type.neoecoae.mega_chemical", "Mega Chemical");
        provider.add("cell_type.neoecoae.mega_energy", "Mega Energy");
        provider.add("cell_type.neoecoae.mega_fluid", "Mega Fluid");
        provider.add("cell_type.neoecoae.mega_item", "Mega Item");
        provider.add("cell_type.neoecoae.other", "Other");
        provider.add("neoecoae.unknow_cell_type", "Unknown Storage Cell Type");
        provider.add("neoecoae.unknown_cell_type", "Unknown Storage Cell Type");

        provider.add("category.neoecoae.cooling", "Cooling");
        provider.add("category.neoecoae.cooling.coolant", "Coolant: %s");
        provider.add("category.neoecoae.cooling.max_overclock", "Max Overclock: %s");
        provider.add("category.neoecoae.multiblock", "ECO Multiblock Info");
        provider.add("category.neoecoae.integrated_working_station", "Integrated Working Station");

        provider.add("emi.category.neoecoae.multiblock", "ECO Multiblock Info");
        provider.add("emi.category.neoecoae.integrated_working_station", "Integrated Working Station");
        provider.add("emi.category.neoecoae.cooling", "Cooling");

        // Additional item and block names
        provider.add("block.neoecoae.fx_monitor_core", "ECO - FX Monitor Core");
        provider.add("item.neoecoae.eco_lightning_cell_16m", "ECO - LE4 Lightning Storage Matrix");
        provider.add("item.neoecoae.eco_lightning_cell_256m", "ECO - LE9 Lightning Storage Matrix");
        provider.add("item.neoecoae.eco_lightning_cell_64m", "ECO - LE6 Lightning Storage Matrix");
        provider.add("item.neoecoae.eco_lightning_cell_housing", "ECO Lightning Storage Matrix Housing");

        provider.add("tooltip.neoecoae.holdshift", "Hold [Shift] for more info");
        provider.add("tooltip.neoecoae.max_lenth", "§7§oMax structure length: %s");

        provider.add("tooltip.neoecoae.storage_system", "§7§oThe core of the storage system");
        addLangs(
                provider,
                "tooltip.neoecoae.storage_dirve",
                "§7§oDrives storage matrices",
                "§7§oThe levels of storage matrices it can drive are determined by the storage Controller");

        provider.add("tooltip.neoecoae.crafting_system", "§7§oThe core of the crafting subsystem");
        provider.add(
                "tooltip.neoecoae.crafting_parallels",
                "§7§oParallel cores provide parallel processing for the crafting subsystem");
        provider.add("tooltip.neoecoae.max_parallel_count", "§7§oMax parallel +%s");
        provider.add("tooltip.neoecoae.overclocked", "§7§oWhen overclocked:");
        provider.add("tooltip.neoecoae.active_cooling", "§7§oWhen active cooling is enabled:");
        provider.add("tooltip.neoecoae.clear_negative_effect", "§7§oClears negative effects caused by overclocking");

        addLangs(
                provider,
                "tooltip.neoecoae.crafting_worker",
                "§7§oECO - FX Worker Core is a primary component of the crafting subsystem",
                "§7§oECO - FX worker core can store 32 crafting jobs and processes one at a time");
        provider.add("tooltip.neoecoae.crafting_jobs_l4", "§7§oStores crafting jobs: x%s [L4]");
        provider.add("tooltip.neoecoae.crafting_jobs_l6", "§7§oStores crafting jobs: x%s [L6]");
        provider.add("tooltip.neoecoae.crafting_jobs_l9", "§7§oStores crafting jobs: x%s [L9]");
        provider.add("tooltip.neoecoae.power_multiply_l4", "§7§oPower multiplier: x%s [L4]");
        provider.add("tooltip.neoecoae.power_multiply_l6", "§7§oPower multiplier: x%s [L6]");
        provider.add("tooltip.neoecoae.power_multiply_l9", "§7§oPower multiplier: x%s [L9]");

        addLangs(
                provider,
                "tooltip.neoecoae.crafting_pattern_bus",
                "§7§oECO - FD Smart Pattern Bus is a core part of the crafting subsystem",
                "§7§oEach page can store 63 patterns; the number of pages is configurable",
                "§7§oWhen encoding patterns on an ME encoding terminal, use the adjacent button to quick upload",
                "§7§oBreaking normally preserves stored patterns in the dropped block",
                "§7§oSneak-breaking drops stored patterns instead");

        provider.add("tooltip.neoecoae.computation_system", "§7§oThe core of the computation subsystem");
        addLangs(
                provider,
                "tooltip.neoecoae.computation_system_desc",
                "§7§oThe computation subsystem introduces virtual craft processors (vCPU):",
                "§7§oThe Controller exposes a single vCPU to the ME network with capacity equal to the subsystem's currently available bytes",
                "§7§oWhen a user submits a crafting job to a vCPU, the Controller will auto-adjust the vCPU's byte capacity to the job's requirement and then allocate it to a threading core",
                "§7§oNew vCPUs can be allocated as long as the number of allocated vCPUs does not exceed max threads",
                "§7§ovCPUs are destroyed once a crafting job is complete and all items have been returned");

        addLangs(
                provider,
                "tooltip.neoecoae.computation_drive",
                "§7§oDrives flash crystal arrays",
                "§7§oThe levels of flash crystal arrays it can drive are determined by the computation Controller");
        addLangs(
                provider,
                "tooltip.neoecoae.computation_threading_core",
                "§7§oThreading cores are the main component of the computation subsystem and provide threads to the Controller",
                "§7§oThreads determine the maximum number of virtual craft processors (vCPUs)",
                "§7§oWhen dismantled, compressed CPU data is saved to the drop");
        provider.add("tooltip.neoecoae.max_thread_count", "§7§oMax threads +%s");
        addLangs(
                provider,
                "tooltip.neoecoae.computation_parallel_core",
                "§7§oParallel cores provide parallelism for the computation subsystem",
                "§7§oParallelism increases the number of tasks each thread core can process per tick");
        provider.add("tooltip.neoecoae.computation_cell", "§7§oProvides %s bytes to computation subsystem");

        provider.add(
                "tooltip.neoecoae.crafting_network_switch", "Links F9 crafting subsystem hosts on the same ME network");
        provider.add(
                "tooltip.neoecoae.computation_network_switch",
                "Links C9 computation subsystem hosts on the same ME network");
        provider.add("tooltip.neoecoae.network_switch.multiplier", "Each linked host contributes x%d capacity");
        provider.add("tooltip.neoecoae.network_switch.power_multiplier", "Power consumption while linked: x%d");
        provider.add(
                "tooltip.neoecoae.network_switch.requirement",
                "Requires at least 2 linked hosts; a single host remains at x1");
        provider.add(
                "tooltip.neoecoae.network_switch.computation_cooling", "Requires a cooling controller on this host");
        provider.add(
                "tooltip.neoecoae.network_switch.computation_high_energy_cooling",
                "Requires a C9 cooling controller on this host");
        provider.add(
                "tooltip.neoecoae.network_switch.crafting_cooling",
                "Shared pool: 4 coolant per active task thread per tick; active exchange continuously draws full rated power");
        provider.add(
                "tooltip.neoecoae.network_switch.crafting_high_energy_cooling",
                "Highest-tier shared pool: 16 coolant per active task thread per tick; active exchange continuously draws full rated power");

        provider.add("tag.item.ae2.inscriber_presses", "Inscriber Presses");
        provider.add("tag.item.ae2.metal_ingots", "Metal Ingots");
        provider.add("tag.item.c.budding_blocks", "Budding Blocks");
        provider.add("tag.item.c.clusters", "Clusters");
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
        provider.add("tag.item.c.storage_blocks.aluminum", "Blocks of Aluminum");
        provider.add("tag.item.c.storage_blocks.aluminum_alloy", "Blocks of Aluminum Alloy");
        provider.add("tag.item.c.storage_blocks.black_tungsten_alloy", "Blocks of Black Tungsten Alloy");
        provider.add("tag.item.c.storage_blocks.energized_crystal", "Blocks of Energized Crystal");
        provider.add("tag.item.c.storage_blocks.energized_fluix_crystal", "Blocks of Energized Fluix Crystal");
        provider.add("tag.item.c.storage_blocks.raw_aluminum", "Blocks of Raw Aluminum");
        provider.add("tag.item.c.storage_blocks.raw_tungsten", "Blocks of Raw Tungsten");
        provider.add("tag.item.c.storage_blocks.tungsten", "Blocks of Tungsten");
        provider.add("tag.item.c.tools.mining_tool", "Mining Tools");
        provider.add("tag.item.neoecoae.crystal_ingot_base", "Crystal Ingot Bases");
        provider.add("tag.item.neoecoae.superconductive_ingot_base", "Superconductive Ingot Bases");
        provider.add(
                "tooltip.neoecoae.fx_monitor_core.0",
                "§7§oThe ECO - FX Monitor Core provides an ME-style crafting monitor");
        provider.add(
                "tooltip.neoecoae.fx_monitor_core.1",
                "§7§oDisplays crafting jobs being processed by the FX crafting subsystem");
        provider.add(
                "tooltip.neoecoae.infinite_component.components",
                "Component slot: 64 infinite storage components (insert them on the storage host screen)");
        provider.add("tooltip.neoecoae.infinite_component.header", "Infinite Storage Requirements");
        provider.add(
                "tooltip.neoecoae.infinite_component.matrices",
                "Drives: any 12 L9 storage matrices in the same storage host");
        provider.add(
                "tooltip.neoecoae.network_switch.computation_ultimate",
                "With 8 high-energy C9 hosts, each with at least 10 threading cores and all drives filled with flash crystal arrays, combined CPU parallelism reaches 2.1E and storage reaches 9.2E");
        provider.add("tooltip.neoecoae.pattern.verified_durability", "§6Verified: Durability Pattern");
        provider.add("tooltip.neoecoae.pattern.verified_normal", "§6Verified: Normal Pattern");
        provider.add("tooltip.neoecoae.pattern.verified_self_growing", "§6Verified: Self-Growing Pattern");
        provider.add(
                "tooltip.neoecoae.pattern.verified_smithing_stonecutting",
                "§6Verified: Smithing / Stonecutting Recipe");
        provider.add("tooltip.neoecoae.pattern.verified_special_nbt", "§6Verified: Special NBT Recipe");
        provider.add(
                "tooltip.neoecoae.storage.infinite_component_locked",
                "Cannot remove infinite components: current contents cannot safely return to normal storage matrices");
        provider.add(
                "tooltip.neoecoae.storage.infinite_member_locked",
                "Cannot remove managed storage matrices while infinite storage is enabled");

        provider.add("neoecoae.classic_pack", "Neo ECO AE Extension Classic Textures");

        provider.add(
                "tooltip.neoecoae.budding_energized_crystal_block",
                "Obtained from lightning-struck quartz budding rock");
    }

    private static void addLangs(RegistrateLangProvider provider, String key, String... langs) {
        for (int i = 0; i < langs.length; i++) {
            provider.add(key + "." + i, langs[i]);
        }
    }
}
