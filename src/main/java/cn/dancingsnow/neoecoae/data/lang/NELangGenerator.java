package cn.dancingsnow.neoecoae.data.lang;

import cn.dancingsnow.neoecoae.core.content.LanguageContent;
import com.tterrag.registrate.providers.RegistrateLangProvider;

/** Minecraft language generation adapter for the shared content table. */
public class NELangGenerator {
    public static void accept(RegistrateLangProvider provider) {
        LanguageContent.accept(provider::add);
    }
}
