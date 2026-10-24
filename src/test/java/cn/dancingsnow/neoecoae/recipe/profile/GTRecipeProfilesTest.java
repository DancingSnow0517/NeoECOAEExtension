package cn.dancingsnow.neoecoae.recipe.profile;

import cn.dancingsnow.neoecoae.config.RecipeProfileConfig.Profile;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraftforge.common.crafting.ConditionalRecipe;
import net.minecraftforge.resource.PathPackResources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.dancingsnow.neoecoae.recipe.profile.GTRecipeProfiles.Schema;
import static org.junit.jupiter.api.Assertions.*;

class GTRecipeProfilesTest {
    private static final Path ROOT = Path.of("src/main/resources/recipe_profiles");

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void autoWithoutGtKeepsStandaloneWithoutInspectingGtClasses() {
        for (var mods : List.of(Set.<String>of(), Set.of("gtlcore"))) {
            assertEquals(List.of(), GTRecipeProfiles.packPaths(Profile.AUTO, mods::contains,
                () -> { throw new AssertionError("GT API queried without gtceu"); }));
        }
    }

    @ParameterizedTest @EnumSource(Schema.class)
    void autoSelectsGtlBeforeGtmAndUsesTheInstalledMachineSchema(Schema schema) {
        var machinePack = "gt_" + schema.name().toLowerCase(java.util.Locale.ROOT);
        assertEquals(List.of("gtm", machinePack), GTRecipeProfiles.packPaths(Profile.AUTO,
            "gtceu"::equals, () -> schema));
        assertEquals(List.of("gtl", machinePack), GTRecipeProfiles.packPaths(Profile.AUTO,
            Set.of("gtceu", "gtlcore")::contains, () -> schema));
    }

    @Test void standaloneDoesNotInspectOptionalModsOrGtClasses() {
        assertEquals(List.of(), GTRecipeProfiles.packPaths(Profile.STANDALONE,
            mod -> { throw new AssertionError("Optional mod queried in standalone mode"); },
            () -> { throw new AssertionError("GT API queried in standalone mode"); }));
    }

    @Test void selectedProfileFailsBeforeRemovingRecipesWhenDependenciesAreMissing() {
        assertThrows(IllegalStateException.class,
            () -> GTRecipeProfiles.packPaths(Profile.GTM, mod -> false, () -> Schema.LEGACY));
        assertThrows(IllegalStateException.class,
            () -> GTRecipeProfiles.packPaths(Profile.GTL, "gtceu"::equals, () -> Schema.LEGACY));
        assertThrows(IllegalStateException.class,
            () -> Schema.fromRegistryType("unknown.Registry"));
    }

