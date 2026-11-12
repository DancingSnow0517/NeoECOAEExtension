package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NELangGeneratorTest {
    @Test
    void bulkStorageInterfaceNamesSurviveRegeneration() {
        var translations = generate();
        for (String suffix : new String[]{
            "action.priority", "action.compression_display", "compression_display.reserved", "priority.title",
            "migrate.result.busy", "migrate.result.no_bulk_cell", "migrate.result.success", "migrate.result.unavailable"
        }) {
            assertTrue(translations.containsKey("gui.neoecoae.storage.bulk_cell." + suffix), suffix);
        }
        assertEquals("Bytes used: %s / %s", translations.get("tooltip.neoecoae.bulk.bytes"));
    }

    @Test
    void optionalStorageNamesDoNotRequireTheirIntegrationsToBeLoaded() {
        var translations = generate();
        assertEquals("MEGA Mana", translations.get("cell_type.neoecoae.mega_mana"));
        for (String family : new String[]{"mana", "matter"}) {
            assertTrue(translations.containsKey("item.neoecoae.mega_" + family + "_cell_housing"), family);
            assertTrue(translations.containsKey("item.neoecoae.eco_mega_" + family + "_cell_4g"), family);
        }
        for (String family : new String[]{"mana", "data"}) {
            assertTrue(translations.containsKey("item.neoecoae.eco_" + family + "_cell_housing"), family);
            for (String size : new String[]{"16m", "64m", "256m"}) {
                assertTrue(translations.containsKey("item.neoecoae.eco_" + family + "_storage_cell_" + size), family + size);
            }
        }
    }

    private Map<String, String> generate() {
        var translations = new HashMap<String, String>();
        var provider = mock(RegistrateLangProvider.class);
        doAnswer(call -> {
            String key = call.getArgument(0);
            String value = call.getArgument(1);
            assertNull(translations.putIfAbsent(key, value), "Duplicate translation: " + key);
            return null;
        }).when(provider).add(anyString(), anyString());
        NELangGenerator.accept(provider);
        return translations;
    }
}
