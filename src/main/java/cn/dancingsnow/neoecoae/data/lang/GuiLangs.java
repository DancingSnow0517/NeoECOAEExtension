package cn.dancingsnow.neoecoae.data.lang;

import cn.dancingsnow.neoecoae.core.content.GuiLanguageContent;
import com.tterrag.registrate.providers.RegistrateLangProvider;

/** Minecraft language generation adapter for the shared content table. */
public class GuiLangs {
    public static void accept(RegistrateLangProvider provider) {
        GuiLanguageContent.accept(provider::add);
    }
}
