package cn.dancingsnow.neoecoae.gui.workstation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkstationUiJsonResourceTest {
    @Test
    void workstationRootsDeclareStaticGeometryAndCustomDynamicContent() {
        JsonObject integrated = read("integrated.json");
        assertEquals("custom", integrated.get("type").getAsString());
        assertEquals("content", integrated.get("custom").getAsString());
        for (String name : new String[] {"large", "network_toolbox"}) {
            JsonObject root = read(name + ".json");
            assertEquals("element", root.get("type").getAsString());
            assertEquals("content", root.getAsJsonArray("children").get(0)
                .getAsJsonObject().get("custom").getAsString());
        }
        JsonObject large = read("large.json");
        assertEquals(36, large.getAsJsonArray("children").get(1)
            .getAsJsonObject().getAsJsonArray("slots").size());
    }

    private JsonObject read(String file) {
        var resource = getClass().getClassLoader().getResourceAsStream(
            "assets/neoecoae/screens/workstation/" + file);
        assertNotNull(resource, "workstation UI definition is missing: " + file);
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("Unable to parse workstation UI definition: " + file, e);
        }
    }
}
