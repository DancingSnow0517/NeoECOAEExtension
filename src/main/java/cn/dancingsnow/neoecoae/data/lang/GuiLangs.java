package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class GuiLangs {
    public static void accept(RegistrateLangProvider provider) {
        // storage
        provider.add("gui.neoecoae.storage.energy", "Energy Monitoring");
        provider.add("gui.neoecoae.storage.energy_status", "Energy Storage: %sAE / %sAE (%d%%)");

        // computation
        provider.add("gui.neoecoae.computation.thread_info", "Thread Used: %d / %d");
        provider.add("gui.neoecoae.computation.parallel_info", "Parallel Count: %d");
        provider.add("gui.neoecoae.computation.storage_info", "Storage Used: %s / %s");

        // crafting
        provider.add("gui.neoecoae.crafting.pattern_bus_count", "Pattern Buses: %d");
        provider.add("gui.neoecoae.crafting.parallel_core_count", "Parallel Cores: %d");
        provider.add("gui.neoecoae.crafting.worker_count", "Worker Cores: %d");
        provider.add("gui.neoecoae.crafting.working_threads", "Working Threads: %d / %d (%d%%)");
        provider.add("gui.neoecoae.crafting.total_parallelism", "Total Parallelism: %d");
        provider.add("gui.neoecoae.crafting.total_parallelism.overflow", "Overflow: %d (%d%%)");
        provider.add("gui.neoecoae.crafting.max_energy_usage", "Max Energy Usage: §b%s AE");
        provider.add("gui.neoecoae.crafting.enable_overlock", "Enable Overlock: ");
        provider.add("gui.neoecoae.crafting.overclocked.tooltip", "Boosting performance within a limited range while consuming more §cEnergy§f.");
        provider.add("gui.neoecoae.crafting.enable_active_cooling", "Enable Active Cooling: ");
        provider.add("gui.neoecoae.crafting.active_cooling.tooltip", "Consumes coolant from the fluid input port to enhance performance and eliminate the additional energy cost of overclocking.\nUsable coolants can be looked up in JEI.\nIf the machine's coolant level is insufficient during operation, it will stop running.\nIf the fluid output port is full, coolant cannot be consumed from the fluid input port, preventing the machine from replenishing its coolant supply.");
    }
}
