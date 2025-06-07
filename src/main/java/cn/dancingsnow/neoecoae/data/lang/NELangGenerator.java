package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class NELangGenerator {
    public static void accept(RegistrateLangProvider provider) {
        provider.add("config.jade.plugin_neoecoae.eco_drive", "ECO Drive");

        provider.add("jade.neoecoae.drive_mounted", "ECO Drive Mounted");
        provider.add("jade.neoecoae.drive_unmounted", "ECO Drive Unmounted");

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
    }
}
