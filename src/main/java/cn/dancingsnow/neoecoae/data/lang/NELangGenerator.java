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

        provider.add("category.neoecoae.cooling", "Cooling");
        provider.add("category.neoecoae.cooling.coolant", "Coolant: %d");

        provider.add("tooltip.neoecoae.holdshift", "Hold [Shift] to show more info");
        provider.add("tooltip.neoecoae.max_lenth", "Maximum length of structure: %d");

        provider.add("tooltip.neoecoae.storage_system", "The core of the storage system");
        addLangs(provider, "tooltip.neoecoae.storage_dirve",
            "Can drive storage matrix",
            "The storage matrix that can be driven is determined by the Storage System"
        );

        provider.add("tooltip.neoecoae.crafting_system", "The core of the crafting system");
        provider.add("tooltip.neoecoae.crafting_parallels", "Parallel core provides parallel count for crafting systems");
        provider.add("tooltip.neoecoae.max_parallel_count", "Max parallel count +%d");
        provider.add("tooltip.neoecoae.overclocked", "When enabling overclocking:");
        provider.add("tooltip.neoecoae.active_cooling", "When enabling active cooling:");
        provider.add("tooltip.neoecoae.clear_negative_effect", "Clear the negative effects of overclocking");

        addLangs(provider, "tooltip.neoecoae.crafting_worker",
            "ECO - FX Worker is the main part of the crafting system",
            "ECO - FX Worker can store 32 crafting jobs, processing 1 crafting job per crafting"
        );
        provider.add("tooltip.neoecoae.crafting_jobs_l4", "Store Crafting Jobs: x%d [L4]");
        provider.add("tooltip.neoecoae.crafting_jobs_l6", "Store Crafting Jobs: x%d [L6]");
        provider.add("tooltip.neoecoae.crafting_jobs_l9", "Store Crafting Jobs: x%d [L9]");
        provider.add("tooltip.neoecoae.power_multiply_l4", "Power Multiply: x%d [L4]");
        provider.add("tooltip.neoecoae.power_multiply_l6", "Power Multiply: x%d [L6]");
        provider.add("tooltip.neoecoae.power_multiply_l9", "Power Multiply: x%d [L9]");

        addLangs(provider, "tooltip.neoecoae.crafting_pattern_bus",
            "ECO - FD Smart Pattern Bus is the main part of the crafting system",
            "Each bus can store 63 patten",
            "When encoding patten on the ME Encoding Terminal, you can use the adjacent button to quickly upload them"
        );
    }

    private static void addLangs(RegistrateLangProvider provider, String key, String... langs) {
        for (int i = 0; i < langs.length; i++) {
            provider.add(key + "." + i, langs[i]);
        }
    }
}
