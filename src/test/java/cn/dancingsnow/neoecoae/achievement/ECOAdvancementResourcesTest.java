package cn.dancingsnow.neoecoae.achievement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ECOAdvancementResourcesTest {
    private static final String DIRECTORY = "data/neoecoae/advancements/eco/";
    private static final String ROOT = "neoecoae:eco/root";
    private static final Set<String> NAMES = Set.of(
        "root", "energized_crystal_lightning", "host_l4", "host_l6", "host_l9",
        "infinite_component", "integrated_working_station", "large_integrated_formed");

    @Test
    void milestonesBelongToOneConnectedAcyclicTab() throws Exception {
        Map<String, JsonObject> advancements = new HashMap<>();
        Set<String> roots = new HashSet<>();
        for (String name : NAMES) {
            String id = "neoecoae:eco/" + name;
            JsonObject advancement = read(name);
            advancements.put(id, advancement);
            if (!advancement.has("parent")) roots.add(id);
        }
        assertEquals(Set.of(ROOT), roots, "Every extra root creates a separate advancement tab");
        for (String start : advancements.keySet()) {
            Set<String> visited = new HashSet<>();
            String current = start;
            while (!current.equals(ROOT)) {
                assertTrue(visited.add(current), "Cyclic advancement ancestry: " + start);
                JsonObject advancement = advancements.get(current);
                assertNotNull(advancement, "Missing advancement parent: " + current);
                current = advancement.get("parent").getAsString();
            }
        }
    }

    @Test
    void usesCurrentResourceAndIconFormats() throws Exception {
        for (String name : NAMES) {
            assertNull(getClass().getClassLoader().getResource(
                "data/neoecoae/advancement/eco/" + name + ".json"), "Wrong version directory: " + name);
            JsonObject icon = read(name).getAsJsonObject("display").getAsJsonObject("icon");
            assertTrue(icon.has("item"), "Missing Forge item-stack field: " + name);
            assertFalse(icon.has("id"), "Newer item-stack field: " + name);
        }
    }

    @Test
    void rootUnlocksSilentlyAndUsesAnExistingBackground() throws Exception {
        JsonObject root = read("root");
        assertEquals("minecraft:tick", root.getAsJsonObject("criteria")
            .getAsJsonObject("joined").get("trigger").getAsString());
        JsonObject display = root.getAsJsonObject("display");
        assertFalse(display.get("show_toast").getAsBoolean());
        assertFalse(display.get("announce_to_chat").getAsBoolean());
        String[] background = display.get("background").getAsString().split(":", 2);
        assertEquals(2, background.length);
        String resource = "assets/" + background[0] + "/" + background[1];
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing advancement background: " + resource);
            assertNotNull(ImageIO.read(stream), "Invalid advancement background: " + resource);
        }
    }

    private JsonObject read(String name) throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(DIRECTORY + name + ".json");
        assertNotNull(stream, "Missing advancement resource: " + name);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
