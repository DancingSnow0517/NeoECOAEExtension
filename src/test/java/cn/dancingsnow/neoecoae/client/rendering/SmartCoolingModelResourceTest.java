package cn.dancingsnow.neoecoae.client.rendering;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.texture.WorkstationSpriteSource;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmartCoolingModelResourceTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/neoecoae");
    private static final String TEXTURES = "block/working_station/controller_formed/";
    private static final List<String> LAYERS = List.of("controller_formed_screen_on/dark",
        "controller_formed_screen_on/middle", "controller_formed_screen_on/light",
        "controller_formed_light/dark", "controller_formed_light/light");
    private static final SpriteResourceLoader LOADER = SpriteResourceLoader.create(List.of(AnimationMetadataSection.SERIALIZER));

    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(NeoECOAE.class.getName());
        }
    }

    @Test
    void ordinaryAndSmartModelsSharePaletteLayersButUseTheCorrectBody() throws Exception {
        var generated = sprites(source(), resources(Map.of()));
        for (boolean working : new boolean[] {false, true}) {
            var ordinary = read("models/" + LargeWorkstationModule.REGULAR.modelPath(working) + ".json");
            var smart = read("models/" + LargeWorkstationModule.REGULAR.modelPath(working, true) + ".json");
            assertFalse(ordinary.getAsJsonObject("textures").has("cooling_screen"));
            assertEquals("neoecoae:" + TEXTURES + "controller_formed_smart",
                smart.getAsJsonObject("textures").get("body").getAsString());
            for (var module : LargeWorkstationModule.values()) {
                var regularModel = read("models/" + module.modelPath(working) + ".json");
                var smartModel = read("models/" + module.modelPath(working, true) + ".json");
                if (module != LargeWorkstationModule.REGULAR) {
                    assertEquals(regularModel.get("textures"), smartModel.get("textures"), module.toString());
                    assertEquals("neoecoae:" + LargeWorkstationModule.REGULAR.modelPath(working, true),
                        smartModel.get("parent").getAsString());
                }
                for (var entry : regularModel.getAsJsonObject("textures").entrySet()) {
                    var texture = ResourceLocation.parse(entry.getValue().getAsString());
                    if (texture.getPath().contains("/colored/")) {
                        assertTrue(generated.containsKey(texture), texture.toString());
                        if (texture.getPath().contains("/controller_formed_light/")) {
                            assertEquals(working, texture.getPath().endsWith("_working"));
                        }
                    } else {
                        assertTrue(Files.isRegularFile(ROOT.resolve("textures/" + texture.getPath() + ".png")), texture.toString());
                    }
                }
            }
        }
    }

    @Test
    void rearScreenRemainsUncoloredAndTheNewPortModelsAreConnected() throws Exception {
        for (boolean working : new boolean[] {false, true}) {
            var smart = read("models/" + LargeWorkstationModule.REGULAR.modelPath(working, true) + ".json");
            assertEquals("neoecoae:block/working_station/cooling_controller/screen_on",
                smart.getAsJsonObject("textures").get("cooling_screen").getAsString());
            var screen = smart.getAsJsonArray("elements").asList().stream().map(e -> e.getAsJsonObject())
                .filter(e -> e.get("name").getAsString().equals("back_screen")).findFirst().orElseThrow();
            assertEquals(JsonParser.parseString("[3,3,32]"), screen.get("from"));
            assertEquals(JsonParser.parseString("[13,13,32]"), screen.get("to"));
            assertEquals(java.util.Set.of("south"), screen.getAsJsonObject("faces").keySet());
            assertFalse(screen.get("shade").getAsBoolean());
        }
        assertEquals("neoecoae:block/large_integrated_working_station_pattern_provider",
            read("models/block/large_integrated_working_station_interface.json").get("parent").getAsString());
        var cooling = JsonParser.parseString(Files.readString(Path.of(
            "src/generated/resources/assets/neoecoae/models/block/large_integrated_working_station_smart_cooling_module.json")))
            .getAsJsonObject();
        assertEquals("neoecoae:block/large_integrated_working_station_cooling_controller", cooling.get("parent").getAsString());
    }

    @Test
    void unknownModulesUseTheOriginalMasksWithoutPaletteColoring() throws Exception {
        for (boolean working : new boolean[] {false, true}) {
            for (boolean smart : new boolean[] {false, true}) {
                var model = read("models/" + LargeWorkstationModule.OTHER.modelPath(working, smart) + ".json");
                for (var texture : model.getAsJsonObject("textures").asMap().values()) {
                    assertFalse(texture.getAsString().contains("/colored/"));
                    assertTrue(Files.isRegularFile(ROOT.resolve("textures/" + ResourceLocation.parse(texture.getAsString()).getPath() + ".png")));
                }
            }
        }
    }

    @Test
    void palettesReplaceGrayPixelsAndRetainScreenAnimationAndIdleLights() throws Exception {
        var source = source();
        var generated = sprites(source, resources(Map.of()));
        assertEquals(7 * source.palettes().size(), generated.size());
        for (var palette : source.palettes().entrySet()) {
            var colors = ImageIO.read(ROOT.resolve("textures/" + palette.getValue().getPath() + ".png").toFile());
            for (int layer = 0; layer < LAYERS.size(); layer++) {
                var mask = ImageIO.read(ROOT.resolve("textures/" + TEXTURES + LAYERS.get(layer) + ".png").toFile());
                try (var sprite = load(generated, palette.getKey(), LAYERS.get(layer))) {
                    assertPixels(sprite, mask, 0, colors.getRGB(layer, 0));
                    if (layer < 3) {
                        assertEquals(16, sprite.height());
                        assertEquals(22, sprite.getUniqueFrames().count());
                        assertEquals(5, sprite.metadata().getSection(AnimationMetadataSection.SERIALIZER).orElseThrow().getDefaultFrameTime());
                    } else {
                        assertNull(sprite.createTicker(), "Idle lights must not blink");
                    }
                }
            }
        }
    }

    @Test
    void workingLightsAlternateEntriesThreeFourAndFiveSixWithTheSameMask() throws Exception {
        var source = source();
        var generated = sprites(source, resources(Map.of()));
        for (var palette : source.palettes().entrySet()) {
            var colors = ImageIO.read(ROOT.resolve("textures/" + palette.getValue().getPath() + ".png").toFile());
            for (int layer = 3; layer <= 4; layer++) {
                var mask = ImageIO.read(ROOT.resolve("textures/" + TEXTURES + LAYERS.get(layer) + ".png").toFile());
                try (var sprite = load(generated, palette.getKey(), LAYERS.get(layer) + "_working")) {
                    assertEquals(mask.getWidth(), sprite.width());
                    assertEquals(mask.getHeight(), sprite.height());
                    assertEquals(2 * mask.getHeight(), sprite.getOriginalImage().getHeight());
                    assertEquals(2, sprite.getUniqueFrames().count());
                    assertPixels(sprite, mask, 0, colors.getRGB(layer, 0));
                    assertPixels(sprite, mask, mask.getHeight(), colors.getRGB(layer + 2, 0));
                    var animation = sprite.metadata().getSection(AnimationMetadataSection.SERIALIZER).orElseThrow();
                    assertTrue(animation.isInterpolatedFrames());
                    assertEquals(15, animation.getDefaultFrameTime());
                }
            }
        }
    }

    @Test
    void reloadingPalettesAndExplicitSpriteOverridesReplacePreviousColors() throws Exception {
        var source = source();
        var palettePath = ResourceLocation.parse("neoecoae:textures/" + TEXTURES + "colormap/normal.png");
        var colors = ImageIO.read(ROOT.resolve("textures/" + TEXTURES + "colormap/normal.png").toFile());
        int originalColor = colors.getRGB(0, 0);
        colors.setRGB(0, 0, 0xFFE13487);
        var mask = ImageIO.read(ROOT.resolve("textures/" + TEXTURES + LAYERS.getFirst() + ".png").toFile());
        try (var original = load(sprites(source, resources(Map.of())), "normal", LAYERS.getFirst());
             var reloaded = load(sprites(source, resources(Map.of(palettePath, imageResource(colors)))), "normal", LAYERS.getFirst())) {
            assertPixels(original, mask, 0, originalColor);
            assertPixels(reloaded, mask, 0, 0xFFE13487);
        }
        var replacement = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        replacement.setRGB(0, 0, 0xFF123456);
        var spritePath = ResourceLocation.parse("neoecoae:textures/" + TEXTURES + "colored/normal/" + LAYERS.getFirst() + ".png");
        try (var sprite = load(sprites(source, resources(Map.of(spritePath, imageResource(replacement)))), "normal", LAYERS.getFirst())) {
            assertEquals(2, sprite.width());
            assertEquals(1, sprite.height());
            assertEquals(0xFF563412, sprite.getOriginalImage().getPixelRGBA(0, 0));
        }
    }

    private static void assertPixels(SpriteContents sprite, BufferedImage mask, int offsetY, int color) {
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                int pixel = sprite.getOriginalImage().getPixelRGBA(x, y + offsetY);
                int alpha = (mask.getRGB(x, y) >>> 24) * (color >>> 24) / 255;
                assertEquals(alpha, pixel >>> 24);
                if (alpha != 0) {
                    int abgr = (alpha << 24) | (color & 0xFF00) | ((color >>> 16) & 0xFF) | ((color & 0xFF) << 16);
                    assertEquals(abgr, pixel, sprite.name().toString());
                }
            }
        }
    }

    private static WorkstationSpriteSource source() throws Exception {
        var atlas = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/minecraft/atlases/blocks.json")))
            .getAsJsonObject();
        return WorkstationSpriteSource.CODEC.codec().parse(JsonOps.INSTANCE, atlas.getAsJsonArray("sources").get(0)).getOrThrow();
    }

    private static Map<ResourceLocation, SpriteSource.SpriteSupplier> sprites(WorkstationSpriteSource source, ResourceManager manager) {
        Map<ResourceLocation, SpriteSource.SpriteSupplier> sprites = new HashMap<>();
        // Vanilla's block directory source initially includes these one-pixel palette strips.
        source.palettes().values().forEach(palette -> sprites.put(palette, loader -> loader.loadSprite(palette,
            manager.getResource(SpriteSource.TEXTURE_ID_CONVERTER.idToFile(palette)).orElseThrow())));
        source.run(manager, new SpriteSource.Output() {
            @Override public void add(ResourceLocation id, SpriteSource.SpriteSupplier supplier) { sprites.put(id, supplier); }
            @Override public void removeAll(Predicate<ResourceLocation> predicate) { sprites.keySet().removeIf(predicate); }
        });
        return sprites;
    }

    private static SpriteContents load(Map<ResourceLocation, SpriteSource.SpriteSupplier> sprites, String palette, String layer) {
        var id = ResourceLocation.parse("neoecoae:" + TEXTURES + "colored/" + palette + "/" + layer);
        assertTrue(sprites.containsKey(id), id.toString());
        var sprite = sprites.get(id).apply(LOADER);
        assertNotNull(sprite, id.toString());
        return sprite;
    }

    private static ResourceManager resources(Map<ResourceLocation, Resource> overrides) {
        var manager = mock(ResourceManager.class);
        var pack = mock(PackResources.class);
        when(manager.getResource(any())).thenAnswer(call -> {
            ResourceLocation id = call.getArgument(0);
            if (overrides.containsKey(id)) return Optional.of(overrides.get(id));
            Path file = Path.of("src/main/resources/assets").resolve(id.getNamespace()).resolve(id.getPath());
            if (!Files.isRegularFile(file)) return Optional.empty();
            return Optional.of(new Resource(pack, () -> Files.newInputStream(file), () -> {
                Path metadata = Path.of(file + ".mcmeta");
                return Files.isRegularFile(metadata) ? ResourceMetadata.fromJsonStream(Files.newInputStream(metadata)) : ResourceMetadata.EMPTY;
            }));
        });
        return manager;
    }

    private static Resource imageResource(BufferedImage image) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        byte[] data = bytes.toByteArray();
        return new Resource(mock(PackResources.class), () -> new ByteArrayInputStream(data));
    }

    private static JsonObject read(String path) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();
    }
}
