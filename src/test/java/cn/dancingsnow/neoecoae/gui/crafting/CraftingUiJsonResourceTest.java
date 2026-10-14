package cn.dancingsnow.neoecoae.gui.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CraftingUiJsonResourceTest {
    @Test
    void controllerResourceDeclaresItsRequiredControlsAndActions() {
        JsonObject root = read("crafting_controller.json");

        assertEquals("crafting_controller_root", root.get("id").getAsString());
        assertEquals("crafting_artwork", node(root, "crafting_artwork").get("custom").getAsString());
        assertEquals("toggle_overclocked", node(root, "overclock_button").get("action").getAsString());
        assertEquals("toggle_active_cooling", node(root, "active_cooling_button").get("action").getAsString());
        assertEquals("player_inventory", node(root, "player_inventory").get("custom").getAsString());
    }

    @Test
    void interfaceAndBusResourcesDeclareTheirRequiredInventories() {
        JsonObject patternInterface = read("crafting_interface.json");
        assertEquals("pattern_preview", node(patternInterface, "pattern_preview").get("custom").getAsString());

        JsonObject patternBus = read("crafting_pattern_bus.json");
        assertEquals("pattern_inventory", node(patternBus, "pattern_inventory").get("custom").getAsString());
        assertEquals("player_inventory", node(patternBus, "player_inventory").get("custom").getAsString());
    }

    private static JsonObject node(JsonObject root, String id) {
        JsonObject found = findNode(root, id);
        assertNotNull(found, "Required crafting UI control is missing: " + id);
        return found;
    }

    private static JsonObject findNode(JsonObject root, String id) {
        if (root.has("id") && id.equals(root.get("id").getAsString())) return root;
        if (root.has("children")) {
            for (var child : root.getAsJsonArray("children")) {
                JsonObject found = findNode(child.getAsJsonObject(), id);
                if (found != null) return found;
            }
        }
        return null;
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