    @Test void installedRegistryContractSelectsOneMachineSchemaAndOneProgressionProfile() {
        assertEquals(Schema.LEGACY,
            Schema.fromRegistryType("com.gregtechceu.gtceu.api.registry.GTRegistry$String"));
        assertEquals(Schema.MODERN,
            Schema.fromRegistryType("com.gregtechceu.gtceu.api.registry.GTRegistry$RL"));
        for (var profile : List.of(Profile.GTM, Profile.GTL)) {
            for (var schema : Schema.values()) {
                var paths = GTRecipeProfiles.packPaths(profile, mod -> true, () -> schema);
                assertEquals(2, paths.size());
                assertEquals(profile.name().toLowerCase(java.util.Locale.ROOT), paths.get(0));
                assertEquals("gt_" + schema.name().toLowerCase(java.util.Locale.ROOT), paths.get(1));
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"gtm", "gtl"})
    void automaticallySelectedPacksOverrideOldRecipesAndSurviveDatapackReload(String profile) throws Exception {
        var mods = profile.equals("gtl") ? Set.of("gtceu", "gtlcore") : Set.of("gtceu");
        var paths = GTRecipeProfiles.packPaths(Profile.AUTO, mods::contains, () -> Schema.LEGACY);
        var repository = new PackRepository(consumer -> paths.forEach(name -> consumer.accept(pack(name))));
        repository.reload();
        assertEquals(2, repository.getSelectedIds().size());
        repository.setSelected(List.of());
        repository.reload();
        assertEquals(2, repository.getSelectedIds().size());
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(
            new PathPackResources("base", true, Path.of("src/generated/resources")),
            new PathPackResources("profile", true, ROOT.resolve(paths.get(0))),
            new PathPackResources("machine", true, ROOT.resolve("gt_legacy"))))) {
            var id = new ResourceLocation("neoecoae:recipes/aluminum_alloy_dust.json");
            try (var reader = manager.getResource(id).orElseThrow().openAsReader()) {
                var disabled = JsonParser.parseReader(reader).getAsJsonObject();
                assertEquals("forge:conditional", disabled.get("type").getAsString());
                assertNull(new ConditionalRecipe.Serializer<>().fromJson(id, disabled));
            }
            try (var reader = manager.getResource(new ResourceLocation(
                    "neoecoae:recipes/integrated_working_station/crystal_ingot.json")).orElseThrow().openAsReader()) {
                var recipe = JsonParser.parseReader(reader).getAsJsonObject();
                assertEquals(4, recipe.getAsJsonObject("itemOutput").get("count").getAsInt());
                assertEquals("gtceu:argon", recipe.getAsJsonObject("inputFluid").get("fluid").getAsString());
            }
            assertTrue(manager.getResource(new ResourceLocation(
                "neoecoae:recipes/compat/gt/bootstrap/b10_integrated_working_station.json")).isPresent());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"gtm", "gtl"})
    void everyNativeRecipeUsesTheRealForgeSerializerAndRetainsItsCosts(String profile) throws Exception {
        int nativeCount = 0;
        int disabledCount = 0;
        for (var path : recipeFiles(profile)) {
            var json = read(path);
            if (json.get("type").getAsString().equals("forge:conditional")) {
                assertNull(new ConditionalRecipe.Serializer<>().fromJson(new ResourceLocation("test:disabled"), json));
                disabledCount++;
                continue;
            }
            var mods = json.getAsJsonArray("conditions").asList().stream()
                .map(c -> c.getAsJsonObject().get("modid").getAsString()).collect(Collectors.toSet());
            assertTrue(mods.contains("gtceu"));
            assertEquals(profile.equals("gtl"), mods.contains("gtlcore"));
            if (!json.get("type").getAsString().equals("neoecoae:integrated_working_station")) continue;
            nativeCount++;
            // Use vanilla identities only to isolate JSON/quantity handling from optional mod registration.
            var decode = json.deepCopy();
            substituteRegistryIdentities(decode);
            var recipe = new IntegratedWorkingStationRecipe.Serializer().fromJson(new ResourceLocation("test:profile"), decode);
            assertEquals(json.get("energy").getAsInt(), recipe.energy());
            assertEquals(100, recipe.processingTicks());
            assertTrue(recipe.inputItems().size() <= 9);
            if (json.has("itemOutput")) {
                assertEquals(json.getAsJsonObject("itemOutput").get("count").getAsInt(), recipe.itemOutput().getCount());
            }
            if (json.has("inputFluid")) {
                assertEquals(json.getAsJsonObject("inputFluid").get("amount").getAsInt(), recipe.inputFluid().amount());
            }
        }
        assertEquals(46, nativeCount); // 42 approved replacements and 4 optional module recipes.
        assertEquals(27, disabledCount);
        var waste = recipe(profile, "integrated_working_station/eco_spent_nuclear_waste_cell_16m");
        assertTrue(waste.getAsJsonArray("conditions").toString().contains("appmek"));
        assertTrue(waste.getAsJsonArray("conditions").toString().contains("ae2omnicells"));
    }

    @ParameterizedTest @ValueSource(strings = {"legacy", "modern"})
    void gtMachineRecipesKeepRealQuantitiesTickEuAndProcessRequirements(String schema) throws Exception {
        String prefix = schema.equals("modern") ? "gtceu:" : "";
        assertEquals(20, recipeFiles("gt_" + schema).size());
        var controller = recipe("gt_" + schema, "compat/gt/bootstrap/b10_integrated_working_station");
        assertEquals("gtceu:assembler", controller.get("type").getAsString());
        assertEquals(600, controller.get("duration").getAsInt());
        assertEquals(9, controller.getAsJsonObject("inputs").getAsJsonArray(prefix + "item").size());
        var eu = controller.getAsJsonObject("tickInputs").getAsJsonArray(prefix + "eu")
            .get(0).getAsJsonObject().get("content");
        if (schema.equals("modern")) {
            assertEquals(1920, eu.getAsJsonObject().get("voltage").getAsInt());
            assertEquals(1, eu.getAsJsonObject().get("amperage").getAsInt());
        } else assertEquals(1920, eu.getAsInt());
        var blast = recipe("gt_" + schema, "compat/gt/bootstrap/b04_black_tungsten_alloy_ingot");
        assertEquals(4500, blast.getAsJsonObject("data").get("ebf_temp").getAsInt());
        var processor = recipe("gt_" + schema, "compat/gt/bootstrap/b09_superconducting_processor");
        assertEquals(prefix + "cleanroom", processor.getAsJsonArray("recipeConditions")
            .get(0).getAsJsonObject().get("type").getAsString());
        var input = processor.getAsJsonObject("inputs").getAsJsonArray(prefix + "item").get(0)
            .getAsJsonObject().getAsJsonObject("content");
        assertEquals("gtceu:sized", input.get("type").getAsString());
        assertEquals(4, input.get("count").getAsInt());
        assertTrue(input.has("ingredient"));
        for (var path : recipeFiles("gt_" + schema)) {
            var json = read(path);
            assertFalse(json.has("energy")); // These are original-machine EU recipes, never ECO AE recipes.
            assertTrue(json.getAsJsonObject("inputs").has(prefix + "item"));
            assertFalse(json.has("proposal_profile"));
        }
    }

    @Test void gtlReplacesOnlyTheSpecifiedTiersAndKeepsTheIvLargeStructure() throws Exception {
        var gtm = recipe("gtm", "eco_cell_component_16m").toString();
        var gtl = recipe("gtl", "eco_cell_component_16m").toString();
        assertTrue(gtm.contains("gtceu:ev_field_generator"));
        assertTrue(gtl.contains("gtceu:iv_field_generator"));
        assertTrue(recipe("gtl", "eco_cell_component_64m").toString().contains("gtceu:zpm_field_generator"));
        assertTrue(recipe("gtl", "eco_cell_component_256m").toString().contains("gtceu:uhv_field_generator"));
        var casing = recipe("gtl", "large_integrated_working_station_casing");
        assertEquals(4, casing.getAsJsonObject("itemOutput").get("count").getAsInt());
        assertTrue(casing.toString().contains("neoecoae:eco_cell_component_16m"));
        var infinite = recipe("gtl", "eco_infinite_cell_component");
        assertTrue(infinite.toString().contains("gtceu:quantanium_ingot"));
        assertTrue(infinite.toString().contains("gtceu:uev_field_generator"));
        assertEquals(128, infinite.getAsJsonArray("inputItems").get(0).getAsJsonObject().get("count").getAsInt());
    }

    @Test void advancedAeKeepsItsWholeBatchAndModulesKeepTheirOriginalDependencyPermissions() throws Exception {
        for (String profile : List.of("gtm", "gtl")) {
            for (String crystal : List.of("energized_crystal", "energized_fluix_crystal")) {
                var reaction = recipe(profile, "reaction_chamber/" + crystal);
                assertEquals("advanced_ae:reaction", reaction.get("type").getAsString());
                assertEquals(64, reaction.getAsJsonObject("output").get("#").getAsInt());
                assertEquals(500_000, reaction.get("energy").getAsInt());
                var fluid = reaction.getAsJsonObject("fluid").getAsJsonObject("fluidStack");
                assertEquals("gtceu:distilled_water", fluid.get("FluidName").getAsString());
                assertEquals(500, fluid.get("Amount").getAsInt());
            }
            for (var card : Map.of("advanced_ae", "advanced_ae", "extendedae", "expatternprovider",
                    "aelt", "ae2lt", "ae2cs", "ae2cs").entrySet()) {
                var module = recipe(profile, "integrated_working_station/module_" + card.getKey());
                assertTrue(module.getAsJsonArray("conditions").toString().contains(card.getValue()));
                assertEquals(16_000, module.get("energy").getAsInt());
                assertEquals(100, module.get("processingTicks").getAsInt());
                assertEquals("neoecoae:integrated_working_station_module_empty", module.getAsJsonArray("inputItems")
                    .get(0).getAsJsonObject().get("item").getAsString());
            }
        }
    }

    @Test void firstControllerIsReachableUsingGtMachinesAndRetainedAeRoutesWithoutAnyWorkstation() throws Exception {
        var dependencies = new HashMap<String, Set<String>>();
        for (var path : recipeFiles("gt_legacy")) {
            var recipe = read(path);
            var output = recipe.getAsJsonObject("outputs").getAsJsonArray("item").get(0).getAsJsonObject()
                .getAsJsonObject("content").getAsJsonObject("ingredient").get("item").getAsString();
            dependencies.put(output, neoDependencies(recipe.getAsJsonObject("inputs")));
        }
        var base = Path.of("src/generated/resources/data/neoecoae/recipes");
        for (String name : List.of("crystal_matrix", "superconducting_processor_press",
                "inscriber/superconducting_processor_print", "inscriber/energized_crystal_dust",
                "inscriber/energized_fluix_crystal_dust")) {
            var recipe = read(base.resolve(name + ".json"));
            var output = recipe.getAsJsonObject("result").get("item").getAsString();
            dependencies.put(output, neoDependencies(recipe.has("key") ? recipe.get("key") : recipe.get("ingredients")));
        }
        // Existing world growth supplies the initial crystal; this audit concerns Neo-owned circular prerequisites.
        var reachable = new HashSet<>(Set.of("neoecoae:energized_crystal"));
        boolean changed;
        do {
            changed = false;
            for (var entry : dependencies.entrySet()) {
                if (reachable.containsAll(entry.getValue())) changed |= reachable.add(entry.getKey());
            }
        } while (changed);
        assertTrue(reachable.contains("neoecoae:integrated_working_station"), dependencies.toString());
        assertFalse(dependencies.values().stream().anyMatch(d -> d.contains("neoecoae:integrated_working_station")));
    }

    @ParameterizedTest @ValueSource(strings = {"gtm", "gtl"})
    void materialConversionAndPrivateTagsPreserveMassWithoutPublicTagCycles(String profile) throws Exception {
        var block = recipe(profile, "aluminum_block");
        assertEquals(List.of("AAA", "AAA", "AAA"), block.getAsJsonArray("pattern").asList().stream()
            .map(JsonElement::getAsString).toList());
        assertEquals("gtceu:aluminium_block", block.getAsJsonObject("result").get("item").getAsString());
        assertEquals(9, recipe(profile, "aluminum_ingot").getAsJsonObject("result").get("count").getAsInt());
        assertEquals(9, recipe(profile, "tungsten_ingot").getAsJsonObject("result").get("count").getAsInt());
        var tags = ROOT.resolve(profile).resolve("data/neoecoae/tags/items/compat/gt");
        try (var files = Files.list(tags)) { assertEquals(6, files.count()); }
        assertFalse(Files.exists(ROOT.resolve(profile).resolve("data/forge")));
        var silicon = read(tags.resolve("silicon.json")).getAsJsonArray("values").asList().stream()
            .map(c -> c.getAsJsonObject().get("id").getAsString()).collect(Collectors.toSet());
        assertEquals(Set.of("ae2:silicon", "gtceu:silicon_ingot", "gtceu:silicon_dust"), silicon);
        for (String metal : List.of("aluminum", "tungsten")) {
            for (String form : List.of("ingot", "dust", "block")) {
                var conversion = recipe(profile, "compat/gt/materials/" + metal + "_" + form);
                assertEquals(1, conversion.getAsJsonArray("ingredients").size());
                assertEquals(1, conversion.getAsJsonObject("result").get("count").getAsInt());
            }
        }
    }

    private static Pack pack(String name) {
        return java.util.Objects.requireNonNull(Pack.readMetaAndCreate("test:" + name,
            net.minecraft.network.chat.Component.literal(name), true,
            id -> new PathPackResources(id, true, ROOT.resolve(name)), PackType.SERVER_DATA,
            Pack.Position.TOP, PackSource.BUILT_IN));
    }

    private static List<Path> recipeFiles(String name) throws IOException {
        try (var files = Files.walk(ROOT.resolve(name).resolve("data/neoecoae/recipes"))) {
            return files.filter(p -> p.toString().endsWith(".json")).toList();
        }
    }

    private static JsonObject recipe(String pack, String path) throws IOException {
        return read(ROOT.resolve(pack).resolve("data/neoecoae/recipes/" + path + ".json"));
    }

    private static JsonObject read(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Set<String> neoDependencies(JsonElement json) {
        var result = new HashSet<String>();
        if (json.isJsonArray()) json.getAsJsonArray().forEach(value -> result.addAll(neoDependencies(value)));
        else if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("item") && entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsString().startsWith("neoecoae:")) {
                    result.add(entry.getValue().getAsString());
                } else if (entry.getKey().equals("tag") && entry.getValue().isJsonPrimitive()) {
                    var item = Map.of("forge:dusts/aluminum_alloy", "neoecoae:aluminum_alloy_dust",
                        "forge:gems/energized_crystal", "neoecoae:energized_crystal",
                        "forge:gems/energized_fluix_crystal", "neoecoae:energized_fluix_crystal",
                        "forge:dusts/energized_crystal", "neoecoae:energized_crystal_dust",
                        "forge:dusts/energized_fluix_crystal", "neoecoae:energized_fluix_crystal_dust")
                        .get(entry.getValue().getAsString());
                    if (item != null) result.add(item);
                } else result.addAll(neoDependencies(entry.getValue()));
            }
        }
        return result;
    }

    private static void substituteRegistryIdentities(JsonElement value) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(GTRecipeProfilesTest::substituteRegistryIdentities);
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("item")) object.addProperty("item", "minecraft:iron_ingot");
            if (object.has("fluid")) object.addProperty("fluid", "minecraft:water");
            object.entrySet().forEach(entry -> substituteRegistryIdentities(entry.getValue()));
        }
    }
}
