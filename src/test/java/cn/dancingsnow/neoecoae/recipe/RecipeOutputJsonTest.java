package cn.dancingsnow.neoecoae.recipe;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RecipeOutputJsonTest {
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath("neoecoae", "test_recipe");

    @Test
    void itemOutputRequiresItemOrId() {
        JsonObject output = new JsonObject();
        output.addProperty("count", 2);

        assertThrows(JsonParseException.class, () -> RecipeOutputJson.readItemStack(RECIPE_ID, "itemOutput", output));
    }

    @Test
    void fluidOutputRejectsTags() {
        JsonObject output = new JsonObject();
        output.addProperty("tag", "minecraft:water");
        output.addProperty("amount", 1000);

        assertThrows(JsonParseException.class, () -> RecipeOutputJson.readFluidStack(RECIPE_ID, "fluidOutput", output));
    }
}
