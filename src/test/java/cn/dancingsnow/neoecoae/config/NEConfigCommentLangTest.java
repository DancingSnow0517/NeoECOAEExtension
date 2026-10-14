package cn.dancingsnow.neoecoae.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

/** Keeps every NEConfig comment equal to the zh_cn tooltip followed by the en_us tooltip of the same key. */
class NEConfigCommentLangTest {
    private static final String PREFIX = "neoecoae.configuration.";
    private static final String SUFFIX = ".tooltip";
    private static final JsonObject EN = readLang("src/generated/resources/assets/neoecoae/lang/en_us.json");
    private static final JsonObject ZH = readLang("src/main/resources/assets/neoecoae/lang/zh_cn.json");

    @Test
    void commentsMatchLangTooltipsAndCoverEveryTooltipKey() {
        Set<String> checked = new HashSet<>();
        walk(NEConfig.SPEC.getSpec(), List.of(), checked);

        Set<String> expected = EN.keySet().stream()
            .filter(key -> key.startsWith(PREFIX) && key.endsWith(SUFFIX))
            .map(key -> key.substring(PREFIX.length(), key.length() - SUFFIX.length()))
            .collect(Collectors.toSet());
        assertEquals(expected, checked);
    }

    private static void walk(UnmodifiableConfig config, List<String> path, Set<String> checked) {
        config.valueMap().forEach((name, value) -> {
            List<String> childPath = new ArrayList<>(path);
            childPath.add(name);
            if (value instanceof ModConfigSpec.ValueSpec spec) {
                // NeoForge appends " Default: ..." / " Range: ..." lines after our comment
                assertComment(name, spec.getComment().split("\n ", 2)[0]);
                checked.add(name);
            } else if (value instanceof UnmodifiableConfig section) {
                assertComment(name, NEConfig.SPEC.getLevelComment(childPath));
                checked.add(name);
                walk(section, childPath, checked);
            }
        });
    }

    private static void assertComment(String name, String comment) {
        assertEquals(tooltip(ZH, name) + "\n" + tooltip(EN, name), comment, name);
    }

    private static String tooltip(JsonObject lang, String name) {
        String key = PREFIX + name + SUFFIX;
        assertTrue(lang.has(key), "missing " + key);
        return lang.get(key).getAsString();
    }

    private static JsonObject readLang(String file) {
        try {
            return JsonParser.parseString(Files.readString(Path.of(file), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
