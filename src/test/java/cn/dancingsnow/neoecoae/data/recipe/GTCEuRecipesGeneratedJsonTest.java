package cn.dancingsnow.neoecoae.data.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GTCEuRecipesGeneratedJsonTest {
    private static final Path RECIPE_ROOT = Path.of("src/generated/resources/data/neoecoae/recipes");
    private static final Path TAG_ROOT = Path.of("src/generated/resources/data/neoecoae/tags/items");
    private static final int HV = 512;
    private static final int IV = 8_192;
    private static final int LUV = 32_768;
    private static final int UV = 524_288;

    @Test
    void tieredAssemblerRecipesUseRequestedGtStages() throws IOException {
        for (String path : new String[] {
            "assembler/eco_cell_component_16m",
            "assembler/eco_computation_cell_l4",
            "assembler/storage_system_l4",
            "assembler/computation_system_l4",
            "assembler/crafting_system_l4"
        }) {
            assertGtStage(path, "gtceu:assembler", HV);
        }

        for (String path : new String[] {
            "assembler/eco_cell_component_64m",
            "assembler/eco_computation_cell_l6",
            "assembler/storage_system_l6",
            "assembler/computation_system_l6",
            "assembler/crafting_system_l6"
        }) {
            assertGtStage(path, "gtceu:assembler", IV);
        }

        for (String path : new String[] {
            "assembler/eco_cell_component_256m",
            "assembler/eco_computation_cell_l9",
            "assembler/storage_system_l9",
            "assembler/computation_system_l9",
            "assembler/crafting_system_l9"
        }) {
            assertGtStage(path, "gtceu:assembler", LUV);
        }
    }

    @Test
    void baseMaterialGtRecipesUseHv() throws IOException {
        for (String path : new String[] {
            "chemical_reactor/energized_crystal",
            "chemical_reactor/energized_fluix_crystal",
            "mixer/aluminum_alloy_dust",
            "mixer/black_tungsten_alloy_dust",
            "mixer/crystal_ingot",
            "mixer/energized_superconductive_ingot",
            "mixer/cryotheum",
            "mixer/cryotheum_solution",
            "macerator/iron_dust",
            "macerator/aluminum_dust",
            "macerator/tungsten_dust",
            "macerator/aluminum_alloy_dust",
            "macerator/black_tungsten_alloy_dust",
            "macerator/energized_crystal_dust",
            "macerator/energized_fluix_crystal_dust",
            "forming_press/superconducting_processor_press",
            "forming_press/superconducting_processor_print",
            "forming_press/superconducting_processor"
        }) {
            assertGtStage(path, gtType(path), HV);
        }
    }

    @Test
    void infiniteComponentUsesUvAssemblyLine() throws IOException {
        JsonObject recipe = gtRecipe("assembly_line/eco_infinite_cell_component");

        assertEquals("gtceu:assembly_line", recipe.get("type").getAsString());
        assertEquals(UV, eu(recipe));
        assertEquals(16, recipe.getAsJsonObject("inputs").getAsJsonArray("item").size());
        assertTrue(hasItemCount(recipe, "neoecoae:eco_cell_component_256m", 8));
        assertTrue(hasItemCount(recipe, "neoecoae:eco_computation_cell_l9", 1));
        assertTrue(hasItemCount(recipe, "neoecoae:eco_item_storage_cell_256m", 1));
        assertTrue(hasItemCount(recipe, "neoecoae:storage_system_l9", 1));
        assertTrue(hasItemCount(recipe, "neoecoae:computation_system_l9", 1));
        assertTrue(hasItemCount(recipe, "neoecoae:crafting_system_l9", 1));
        assertTrue(hasItem(recipe, "gtceu:uv_electric_motor"));
        assertTrue(hasItem(recipe, "gtceu:uv_electric_piston"));
        assertTrue(hasItem(recipe, "gtceu:uv_electric_pump"));
        assertTrue(hasItem(recipe, "gtceu:uv_conveyor_module"));
        assertTrue(hasItem(recipe, "gtceu:uv_robot_arm"));
        assertTrue(hasItem(recipe, "gtceu:uv_emitter"));
        assertTrue(hasItem(recipe, "gtceu:uv_sensor"));
        assertTrue(hasItem(recipe, "gtceu:uv_field_generator"));
        assertTrue(hasFluid(recipe, "gtceu:europium"));
        assertTrue(hasFluid(recipe, "gtceu:tritanium"));
        assertTrue(hasFluid(recipe, "gtceu:neutronium"));
        assertTrue(hasResearch(recipe, "neoecoae:eco_cell_component_256m", "gtceu:data_module"));
        assertTrue(hasItemOutput(recipe, "neoecoae:eco_infinite_cell_component"));
    }

    @Test
    void craftingWorkerGtAssemblerRecipeAvoidsMeController() throws IOException {
        JsonObject recipe = gtRecipe("assembler/crafting_worker");

        assertEquals("gtceu:assembler", recipe.get("type").getAsString());
        assertEquals(HV, eu(recipe));
        assertTrue(hasItemCount(recipe, "ae2:256k_crafting_storage", 4));
        assertTrue(hasItemCount(recipe, "ae2:interface", 1));
        assertTrue(hasItemCount(recipe, "neoecoae:crafting_casing", 3));
        assertTrue(hasItemCount(recipe, "neoecoae:crafting_vent", 1));
        assertFalse(hasItem(recipe, "ae2:controller"));
        assertTrue(hasItemOutput(recipe, "neoecoae:crafting_worker"));
    }

    @Test
    void pureEcoInfiniteComponentRecipeFitsIntegratedWorkingStationSlots() throws IOException {
        JsonObject recipe = recipe("integrated_working_station/eco_infinite_cell_component");

        assertEquals("neoecoae:integrated_working_station", recipe.get("type").getAsString());
        assertEquals(9, recipe.getAsJsonArray("inputItems").size());
        assertTrue(hasSizedInput(recipe, "neoecoae:eco_cell_component_256m", 8));
        assertTrue(hasSizedInput(recipe, "neoecoae:eco_computation_cell_l9", 1));
        assertTrue(hasSizedInput(recipe, "neoecoae:eco_item_storage_cell_256m", 1));
        assertTrue(hasSizedInput(recipe, "neoecoae:storage_system_l9", 1));
        assertTrue(hasSizedInput(recipe, "neoecoae:computation_system_l9", 1));
        assertTrue(hasSizedInput(recipe, "neoecoae:crafting_system_l9", 1));
        assertTrue(hasSizedInput(recipe, "ae2:singularity", 16));
        assertEquals(16_000, recipe.getAsJsonObject("inputFluid").get("amount").getAsInt());
        assertEquals(
                "neoecoae:eco_infinite_cell_component",
                recipe.getAsJsonObject("itemOutput").get("item").getAsString());
    }

    @Test
    void integratedWorkingStationRecipesUseKubeJsRecipeSchemaFields() throws IOException {
        try (Stream<Path> recipes = Files.walk(RECIPE_ROOT)) {
            for (Path path : recipes.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList()) {
                JsonObject recipe =
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!"neoecoae:integrated_working_station"
                        .equals(recipe.get("type").getAsString())) {
                    continue;
                }

                if (recipe.has("itemOutput")) {
                    JsonObject itemOutput = recipe.getAsJsonObject("itemOutput");
                    assertTrue(itemOutput.has("item"), path.toString());
                    assertFalse(itemOutput.has("id"), path.toString());
                }

                if (recipe.has("inputFluid")) {
                    JsonObject inputFluid = recipe.getAsJsonObject("inputFluid");
                    assertTrue(inputFluid.has("fluid"), path.toString());
                    assertFalse(inputFluid.has("tag"), path.toString());
                }
            }
        }
    }

    @Test
    void coolingRecipesUseKubeJsFluidField() throws IOException {
        try (Stream<Path> recipes = Files.walk(RECIPE_ROOT.resolve("cooling"))) {
            for (Path path : recipes.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList()) {
                JsonObject input = JsonParser.parseString(Files.readString(path))
                        .getAsJsonObject()
                        .getAsJsonObject("input");

                assertTrue(input.has("fluid"), path.toString());
                assertFalse(input.has("tag"), path.toString());

                JsonObject recipe =
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (recipe.has("output")) {
                    JsonObject output = recipe.getAsJsonObject("output");
                    assertTrue(output.has("fluid"), path.toString());
                    assertTrue(output.has("amount"), path.toString());
                }
            }
        }
    }

    @Test
    void infiniteComponentTagIncludesGtlAndGtoCompatItems() throws IOException {
        JsonObject tag = JsonParser.parseString(Files.readString(TAG_ROOT.resolve("infinite_cell_components.json")))
                .getAsJsonObject();

        assertTrue(hasTagValue(tag, "neoecoae:eco_infinite_cell_component"));
        assertTrue(hasOptionalTagValue(tag, "gtlcore:infinite_cell_component"));
        assertTrue(hasOptionalTagValue(tag, "gtocore:infinite_cell_component"));
    }

    @Test
    void inscriberRecipesHaveGtceuFormingPressAlternatives() throws IOException {
        assertGtStage("macerator/iron_dust", "gtceu:macerator", 512);
        assertGtStage("macerator/aluminum_dust", "gtceu:macerator", 512);
        assertGtStage("macerator/tungsten_dust", "gtceu:macerator", 512);
        assertGtStage("macerator/aluminum_alloy_dust", "gtceu:macerator", 512);
        assertGtStage("macerator/black_tungsten_alloy_dust", "gtceu:macerator", 512);
        assertGtStage("macerator/energized_crystal_dust", "gtceu:macerator", 512);
        assertGtStage("macerator/energized_fluix_crystal_dust", "gtceu:macerator", 512);

        JsonObject printRecipe = gtRecipe("forming_press/superconducting_processor_print");
        assertEquals("gtceu:forming_press", printRecipe.get("type").getAsString());
        assertTrue(hasNonConsumableItem(printRecipe, "neoecoae:superconducting_processor_press"));

        JsonObject pressRecipe = gtRecipe("forming_press/superconducting_processor_press");
        assertTrue(hasNonConsumableItem(pressRecipe, "neoecoae:superconducting_processor_press"));

        JsonObject processorRecipe = gtRecipe("forming_press/superconducting_processor");
        assertTrue(hasItem(processorRecipe, "neoecoae:superconducting_processor_print"));
        assertTrue(hasItemTag(processorRecipe, "forge:dusts/silicon"));
        assertTrue(hasItemOutput(processorRecipe, "neoecoae:superconducting_processor"));

        JsonObject inscriberRecipe = recipe("inscriber/superconducting_processor");
        assertEquals("ae2:inscriber", inscriberRecipe.get("type").getAsString());
        assertEquals(
                "forge:plates/silicon",
                inscriberRecipe
                        .getAsJsonObject("ingredients")
                        .getAsJsonObject("bottom")
                        .get("tag")
                        .getAsString());
    }

    @Test
    void gtceuRecipesUseNativeRecipeTypesForRecipeViewerDiscovery() throws IOException {
        for (String path : new String[] {
            "assembler/storage_system_l4",
            "assembler/eco_cell_component_256m",
            "assembler/crafting_worker",
            "assembly_line/eco_infinite_cell_component",
            "forming_press/superconducting_processor",
            "chemical_reactor/energized_crystal",
            "mixer/cryotheum_solution"
        }) {
            assertTrue(recipe(path).get("type").getAsString().startsWith("gtceu:"));
        }
    }

    @Test
    void thirdPartyRecipeSerializersHaveLoadConditions() throws IOException {
        try (Stream<Path> recipes = Files.walk(RECIPE_ROOT)) {
            for (Path path : recipes.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".json"))
                    .toList()) {
                JsonObject recipe =
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                String type = recipe.get("type").getAsString();
                String requiredMod =
                        switch (type.substring(0, type.indexOf(':'))) {
                            case "gtceu" -> "gtceu";
                            case "mekanism" -> "mekanism";
                            case "advanced_ae" -> "advanced_ae";
                            case "expatternprovider" -> "expatternprovider";
                            case "botania" -> "appbot";
                            case "ars_nouveau" -> "arseng";
                            default -> null;
                        };
                if (requiredMod != null) {
                    assertTrue(hasModLoadedCondition(recipe, requiredMod), path.toString());
                }
                assertFalse(type.startsWith("extendedae:"), path.toString());
            }
        }
    }

    @Test
    void generatedCustomRecipesRespectRuntimeBounds() throws IOException {
        try (Stream<Path> recipes = Files.walk(RECIPE_ROOT)) {
            for (Path path : recipes.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".json"))
                    .toList()) {
                JsonObject recipe =
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                String type = recipe.get("type").getAsString();
                if ("neoecoae:integrated_working_station".equals(type)) {
                    assertTrue(recipe.get("energy").getAsInt() > 0, path.toString());
                    assertTrue(recipe.getAsJsonArray("inputItems").size() <= 9, path.toString());
                    assertTrue(recipe.has("itemOutput") || recipe.has("fluidOutput"), path.toString());
                } else if ("neoecoae:cooling".equals(type)) {
                    assertTrue(recipe.get("coolant").getAsInt() > 0, path.toString());
                    assertTrue(recipe.getAsJsonObject("input").get("amount").getAsInt() > 0, path.toString());
                    assertTrue(recipe.get("max_overclock").getAsInt() >= 0, path.toString());
                }
            }
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
            "assembler/crafting_worker",
            "assembler/crafting_system_l4",
            "assembler/crafting_system_l6",
            "assembler/crafting_system_l9",
            "forming_press/superconducting_processor"
        }) {
            assertTrue(!hasItemTagPrefix(gtRecipe(path), "gtceu:circuits/"));
        }
    }

    private static void assertGtStage(String path, String type, int eu) throws IOException {
        JsonObject recipe = gtRecipe(path);

        assertEquals(type, recipe.get("type").getAsString());
        assertEquals(eu, eu(recipe));
    }

    private static String gtType(String path) {
        return "gtceu:" + path.substring(0, path.indexOf('/'));
    }

    private static JsonObject gtRecipe(String path) throws IOException {
        return recipe(path);
    }

    private static JsonObject recipe(String path) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPE_ROOT.resolve(path + ".json")))
                .getAsJsonObject();
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

    private static boolean hasItemTag(JsonObject recipe, String tag) {
        for (var element : recipe.getAsJsonObject("inputs").getAsJsonArray("item")) {
            JsonObject ingredient =
                    element.getAsJsonObject().getAsJsonObject("content").getAsJsonObject("ingredient");
            if (ingredient.has("tag") && tag.equals(ingredient.get("tag").getAsString())) {
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

    private static boolean hasItemCount(JsonObject recipe, String item, int count) {
        for (var element : recipe.getAsJsonObject("inputs").getAsJsonArray("item")) {
            JsonObject content = element.getAsJsonObject().getAsJsonObject("content");
            JsonObject ingredient = content.getAsJsonObject("ingredient");
            if (ingredient.has("item")
                    && item.equals(ingredient.get("item").getAsString())
                    && content.get("count").getAsInt() == count) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSizedInput(JsonObject recipe, String item, int count) {
        for (var element : recipe.getAsJsonArray("inputItems")) {
            JsonObject input = element.getAsJsonObject();
            if (input.has("item")
                    && item.equals(input.get("item").getAsString())
                    && input.get("count").getAsInt() == count) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTagValue(JsonObject tag, String id) {
        for (var element : tag.getAsJsonArray("values")) {
            if (element.isJsonPrimitive() && id.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOptionalTagValue(JsonObject tag, String id) {
        for (var element : tag.getAsJsonArray("values")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject value = element.getAsJsonObject();
            if (id.equals(value.get("id").getAsString())
                    && !value.get("required").getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonConsumableItem(JsonObject recipe, String item) {
        for (var element : recipe.getAsJsonObject("inputs").getAsJsonArray("item")) {
            JsonObject input = element.getAsJsonObject();
            JsonObject ingredient = input.getAsJsonObject("content").getAsJsonObject("ingredient");
            if (ingredient.has("item")
                    && item.equals(ingredient.get("item").getAsString())
                    && input.has("chance")
                    && input.get("chance").getAsInt() == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFluid(JsonObject recipe, String fluid) {
        for (var element : recipe.getAsJsonObject("inputs").getAsJsonArray("fluid")) {
            JsonObject value =
                    element.getAsJsonObject().getAsJsonObject("content").getAsJsonObject("value");
            if (value.has("fluid") && fluid.equals(value.get("fluid").getAsString())) {
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

    private static boolean hasModLoadedCondition(JsonObject recipe, String modId) {
        if (!recipe.has("conditions")) {
            return false;
        }
        for (var element : recipe.getAsJsonArray("conditions")) {
            JsonObject condition = element.getAsJsonObject();
            if ("forge:mod_loaded".equals(condition.get("type").getAsString())
                    && modId.equals(condition.get("modid").getAsString())) {
                return true;
            }
        }
        return false;
    }
}
