package cn.dancingsnow.neoecoae.data.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.google.gson.stream.JsonReader;
import com.tterrag.registrate.providers.RegistrateLangProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LanguageAlignmentTest {
    private static final List<String> LOCALES = List.of("en_us", "en_ud", "zh_cn", "zh_tw", "zh_hk", "lzh");
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])");

    @Test
    void allLocalesHaveTheSameKeysAndCompatibleFormats() throws IOException {
        Map<String, String> english = readLanguage("en_us");
        for (String locale : LOCALES) {
            Map<String, String> localized = readLanguage(locale);
            assertEquals(english.keySet(), localized.keySet(), locale);
            for (Map.Entry<String, String> entry : english.entrySet()) {
                String translated = localized.get(entry.getKey());
                assertFalse(translated.isBlank(), locale + ": " + entry.getKey());
                assertFalse(translated.startsWith("UNLOCALIZED:"), locale + ": " + entry.getKey());
                assertEquals(
                        formatArguments(entry.getValue()), formatArguments(translated), locale + ": " + entry.getKey());
            }
        }
    }

    @Test
    void generatedEnglishMatchesLanguageDefinitions() throws IOException {
        Map<String, String> definitions = new TreeMap<>();
        RegistrateLangProvider provider = mock(RegistrateLangProvider.class);
        doAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    String value = invocation.getArgument(1);
                    assertFalse(definitions.containsKey(key), "Duplicate definition: " + key);
                    definitions.put(key, value);
                    return null;
                })
                .when(provider)
                .add(anyString(), anyString());
        NELangGenerator.accept(provider);
        Map<String, String> english = readLanguage("en_us");
        for (Map.Entry<String, String> entry : definitions.entrySet()) {
            assertEquals(entry.getValue(), english.get(entry.getKey()), entry.getKey());
        }
    }

    private static Map<String, String> readLanguage(String locale) throws IOException {
        String source = locale.startsWith("en_") ? "generated" : "main";
        Path file = Path.of("src", source, "resources", "assets", "neoecoae", "lang", locale + ".json");
        Map<String, String> result = new TreeMap<>();
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(file))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                assertFalse(result.containsKey(key), file + ": duplicate key " + key);
                result.put(key, reader.nextString());
            }
            reader.endObject();
        }
        assertTrue(result.size() > 0, file.toString());
        return result;
    }

    private static List<String> formatArguments(String value) {
        List<String> arguments = new ArrayList<>();
        Matcher matcher = FORMAT.matcher(value);
        while (matcher.find()) {
            if (!matcher.group(2).equals("%")) {
                arguments.add((matcher.group(1) == null ? "" : matcher.group(1) + "$") + matcher.group(2));
            }
        }
        arguments.sort(String::compareTo);
        return arguments;
    }
}
