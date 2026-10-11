package cn.dancingsnow.neoecoae.gui.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingUiJsonResourceTest {
    @Test
    void controllerLayoutKeepsStaticGeometryInTheResource() {
        JsonObject root = read("crafting_controller.json");

        assertEquals("element", root.get("type").getAsString());
        assertEquals(253, root.getAsJsonObject("layout").get("width").getAsInt());
        assertEquals(214, root.getAsJsonObject("layout").get("height").getAsInt());
        JsonArray children = root.getAsJsonArray("children");
        assertEquals(4, children.size());
        assertEquals("crafting_artwork", children.get(0).getAsJsonObject().get("custom").getAsString());
        assertEquals("toggle_overclocked", children.get(1).getAsJsonObject().get("action").getAsString());
        assertEquals("toggle_active_cooling", children.get(2).getAsJsonObject().get("action").getAsString());
        assertEquals(37, children.get(1).getAsJsonObject().getAsJsonObject("layout").get("left").getAsInt());
        assertEquals(10, children.get(2).getAsJsonObject().getAsJsonObject("layout").get("left").getAsInt());
        JsonObject inventory = children.get(3).getAsJsonObject();
        assertEquals("player_inventory", inventory.get("custom").getAsString());
        assertTrue(inventory.getAsJsonObject("layout").get("left").getAsInt() == 7);
    }

    @Test
    void craftingScreensDeclareResourceBackedRoots() {
        JsonObject patternInterface = read("crafting_interface.json");
        assertEquals(195, patternInterface.getAsJsonObject("layout").get("width").getAsInt());
        assertEquals("pattern_preview", patternInterface.getAsJsonArray("children").get(5)
            .getAsJsonObject().get("custom").getAsString());

        JsonObject patternBus = read("crafting_pattern_bus.json");
        assertEquals("panel_bg", patternBus.get("class").getAsString());
        assertEquals("pattern_inventory", patternBus.getAsJsonArray("children").get(1)
            .getAsJsonObject().get("custom").getAsString());
    }

    private JsonObject read(String file) {
        String path = "assets/neoecoae/screens/crafting/" + file;
        var resource = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(resource, "crafting UI definition is missing: " + file);
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("Unable to parse crafting UI definition: " + file, e);
        }
    }
}
