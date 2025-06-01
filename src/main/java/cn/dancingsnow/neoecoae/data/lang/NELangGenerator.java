package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class NELangGenerator {
    public static void accept(RegistrateLangProvider provider) {
        provider.add("config.jade.plugin_neoecoae.eco_drive", "ECO Drive");

        provider.add("jade.neoecoae.drive_mounted", "ECO Drive Mounted");
        provider.add("jade.neoecoae.drive_unmounted", "ECO Drive Unmounted");

        provider.add("gui.neoecoae.crafting.pattern_bus_count", "Pattern Bus Count: %d");
        provider.add("gui.neoecoae.crafting.parallel_core_count", "Parallel Core Count: %d");
        provider.add("gui.neoecoae.crafting.worker_count", "Worker Core Count: %d");
        provider.add("neoecoae.tooltip.upload_pattern", "Upload Pattern into available ECO Crafting System");
    }
}
