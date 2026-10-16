package cn.dancingsnow.neoecoae.data.lang;

import cn.dancingsnow.neoecoae.core.content.ConfigLanguageContent;
import com.tterrag.registrate.providers.RegistrateLangProvider;

/** Minecraft language generation adapter for the shared content table. */
public class ConfigLangs {
    public static void accept(RegistrateLangProvider provider) {
        ConfigLanguageContent.accept(provider::add);
    }
}
