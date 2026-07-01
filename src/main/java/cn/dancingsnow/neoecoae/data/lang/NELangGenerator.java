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
        provider.add("config.jade.plugin_neoecoae.eco_computation_system", "ECO Computation System");

        provider.add("jade.neoecoae.drive_mounted", "ECO drive mounted");
        provider.add("jade.neoecoae.drive_unmounted", "ECO drive unmounted");
        provider.add("jade.neoecoae.worker_threads", "Threads: %d/%d");
        provider.add("jade.neoecoae.formed", "Formed: %s");
        provider.add("jade.neoecoae.running", "Running: %s");
        provider.add("jade.neoecoae.yes", "Yes");
        provider.add("jade.neoecoae.no", "No");
        provider.add("jade.neoecoae.thread_label", "Threads: ");
        provider.add("jade.neoecoae.storage_label", "Storage: ");
        provider.add("jade.neoecoae.energy_per_tick_label", "Energy: ");
        provider.add("jade.neoecoae.time_multiplier_label", "Time multiplier: ");
        provider.add("jade.neoecoae.overclock_multiplier_label", "Overclock multiplier: ");
        provider.add("jade.neoecoae.recipes_per_operation_label", "Recipes per operation: ");
        provider.add("jade.neoecoae.working_crafts_label", "Working crafts: ");
        provider.add("jade.neoecoae.recipes_suffix", " recipes");
        provider.add("jade.neoecoae.overclocked", "Overclock enabled");
        provider.add("jade.neoecoae.activeCooling", "Active cooling enabled");
        provider.add("jade.neoecoae.coolant", "Coolant: %d");
        provider.add("jade.neoecoae.coolant_max_overclock", "Coolant max overclock: %d");
        provider.add("jade.neoecoae.coolant_max_overclock.none", "Coolant max overclock: None");
        provider.add("jade.neoecoae.overclock_status", "Theoretical/Effective overclock: %d/%d");
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

        provider.add("neoecoae.tooltip.upload_pattern", "Upload Pattern");

        provider.add("cell_type.neoecoae.chemical", "Chemical");
        provider.add("cell_type.neoecoae.flux", "FE");
        provider.add("cell_type.neoecoae.mana", "Mana");
        provider.add("cell_type.neoecoae.source", "Source");

        provider.add("category.neoecoae.cooling", "Cooling");
        provider.add("category.neoecoae.cooling.coolant", "Coolant: %s");
        provider.add("category.neoecoae.cooling.max_overclock", "Max Overclock: %s");
        provider.add("category.neoecoae.multiblock", "ECO Multiblock Info");
        provider.add("category.neoecoae.integrated_working_station", "Integrated Working Station");

        provider.add("emi.category.neoecoae.multiblock", "ECO Multiblock Info");
        provider.add("emi.category.neoecoae.integrated_working_station", "Integrated Working Station");
        provider.add("emi.category.neoecoae.cooling", "Cooling");

        provider.add("tooltip.neoecoae.holdshift", "Hold [Shift] for more info");
        provider.add("tooltip.neoecoae.max_lenth", "§7§oMax structure length: %d");

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
        provider.add("tooltip.neoecoae.max_parallel_count", "§7§oMax parallel +%d");
        provider.add("tooltip.neoecoae.overclocked", "§7§oWhen overclocked:");
        provider.add("tooltip.neoecoae.active_cooling", "§7§oWhen active cooling is enabled:");
        provider.add("tooltip.neoecoae.clear_negative_effect", "§7§oClears negative effects caused by overclocking");

        addLangs(
                provider,
                "tooltip.neoecoae.crafting_worker",
                "§7§oECO - FX Worker Core is a primary component of the crafting subsystem",
                "§7§oECO - FX worker core can store 32 crafting jobs and processes one at a time");
        provider.add("tooltip.neoecoae.crafting_jobs_l4", "§7§oStores crafting jobs: x%d [L4]");
        provider.add("tooltip.neoecoae.crafting_jobs_l6", "§7§oStores crafting jobs: x%d [L6]");
        provider.add("tooltip.neoecoae.crafting_jobs_l9", "§7§oStores crafting jobs: x%d [L9]");
        provider.add("tooltip.neoecoae.power_multiply_l4", "§7§oPower multiplier: x%d [L4]");
        provider.add("tooltip.neoecoae.power_multiply_l6", "§7§oPower multiplier: x%d [L6]");
        provider.add("tooltip.neoecoae.power_multiply_l9", "§7§oPower multiplier: x%d [L9]");

        addLangs(
                provider,
                "tooltip.neoecoae.crafting_pattern_bus",
                "§7§oECO - FD Smart Pattern Bus is a core part of the crafting subsystem",
                "§7§oEach page can store 63 patterns; the number of pages is configurable",
                "§7§oWhen encoding patterns on an ME encoding terminal, use the adjacent button to quick upload");

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
        provider.add("tooltip.neoecoae.max_thread_count", "§7§oMax threads +%d");
        addLangs(
                provider,
                "tooltip.neoecoae.computation_parallel_core",
                "§7§oParallel cores provide parallelism for the computation subsystem",
                "§7§oParallelism increases the number of tasks each thread core can process per tick");
        provider.add("tooltip.neoecoae.computation_cell", "§7§oProvides %s bytes to computation subsystem");

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
