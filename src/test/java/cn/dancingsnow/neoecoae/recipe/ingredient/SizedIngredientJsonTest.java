package cn.dancingsnow.neoecoae.recipe.ingredient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

class SizedIngredientJsonTest {
    @Test
    void preservesVanillaAlternativeIngredientArraysDuringNormalization() {
        JsonArray alternatives = new JsonArray();
        JsonObject legacyItem = new JsonObject();
        legacyItem.addProperty("id", "minecraft:iron_ingot");
        alternatives.add(legacyItem);
        alternatives.add(tag("forge:ingots/aluminum"));

        var normalized = SizedIngredient.normalizeIngredientJson(alternatives);

        assertTrue(normalized.isJsonArray());
        assertTrue(normalized.getAsJsonArray().get(0).getAsJsonObject().has("item"));
    }

    @Test
    void rejectsNonPositiveItemAndFluidAmounts() {
        JsonObject item = tag("forge:ingots/iron");
        item.addProperty("count", 0);
        assertThrows(JsonParseException.class, () -> SizedIngredient.fromJson(item));

        JsonObject fluid = tag("minecraft:water");
        fluid.addProperty("amount", 0);
        assertThrows(JsonParseException.class, () -> SizedFluidIngredient.fromJson(fluid));
    }

    private static JsonObject tag(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("tag", id);
        return json;
    }
}
