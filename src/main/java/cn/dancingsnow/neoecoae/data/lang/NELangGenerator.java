package cn.dancingsnow.neoecoae.data.lang;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.ponder.NEPonderPlugin;
import com.tterrag.registrate.providers.RegistrateLangProvider;
import net.createmod.ponder.foundation.PonderIndex;

public class NELangGenerator {
    public static void accept(RegistrateLangProvider provider) {
        PonderIndex.addPlugin(new NEPonderPlugin());
        PonderIndex.getLangAccess().provideLang(NeoECOAE.MOD_ID, provider::add);

        provider.add("config.jade.plugin_neoecoae.eco_drive", "ECO Drive");
        provider.add("config.jade.plugin_neoecoae.eco_crafting_worker", "ECO Crafting Worker");
        provider.add("config.jade.plugin_neoecoae.eco_crafting_system", "ECO Crafting System");

        provider.add("jade.neoecoae.drive_mounted", "ECO Drive Mounted");
        provider.add("jade.neoecoae.drive_unmounted", "ECO Drive Unmounted");
        provider.add("jade.neoecoae.worker_threads", "Threads: %d/%d");
        provider.add("jade.neoecoae.overclocked", "Overclock Enabled");
        provider.add("jade.neoecoae.activeCooling", "Active Cooling Enabled");
        provider.add("jade.neoecoae.coolant", "Coolant: %d");

        provider.add("gui.neoecoae.crafting.pattern_bus_count", "Pattern Buses: %d");
        provider.add("gui.neoecoae.crafting.parallel_core_count", "Parallel Cores: %d");
        provider.add("gui.neoecoae.crafting.worker_count", "Worker Cores: %d");

        provider.add("gui.neoecoae.crafting.crafting_progress", "Total Queue Usage: %d%%");
        provider.add("gui.neoecoae.crafting.crafting_progress.1", "%d / %d");

        provider.add("gui.neoecoae.crafting.total_parallelism", "Total Parallelism: %d");
        provider.add("gui.neoecoae.crafting.total_parallelism.limit", "Limited to: %d");
        provider.add("gui.neoecoae.crafting.total_parallelism.overflow", "Overflow: %d (%d%%)");

        provider.add("gui.neoecoae.crafting.max_energy_usage", "Max Energy Usage: §b%s AE");
        provider.add("gui.neoecoae.crafting.max_energy_usage.tip", "Displayed value is the energy consumed per operation when all working cores are at full load.");

        provider.add("gui.neoecoae.crafting.overclocked", "Overclock: %s");
        provider.add("gui.neoecoae.crafting.overclocked.off", "§cDisabled");
        provider.add("gui.neoecoae.crafting.overclocked.on.1", "§eI");
        provider.add("gui.neoecoae.crafting.overclocked.on.2", "§6II");
        provider.add("gui.neoecoae.crafting.overclocked.on.3", "§cIII");
        provider.add("gui.neoecoae.crafting.overclocked.enable.tip", "Click to enable §eOverclock I§f mode, boosting performance within a limited range while consuming more §cEnergy§f.");
        provider.add("gui.neoecoae.crafting.overclocked.disable.tip", "Click to disable §eOverclock I§f mode.");

        provider.add("gui.neoecoae.crafting.active_cooling", "Active Cooling: %s");
        provider.add("gui.neoecoae.crafting.active_cooling.on", "§aEnabled");
        provider.add("gui.neoecoae.crafting.active_cooling.off", "§cDisabled");
        provider.add("gui.neoecoae.crafting.active_cooling.enable.tip.0", "Click to enable the §9ECO - FZ Active Cooling Subsystem§f, consuming §9Coolant§f from the fluid input tank to §aboot performance§f and reduce §aenergy consumption§f.");
        provider.add("gui.neoecoae.crafting.active_cooling.enable.tip.1", "View available coolants in the Recipe Viewer.");
        provider.add("gui.neoecoae.crafting.active_cooling.enable.tip.2", "Enabling active cooling will put the subsystem into §6Overclock II§f mode.");
        provider.add("gui.neoecoae.crafting.active_cooling.enable.tip.3", "If §eOverclock I§f mode is already enabled, enabling §9Active Cooling§f will put the subsystem into §cOverclock III§f mode.");
        provider.add("gui.neoecoae.crafting.active_cooling.enable.tip.4", "§cOverclock III§f mode will §2significantly boost§f overall performance and reduce §aEnergy§f consumption while using more §9Coolant§f.");
        provider.add("gui.neoecoae.crafting.active_cooling.enable.tip.5", "§fCrafting will §cstop§f if §9Coolant§f runs low while active cooling is enabled.");
        provider.add("gui.neoecoae.crafting.active_cooling.enable.tip.6", "§fCoolant cannot be consumed if the fluid output tank is full.");
        provider.add("gui.neoecoae.crafting.active_cooling.disable.tip", "Click to disable the §9ECO - FZ Active Cooling Subsystem§f.");

        provider.add("neoecoae.tooltip.upload_pattern", "Upload Pattern into available ECO Crafting System");

        provider.add("category.neoecoae.cooling", "Cooling");
        provider.add("category.neoecoae.cooling.coolant", "Coolant: %d");
        provider.add("category.neoecoae.multiblock", "ECO Multiblock Info");

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
    }

    private static void addLangs(RegistrateLangProvider provider, String key, String... langs) {
        for (int i = 0; i < langs.length; i++) {
            provider.add(key + "." + i, langs[i]);
        }
    }
}
