package cn.dancingsnow.neoecoae.data.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GTCEuRecipesGeneratedJsonTest {
    private static final Path RECIPE_ROOT = Path.of("src/generated/resources/data/neoecoae/recipes");

    @Test
    void tieredAssemblerRecipesUseRequestedGtStages() throws IOException {
        assertGtStage("assembler/storage_system_l4", "gtceu:assembler", 512);
        assertGtStage("assembler/storage_system_l6", "gtceu:assembler", 8_192);
        assertGtStage("assembler/storage_system_l9", "gtceu:assembler", 32_768);
    }

    @Test
    void infiniteComponentUsesUhvAssemblyLine() throws IOException {
        JsonObject recipe = gtRecipe("assembly_line/eco_infinite_cell_component");

        assertEquals("gtceu:assembly_line", recipe.get("type").getAsString());
        assertEquals(2_097_152, eu(recipe));
        assertTrue(hasItem(recipe, "gtceu:uhv_electric_motor"));
        assertTrue(hasItem(recipe, "gtceu:uhv_electric_piston"));
        assertTrue(hasItem(recipe, "gtceu:uhv_electric_pump"));
        assertTrue(hasItem(recipe, "gtceu:uhv_conveyor_module"));
        assertTrue(hasItem(recipe, "gtceu:uhv_robot_arm"));
        assertTrue(hasItem(recipe, "gtceu:uhv_emitter"));
        assertTrue(hasItem(recipe, "gtceu:uhv_sensor"));
        assertTrue(hasItem(recipe, "gtceu:uhv_field_generator"));
        assertTrue(hasResearch(recipe, "neoecoae:eco_cell_component_256m", "gtceu:data_module"));
        assertTrue(hasItemOutput(recipe, "neoecoae:eco_infinite_cell_component"));
    }

    @Test
    void gtceuRecipesAreHiddenBehindForgeConditionalRecipes() throws IOException {
        for (String path : new String[] {
            "assembler/storage_system_l4",
            "assembler/eco_cell_component_256m",
            "assembly_line/eco_infinite_cell_component",
            "chemical_reactor/energized_crystal",
            "mixer/cryotheum_solution"
        }) {
            assertGtRecipeConditions(path);
        }
    }

    @Test
    void iwsRecipesStayAvailableWithoutConditionalWrapper() throws IOException {
        for (String path : new String[] {
            "eco_cell_component_16m",
            "integrated_working_station/energized_crystal",
            "transform/crystal_ingot",
            "cryotheum",
            "inscriber/aluminum_alloy_dust"
        }) {
            assertTrue(!recipe(path).has("conditions"));
        }
    }

    @Test
    void ordinaryGtAssemblerRecipesDoNotRequireGtCircuits() throws IOException {
        for (String path : new String[] {
            "assembler/eco_cell_component_16m",
            "assembler/eco_cell_component_64m",
            "assembler/eco_cell_component_256m",
            "assembler/eco_computation_cell_l4",
            "assembler/eco_computation_cell_l6",
            "assembler/eco_computation_cell_l9",
            "assembler/storage_system_l4",
            "assembler/storage_system_l6",
            "assembler/storage_system_l9",
            "assembler/computation_system_l4",
            "assembler/computation_system_l6",
            "assembler/computation_system_l9",
            "assembler/crafting_system_l4",
            "assembler/crafting_system_l6",
            "assembler/crafting_system_l9"
        }) {
            assertTrue(!hasItemTagPrefix(gtRecipe(path), "gtceu:circuits/"));
        }
    }

    private static void assertGtStage(String path, String type, int eu) throws IOException {
        JsonObject recipe = gtRecipe(path);

        assertEquals(type, recipe.get("type").getAsString());
        assertEquals(eu, eu(recipe));
    }

    private static JsonObject gtRecipe(String path) throws IOException {
        JsonObject wrapper = recipe(path);
        assertEquals("forge:conditional", wrapper.get("type").getAsString());
        assertGtRecipeConditions(wrapper);

        return wrapper.getAsJsonArray("recipes").get(0).getAsJsonObject().getAsJsonObject("recipe");
    }

    private static JsonObject recipe(String path) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPE_ROOT.resolve(path + ".json")))
                .getAsJsonObject();
    }

    private static void assertGtRecipeConditions(String path) throws IOException {
        assertGtRecipeConditions(recipe(path));
    }

    private static void assertGtRecipeConditions(JsonObject wrapper) {
        JsonArray recipes = wrapper.getAsJsonArray("recipes");
        JsonArray conditions = recipes.get(0).getAsJsonObject().getAsJsonArray("conditions");

        assertTrue(hasCondition(conditions, "forge:mod_loaded", "modid", "gtceu"));
        assertEquals(1, conditions.size());
    }

    private static boolean hasCondition(JsonArray conditions, String type, String key, String value) {
        for (var element : conditions) {
            JsonObject condition = element.getAsJsonObject();
            if (type.equals(condition.get("type").getAsString())
                    && value.equals(condition.get(key).getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static int eu(JsonObject recipe) {
        return recipe.getAsJsonObject("tickInputs")
                .getAsJsonArray("eu")
                .get(0)
                .getAsJsonObject()
                .get("content")
                .getAsInt();
    }

    private static boolean hasItemTagPrefix(JsonObject recipe, String prefix) {
        for (var element : recipe.getAsJsonObject("inputs").getAsJsonArray("item")) {
            JsonObject ingredient =
                    element.getAsJsonObject().getAsJsonObject("content").getAsJsonObject("ingredient");
            if (ingredient.has("tag") && ingredient.get("tag").getAsString().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasItem(JsonObject recipe, String item) {
        for (var element : recipe.getAsJsonObject("inputs").getAsJsonArray("item")) {
            JsonObject ingredient =
                    element.getAsJsonObject().getAsJsonObject("content").getAsJsonObject("ingredient");
            if (ingredient.has("item") && item.equals(ingredient.get("item").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasItemOutput(JsonObject recipe, String item) {
        for (var element : recipe.getAsJsonObject("outputs").getAsJsonArray("item")) {
            JsonObject ingredient =
                    element.getAsJsonObject().getAsJsonObject("content").getAsJsonObject("ingredient");
            if (ingredient.has("item") && item.equals(ingredient.get("item").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasResearch(JsonObject recipe, String researchId, String dataItem) {
        for (var conditionElement : recipe.getAsJsonArray("recipeConditions")) {
            JsonObject condition = conditionElement.getAsJsonObject();
            if (!"gtceu:research".equals(condition.get("type").getAsString())) {
                continue;
            }

            for (var researchElement : condition.getAsJsonArray("research")) {
                JsonObject research = researchElement.getAsJsonObject();
                JsonObject dataStack = research.getAsJsonObject("dataItem");
                if (researchId.equals(research.get("researchId").getAsString())
                        && dataItem.equals(dataStack.get("id").getAsString())
                        && dataStack.get("Count").getAsInt() == 1) {
                    return true;
                }
            }
        }
        return false;
    }
}
