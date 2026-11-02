package cn.dancingsnow.neoecoae.client.rendering;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Resource-only contracts: optional integrations and the original desktop pack are not loaded. */
class CellModelResourceTest {
    private static final List<Path> ROOTS = List.of(
        Path.of("src/main/resources/assets/neoecoae"),
        Path.of("src/generated/resources/assets/neoecoae"));
    private static final Path JAVA = Path.of("src/main/java/cn/dancingsnow/neoecoae");
    private static final String ITEM = "neoecoae:item/eco_cells/";
    private static final Pattern OLD_REFERENCE = Pattern.compile(
        "item/eco_cell_compat/|item/eco_cell_(?:light_|status_light)|"
            + "block/storage/drive/(?:[^\\\"\\s]*housing|cell_(?:level|type))|"
            + "block/cell/led/|storage_cell_l9_infinite_item|digital_sigularity");

    @Test
    void allLocalModelParentsOverridesAndTexturesExistAndTextureVariablesResolve() throws Exception {
        Map<String, JsonObject> models = models();
        assertFalse(models.isEmpty());
        for (var entry : models.entrySet()) {
            String name = entry.getKey();
            JsonObject model = entry.getValue();
            if (model.has("parent")) {
                requireLocalReference(model.get("parent").getAsString(), "models", ".json", name);
            }
            // Abstract templates may leave variables for children to supply. Matrix models must
            // resolve every variable, including inherited faces, in their concrete child context.
            boolean concreteMatrix = name.startsWith("block/cell/storage_cell_")
                || name.startsWith("item/cell/")
                || name.matches("item/eco_[^/]+_cell_(?:16m|64m|256m|4g|housing)")
                || name.equals("item/eco_infinite_item_cell")
                || name.equals("item/eco_mega_long_bulk_cell");
            JsonObject textures = concreteMatrix ? resolvedTextures(name, models, new HashSet<>())
                : model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();
            for (var texture : textures.entrySet()) {
                String value = texture.getValue().getAsString();
                if (concreteMatrix) value = resolveVariable(value, textures, name);
                requireLocalReference(value, "textures", ".png", name);
            }
            JsonObject geometry = model;
            while (true) {
                if (geometry.has("elements")) {
                    for (var element : geometry.getAsJsonArray("elements")) {
                        for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                            String value = face.getValue().getAsJsonObject().get("texture").getAsString();
                            if (concreteMatrix) value = resolveVariable(value, textures, name);
                            requireLocalReference(value, "textures", ".png", name);
                        }
                    }
                    break;
                }
                if (!concreteMatrix || !geometry.has("parent")) break;
                String parent = geometry.get("parent").getAsString();
                if (!parent.startsWith("neoecoae:")) break;
                geometry = models.get(parent.substring("neoecoae:".length()));
                assertNotNull(geometry, name + " parent " + parent);
            }
            if (model.has("overrides")) {
                for (var override : model.getAsJsonArray("overrides")) {
                    requireLocalReference(override.getAsJsonObject().get("model").getAsString(),
                        "models", ".json", name);
                }
            }
        }
    }

    @Test
    void javaModelReferencesAndImportedItemCallbacksResolve() throws Exception {
        Pattern literal = Pattern.compile("\"((?:block/cell|item/cell)/[a-z0-9_./]+)\"");
        Pattern imported = Pattern.compile("importedCellModel\\(\"([a-z0-9_]+)\"\\)");
        Pattern cell = Pattern.compile("ItemModelUtil\\.cellModel\\(\"([a-z0-9_]+)\",\\s*\"([a-z0-9]+)\"\\)");
        for (Path path : files(JAVA, ".java")) {
            String source = Files.readString(path);
            var references = literal.matcher(source);
            while (references.find()) {
                String name = references.group(1);
                // The dynamic registration helpers end their literal prefix with an underscore.
                if (name.endsWith("_") || name.endsWith("/")) continue;
                requireLocalReference("neoecoae:" + name, "models", ".json", path.toString());
            }
            var parents = imported.matcher(source);
            while (parents.find()) requireModel("item/cell/" + parents.group(1));
            var cells = cell.matcher(source);
            while (cells.find()) {
                String type = cells.group(1).equals("fe") ? "energy" : cells.group(1);
                requireModel("item/cell/eco_" + type + "_cell_" + cells.group(2));
            }
        }
        // Expand dynamic family/tier registrations without initializing the mod registries.
        for (String level : List.of("l4", "l6", "l9")) {
            for (String family : List.of("item", "fluid", "fe", "chemical", "mana", "source", "data",
                "air", "soul", "experience", "lightning", "omni", "complex_omni", "quantum_omni",
                "neuclear_waste", "matter", "radioactive_chemical", "su")) {
                requireModel("block/cell/storage_cell_" + level + "_" + family);
            }
        }
        for (String family : List.of("item", "fluid", "fe", "chemical", "air", "experience", "soul",
            "mana", "source", "matter", "data")) {
            requireModel("block/cell/storage_cell_mega_l9_" + family);
        }
        requireModel("block/cell/storage_cell_l9_mega_su");
        requireModel("block/cell/storage_cell_l9_infinite_resource");
        requireModel("block/cell/storage_cell_l9_bulk_item");
    }

    @Test
    void cellItemLayersMatchTheStatusTintContractIncludingInfiniteAndAnimations() throws Exception {
        Map<String, JsonObject> models = models();
        int cells = 0;
        for (String name : models.keySet()) {
            if (!name.startsWith("item/")) continue;
            JsonObject textures = resolvedTextures(name, models, new HashSet<>());
            for (var entry : textures.entrySet()) {
                if (!entry.getValue().getAsString().equals(ITEM + "cell_status_light")) continue;
                cells++;
                boolean infinite = texture(textures, "layer0").equals(ITEM + "infinite_resource_cell");
                assertEquals(infinite ? "layer1" : "layer2", entry.getKey(), name);
                assertTrue(texture(textures, "layer0").startsWith(ITEM), name);
                if (infinite) {
                    assertFalse(textures.has("layer2"), name + " must have no tier light");
                } else {
                    String level = texture(textures, "layer1");
                    assertTrue(level.matches(Pattern.quote(ITEM) + "cell_light_l[469]"), name);
                    if (name.endsWith("_16m")) assertEquals(ITEM + "cell_light_l4", level, name);
                    if (name.endsWith("_64m")) assertEquals(ITEM + "cell_light_l6", level, name);
                    if (name.endsWith("_256m") || name.endsWith("_4g")) {
                        assertEquals(ITEM + "cell_light_l9", level, name);
                    }
                    if (textures.has("layer3")) {
                        String overlay = texture(textures, "layer3");
                        assertTrue(overlay.startsWith(ITEM), name);
                        assertTrue(exists("textures/" + overlay.substring("neoecoae:".length()) + ".png.mcmeta"),
                            name + " animation metadata: " + overlay);
                    }
                }
            }
        }
        assertTrue(cells >= 60, "Expected the registered cell icons and their imported parents");
        for (String size : List.of("16m", "64m", "256m")) {
            assertHousing(models, "eco_stress_cell_" + size, "applied_create/andesite_cell_housing");
            assertHousing(models, "eco_matter_cell_" + size, "apprep/matter_cell_housing");
            assertHousing(models, "eco_radioactive_chemical_cell_" + size,
                "megacells/radioactive_chemical_cell_housing");
            for (String family : List.of("omni", "complex_omni", "quantum_omni")) {
                assertHousing(models, "eco_" + family + "_cell_" + size,
                    "ae2omnicells/" + family + "_cell_housing");
            }
        }
        assertHousing(models, "eco_mega_stress_cell_4g", "applied_create/brass_cell_housing");
        assertHousing(models, "eco_mega_matter_cell_4g", "megacells/mega_matter_cell_housing");
        assertHousing(models, "eco_mega_data_cell_4g", "megacells/mega_data_cell_housing");
    }

    @Test
    void animationMetadataHasValidImageDimensionsFrameIndicesAndDurations() throws Exception {
        List<String> required = List.of(
            "item/eco_cells/ae2omnicells/quantum_omni_cell_layer",
            "item/eco_cells/ae2omnicells/spent_neuclear_waste_cell_layer",
            "item/eco_cells/bigger_ae2/digital_singularity_cell_layer",
            "block/storage/drive_cells/digital_singularity_cell_housing",
            "block/storage/drive_cells/cell_type_neuclear_waste");
        for (String texture : required) assertTrue(exists("textures/" + texture + ".png.mcmeta"), texture);
        for (Path root : ROOTS) {
            for (String folder : List.of("textures/item/eco_cells", "textures/block/storage/drive_cells")) {
                for (Path metadata : files(root.resolve(folder), ".png.mcmeta")) {
                    Path imagePath = metadata.resolveSibling(metadata.getFileName().toString().replace(".mcmeta", ""));
                    assertTrue(Files.isRegularFile(imagePath), metadata.toString());
                    var image = ImageIO.read(imagePath.toFile());
                    assertNotNull(image, imagePath.toString());
                    JsonObject animation = json(metadata).getAsJsonObject("animation");
                    assertNotNull(animation, metadata.toString());
                    int width = animation.has("width") ? animation.get("width").getAsInt() : image.getWidth();
                    int height = animation.has("height") ? animation.get("height").getAsInt()
                        : animation.has("width") ? image.getHeight() : Math.min(image.getWidth(), image.getHeight());
                    assertTrue(width > 0 && height > 0, metadata.toString());
                    assertEquals(0, image.getWidth() % width, metadata.toString());
                    assertEquals(0, image.getHeight() % height, metadata.toString());
                    int frames = (image.getWidth() / width) * (image.getHeight() / height);
                    if (animation.has("frametime")) assertTrue(animation.get("frametime").getAsInt() > 0, metadata.toString());
                    if (animation.has("frames")) {
                        assertFalse(animation.getAsJsonArray("frames").isEmpty(), metadata.toString());
                        for (JsonElement frame : animation.getAsJsonArray("frames")) {
                            int index = frame.isJsonObject() ? frame.getAsJsonObject().get("index").getAsInt() : frame.getAsInt();
                            assertTrue(index >= 0 && index < frames, metadata + " frame " + index);
                            if (frame.isJsonObject() && frame.getAsJsonObject().has("time")) {
                                assertTrue(frame.getAsJsonObject().get("time").getAsInt() > 0, metadata.toString());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void legacyPathsAndMisspelledDigitalSingularityAreAbsent() throws Exception {
        for (Path root : ROOTS) {
            assertFalse(Files.exists(root.resolve("textures/item/eco_cell_compat")), root.toString());
            for (Path model : files(root.resolve("models"), ".json")) {
                assertFalse(OLD_REFERENCE.matcher(Files.readString(model)).find(), model.toString());
            }
            for (Path texture : files(root.resolve("textures"), ".png")) {
                String path = root.relativize(texture).toString().replace('\\', '/');
                assertFalse(path.contains("digital_sigularity"), path);
                assertFalse(path.matches("textures/item/eco_(?:cell_(?:light_[^/]*|status_light)|[^/]*_cell_housing)\\.png"), path);
                assertFalse(path.matches("textures/block/storage/drive/(?:[^/]*housing|cell_[^/]*)\\.png"), path);
            }
        }
        for (Path source : files(JAVA, ".java")) {
            assertFalse(OLD_REFERENCE.matcher(Files.readString(source)).find(), source.toString());
        }
    }

    private static void assertHousing(Map<String, JsonObject> models, String item, String housing) {
        String name = "item/" + item;
        assertTrue(models.containsKey(name), name);
        JsonObject textures = resolvedTextures(name, models, new HashSet<>());
        assertEquals(ITEM + housing, texture(textures, "layer0"), name);
        assertEquals(ITEM + "cell_status_light", texture(textures, "layer2"), name);
    }

    private static String texture(JsonObject textures, String layer) {
        assertTrue(textures.has(layer), "Missing " + layer + " in " + textures);
        return textures.get(layer).getAsString();
    }

    private static String resolveVariable(String value, JsonObject textures, String model) {
        Set<String> visited = new HashSet<>();
        while (value.startsWith("#")) {
            assertTrue(visited.add(value), model + " texture cycle " + value);
            String variable = value.substring(1);
            assertTrue(textures.has(variable), model + " unresolved texture " + value);
            value = textures.get(variable).getAsString();
        }
        return value;
    }

    private static JsonObject resolvedTextures(String name, Map<String, JsonObject> models, Set<String> visited) {
        assertTrue(visited.add(name), "Parent model cycle: " + name);
        JsonObject model = models.get(name);
        assertNotNull(model, name);
        JsonObject textures = new JsonObject();
        if (model.has("parent")) {
            String parent = model.get("parent").getAsString();
            if (parent.startsWith("neoecoae:")) {
                textures = resolvedTextures(parent.substring("neoecoae:".length()), models, visited);
            }
        }
        if (model.has("textures")) {
            for (var entry : model.getAsJsonObject("textures").entrySet()) textures.add(entry.getKey(), entry.getValue());
        }
        return textures;
    }

    private static void requireModel(String model) {
        assertTrue(exists("models/" + model + ".json"), "Missing registered model: " + model);
    }

    private static void requireLocalReference(String value, String directory, String extension, String owner) {
        if (!value.startsWith("neoecoae:")) return;
        assertTrue(exists(directory + "/" + value.substring("neoecoae:".length()) + extension),
            owner + " references missing " + value);
    }

    private static boolean exists(String relative) {
        return ROOTS.stream().anyMatch(root -> Files.isRegularFile(root.resolve(relative)));
    }

    private static Map<String, JsonObject> models() throws IOException {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (Path root : ROOTS) {
            Path directory = root.resolve("models");
            for (Path file : files(directory, ".json")) {
                String name = directory.relativize(file).toString().replace('\\', '/').replaceFirst("\\.json$", "");
                JsonObject model = json(file);
                JsonObject previous = result.putIfAbsent(name, model);
                assertTrue(previous == null || previous.equals(model), "Conflicting main/generated model: " + name);
            }
        }
        return result;
    }

    private static JsonObject json(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<Path> files(Path directory, String suffix) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(suffix)).sorted().toList();
        }
    }
}
