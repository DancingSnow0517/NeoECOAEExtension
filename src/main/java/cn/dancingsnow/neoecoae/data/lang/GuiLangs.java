package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class GuiLangs {
    public static void accept(RegistrateLangProvider provider) {
        // storage
        provider.add("gui.neoecoae.storage.energy", "Energy Monitoring");
        provider.add("gui.neoecoae.storage.energy_status", "Energy Storage: %sAE / %sAE (%d%%)");
    }
}
