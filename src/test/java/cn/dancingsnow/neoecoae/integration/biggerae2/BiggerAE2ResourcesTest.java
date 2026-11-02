package cn.dancingsnow.neoecoae.integration.biggerae2;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BiggerAE2ResourcesTest {
    @Test
    void allTwelveCellsHaveConditionalAssemblyDisassemblyAndMatchingArtwork() throws Exception {
        for (var channel : BiggerAE2Channel.values()) {
            for (boolean singularity : new boolean[]{false, true}) {
                String id = channel.cellId(singularity);
                var recipe = json("data/neoecoae/recipe/" + id + ".json");
                assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
                assertEquals("neoecoae:" + id, recipe.getAsJsonObject("result").get("id").getAsString());
                var ingredients = recipe.getAsJsonArray("ingredients");
                assertEquals(2, ingredients.size());
                assertEquals(channel.housingId(), ingredients.get(0).getAsJsonObject().get("item").getAsString());
                assertEquals(BiggerAE2Channel.componentId(singularity),
                    ingredients.get(1).getAsJsonObject().get("item").getAsString());
                Set<String> requiredMods = new HashSet<>();
                recipe.getAsJsonArray("neoforge:conditions").forEach(condition ->
                    requiredMods.add(condition.getAsJsonObject().get("modid").getAsString()));
                assertEquals(channel.requiredMod == null ? Set.of("bigger_ae2")
                    : Set.of("bigger_ae2", channel.requiredMod), requiredMods);

                var disassembly = json("data/neoecoae/recipe/disassembly/" + id + ".json");
                assertEquals("neoecoae:" + id, disassembly.get("cell").getAsString());
                assertEquals(recipe.get("neoforge:conditions"), disassembly.get("neoforge:conditions"));
                var returned = disassembly.getAsJsonArray("cell_disassembly_items");
                assertEquals(channel.housingId(), returned.get(0).getAsJsonObject().get("id").getAsString());
                assertEquals(BiggerAE2Channel.componentId(singularity), returned.get(1).getAsJsonObject().get("id").getAsString());

                var model = json("assets/neoecoae/models/item/" + id + ".json");
                var textures = model.getAsJsonObject("textures");
                assertEquals(singularity ? 4 : 3, textures.size());
                assertEquals("neoecoae:item/eco_cells/cell_light_" + (singularity ? "l9" : "l6"),
                    textures.get("layer1").getAsString());
                assertEquals("neoecoae:item/eco_cells/cell_status_light", textures.get("layer2").getAsString());
                for (var texture : textures.entrySet()) {
                    String path = texture.getValue().getAsString().replace("neoecoae:", "assets/neoecoae/textures/") + ".png";
                    assertNotNull(getClass().getClassLoader().getResource(path), path);
                }
                String family = singularity ? "digital_singularity" : "quantum";
                assertNotNull(getClass().getClassLoader().getResource("assets/neoecoae/models/block/cell/storage_cell_"
                    + family + "_" + (singularity ? "l9" : "l6") + "_" + channel.id + ".json"));
                assertTrue(json("assets/neoecoae/lang/en_us.json").has("item.neoecoae." + id));
                assertTrue(json("assets/neoecoae/lang/zh_cn.json").has("item.neoecoae." + id));
            }
        }
    }

    @Test
    void bothUpstreamComponentsExistInVerifiedRelease() throws Exception {
        for (String component : new String[]{"quantum_cell_component", "digital_singularity_cell_component"}) {
            json("assets/bigger_ae2/models/item/" + component + ".json");
        }
    }

    private JsonObject json(String path) throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
