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
    }
}
