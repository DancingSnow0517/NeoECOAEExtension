package cn.dancingsnow.neoecoae.data.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComputationCellModelResourceTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/neoecoae");

    @Test
    void computationCellsUseCompleteHandwrittenModels() throws IOException {
        JsonObject base = readModel("eco_computation_cell_base");
        assertTrue(base.getAsJsonArray("elements").size() > 0);
        for (String tier : List.of("l4", "l6", "l9")) {
            String modelName = "eco_computation_cell_" + tier;
            JsonObject model = readModel(modelName);
            assertEquals(
                    "neoecoae:item/eco_computation_cell_base",
                    model.get("parent").getAsString());
            assertFalse(
                    Files.exists(Path.of("src/generated/resources/assets/neoecoae/models/item", modelName + ".json")));
            JsonObject textures = base.getAsJsonObject("textures").deepCopy();
            for (Map.Entry<String, JsonElement> texture :
                    model.getAsJsonObject("textures").entrySet()) {
                textures.add(texture.getKey(), texture.getValue());
            }
            for (Map.Entry<String, JsonElement> texture : textures.entrySet()) {
                String location = texture.getValue().getAsString();
                assertTrue(location.startsWith("neoecoae:"), location);
                assertTrue(
                        Files.isRegularFile(
                                ASSETS.resolve("textures/" + location.substring("neoecoae:".length()) + ".png")),
                        location);
            }
        }
    }

    private static JsonObject readModel(String name) throws IOException {
        try (Reader reader = Files.newBufferedReader(ASSETS.resolve("models/item/" + name + ".json"))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
