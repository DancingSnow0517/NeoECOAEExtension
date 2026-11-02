package cn.dancingsnow.neoecoae.data.lang;

import com.google.gson.stream.JsonReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanguageResourcesTest {
    private static final List<String> LOCALES = List.of("en_us", "en_ud", "zh_cn", "zh_tw", "zh_hk", "lzh");
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?([sd])|%%");
    private static final Pattern KEY = Pattern.compile("\"((?:[\\w]+\\.)*neoecoae\\.[\\w.]+)\"");
    private static final Pattern REGISTERED_KEY = Pattern.compile(
        "addLang\\(\\s*\"([^\"]+)\"\\s*,\\s*NeoECOAE\\.id\\(\\s*\"([^\"]+)\"\\s*\\)");

    @Test
    void supportedLanguagesCoverEnglishAndPreserveFormatArguments() throws Exception {
        Map<String, String> english = read("en_us");
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            Map<String, String> translations = read(locale);
            for (var entry : english.entrySet()) {
                String key = entry.getKey();
                String value = translations.get(key);
                if (value == null) {
                    problems.add(locale + ": missing " + key);
                } else if (!arguments(entry.getValue()).equals(arguments(value))) {
                    problems.add(locale + ": format arguments differ for " + key);
                }
            }
            translations.forEach((key, value) -> {
                if (value.isBlank() || value.contains("UNLOCALIZED:") || value.contains("???")
                    || value.indexOf('\uFFFD') >= 0 || value.equals(key)) {
                    problems.add(locale + ": invalid translation for " + key);
                }
            });
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void literalLanguageReferencesHaveEnglishDefinitions() throws Exception {
        Set<String> defined = new HashSet<>(read("en_us").keySet());
        Map<String, Set<Path>> references = new HashMap<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path path : files.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                // Optional integrations declare their labels even when their mods are not loaded by datagen.
                var registered = REGISTERED_KEY.matcher(source);
                while (registered.find()) defined.add(registered.group(1) + ".neoecoae." + registered.group(2));
                if (path.startsWith(Path.of("src/main/java/cn/dancingsnow/neoecoae/data/lang"))) continue;
                var keys = KEY.matcher(source);
                while (keys.find()) {
                    String key = keys.group(1);
                    if (!key.endsWith(".") && isLanguageKey(key)) {
                        references.computeIfAbsent(key, ignored -> new HashSet<>()).add(path);
                    }
                }
            }
        }
        references.keySet().removeAll(defined);
        // Infinite-resource items receive a group name and append these two suffixes themselves.
        references.keySet().removeIf(key -> defined.contains(key + ".contents") && defined.contains(key + ".sink"));
        assertEquals(Map.of(), references, "Missing English language definitions");
    }

    private static boolean isLanguageKey(String key) {
        return key.startsWith("gui.") || key.startsWith("tooltip.") || key.startsWith("jade.")
            || key.startsWith("cell_type.") || key.startsWith("recipe_type.") || key.startsWith("item.")
            || key.startsWith("block.") || key.startsWith("upgrade.") || key.startsWith("category.")
            || key.startsWith("emi.category.") || key.startsWith("neoecoae.configuration.")
            || key.equals("neoecoae.unknow_cell_type");
    }

    private static Map<Integer, String> arguments(String value) {
        Map<Integer, String> arguments = new HashMap<>();
        var matcher = ARGUMENT.matcher(value);
        int next = 1;
        while (matcher.find()) {
            if (matcher.group().equals("%%")) continue;
            int index = matcher.group(1) == null ? next++ : Integer.parseInt(matcher.group(1));
            arguments.merge(index, matcher.group(2), (first, second) -> first + second);
        }
        return arguments;
    }

    private static Map<String, String> read(String locale) throws Exception {
        var resource = LanguageResourcesTest.class.getClassLoader()
            .getResourceAsStream("assets/neoecoae/lang/" + locale + ".json");
        assertNotNull(resource, "Missing language file: " + locale);
        try (var reader = new JsonReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            Map<String, String> translations = new HashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                assertNull(translations.put(key, reader.nextString()), "Duplicate language key in " + locale + ": " + key);
            }
            reader.endObject();
            return translations;
        }
    }
}
