package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationArtworkTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(WorkstationTextures.class.getName());
        }
    }

    @Test
    void moduleCircuitsExactlyCoverTheBakedWiresWithoutMovingTheChipOrShadow() throws IOException {
        BufferedImage background = read("large_integrated_working_station.png");
        BufferedImage atlas = read("integrated_working_station_widget.png");
        int coveredWires = 0;
        for (int y = 0; y < 28; y++) {
            for (int x = 0; x < 28; x++) {
                int baked = background.getRGB(WorkstationTextures.CIRCUIT_X + x, WorkstationTextures.CIRCUIT_Y + y);
                // The chip now uses the wire color too; only the surrounding wires are recolored.
                boolean chip = x >= 10 && x < 26 && y >= 5 && y < 21;
                boolean wire = !chip && baked == 0xFF696D88;
                if (wire) assertEquals(baked, atlas.getRGB(x, 34 + y),
                    "The neutral circuit wires must share the baked origin");
                if (wire) coveredWires++;
                for (LargeWorkstationModule module : LargeWorkstationModule.values()) {
                    if (module == LargeWorkstationModule.REGULAR) continue;
                    var sprite = WorkstationTextures.circuit(module);
                    int pixel = atlas.getRGB(sprite.spritePosition.getX() + x, sprite.spritePosition.getY() + y);
                    assertEquals(wire, (pixel >>> 24) != 0,
                        module + " must recolor only the existing circuit at " + x + "," + y);
                }
            }
        }
        assertTrue(coveredWires > 0);
        int chipDetails = 0;
        for (int y = 42; y < 58; y++) for (int x = 227; x < 243; x++) {
            if (background.getRGB(x, y) == 0xFF696D88) chipDetails++;
            for (LargeWorkstationModule module : LargeWorkstationModule.values()) {
                if (module == LargeWorkstationModule.REGULAR) continue;
                var sprite = WorkstationTextures.circuit(module);
                assertEquals(0, atlas.getRGB(sprite.spritePosition.getX() + x - WorkstationTextures.CIRCUIT_X,
                    sprite.spritePosition.getY() + y - WorkstationTextures.CIRCUIT_Y) >>> 24,
                    "Module recoloring must preserve the baked chip details");
            }
        }
        assertTrue(chipDetails > 0, "The module slot must retain the artist's chip texture");
    }

    @Test
    void artistAtlasPlacesAppliedGeneratorsBeforeOtherWithoutChangingTheExistingFamilies() {
        assertEquals(75, WorkstationTextures.module(LargeWorkstationModule.DATA_ENERGISTICS).spritePosition.getX());
        assertEquals(140, WorkstationTextures.circuit(LargeWorkstationModule.DATA_ENERGISTICS).spritePosition.getX());
        assertEquals(105, WorkstationTextures.module(LargeWorkstationModule.OTHER).spritePosition.getX());
        assertEquals(196, WorkstationTextures.circuit(LargeWorkstationModule.OTHER).spritePosition.getX());
        assertEquals(60, WorkstationTextures.module(LargeWorkstationModule.LIGHTNING_TECH).spritePosition.getX());
        assertEquals(90, WorkstationTextures.module(LargeWorkstationModule.APPLIED_GENERATORS).spritePosition.getX());
        assertEquals(168, WorkstationTextures.circuit(LargeWorkstationModule.APPLIED_GENERATORS).spritePosition.getX());
    }

    @Test
    void dataEnergisticsCardUsesTheSingleStaticArtistImage() throws IOException {
        String path = "/assets/neoecoae/textures/item/integrated_working_station_modules/data_energistics.png";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream);
            var card = ImageIO.read(stream);
            assertEquals(16, card.getWidth());
            assertEquals(16, card.getHeight());
        }
        assertNull(getClass().getResource(path + ".mcmeta"), "The obsolete second animation frame must not be referenced");
    }

    @Test
    void everyCompatibilityModelResolvesItsTextureAndAnimation() throws IOException {
        for (LargeWorkstationModule module : LargeWorkstationModule.values()) {
            for (boolean working : new boolean[] {false, true}) {
                String model = "/assets/neoecoae/models/" + module.modelPath(working) + ".json";
                try (var stream = getClass().getResourceAsStream(model)) {
                    assertNotNull(stream, model);
                    var textures = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("textures");
                    for (var entry : textures.entrySet()) {
                        String[] id = entry.getValue().getAsString().split(":", 2);
                        String path = texturePath(id[0], id[1]);
                        try (var texture = getClass().getResourceAsStream(path)) {
                            assertNotNull(texture, model + " references missing texture " + path);
                            var image = ImageIO.read(texture);
                            if (image.getHeight() > image.getWidth()) {
                                assertNotNull(getClass().getResource(path + ".mcmeta"), path + " needs animation metadata");
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void appliedGeneratorsPreservesSuppliedCardAndHasMatchingControllerTextureMasks() throws Exception {
        String root = "/assets/neoecoae/textures/";
        try (var stream = getClass().getResourceAsStream(root + "item/integrated_working_station_modules/appgen.png")) {
            assertNotNull(stream);
            byte[] original = stream.readAllBytes();
            assertEquals("4b80680112dcd738453a46bc0bc2e44a96421a02877bd86a4cfe0e340741cf24",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original)));
            var card = ImageIO.read(new java.io.ByteArrayInputStream(original));
            assertEquals(16, card.getWidth());
            assertEquals(16, card.getHeight());
        }
        assertNull(getClass().getResource(root + "item/integrated_working_station_modules/appgen.png.mcmeta"));
        String paletteRoot = root + "block/working_station/controller_formed/colormap/";
        try (var appgenStream = getClass().getResourceAsStream(paletteRoot + "appgen.png");
             var normalStream = getClass().getResourceAsStream(paletteRoot + "normal.png")) {
            assertNotNull(appgenStream);
            assertNotNull(normalStream);
            var appgen = ImageIO.read(appgenStream);
            var normal = ImageIO.read(normalStream);
            assertEquals(1, appgen.getHeight());
            assertEquals(normal.getWidth(), appgen.getWidth());
            assertTrue(appgen.getWidth() >= 7);
            boolean recolored = false;
            for (int x = 0; x < 7; x++) {
                assertEquals(normal.getRGB(x, 0) >>> 24, appgen.getRGB(x, 0) >>> 24);
                recolored |= normal.getRGB(x, 0) != appgen.getRGB(x, 0);
            }
            assertTrue(recolored, "Applied Generators must have its own controller colors");
        }
        for (boolean working : new boolean[] {false, true}) {
            try (var stream = getClass().getResourceAsStream("/assets/neoecoae/models/"
                    + LargeWorkstationModule.APPLIED_GENERATORS.modelPath(working) + ".json")) {
                assertNotNull(stream);
                var textures = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("textures");
                assertFalse(textures.isEmpty());
                for (var entry : textures.entrySet()) {
                    assertTrue(entry.getValue().getAsString().contains("/colored/appgen/"));
                    assertNotNull(getClass().getResource(texturePath("neoecoae", entry.getValue().getAsString().substring(9))));
                }
            }
        }
    }

    @Test
    void workstationControlSlotsAndLightBarsAreMirrored() throws IOException {
        BufferedImage background = read("large_integrated_working_station.png");
        for (int y = 89; y <= 114; y++) {
            for (int x = 0; x < 15; x++) {
                assertEquals(background.getRGB(102 + x, y), background.getRGB(134 - x, y),
                    "The cooling control slot must mirror the overclock slot at " + x + "," + y);
            }
        }
        for (int y : new int[] {92, 93, 110, 111}) {
            for (int x = 0; x < 3; x++) {
                assertEquals(background.getRGB(99 + x, y), background.getRGB(119 - x, y),
                    "The light guide bar must mirror at " + x + "," + y);
            }
        }
    }

    @Test
    void everyCardHasExternalModelsForAllFacingsAndBothWorkingStates() throws IOException {
        try (var stream = getClass().getResourceAsStream("/assets/neoecoae/blockstates/integrated_working_station.json")) {
            assertNotNull(stream);
            var variants = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("variants");
            for (String facing : new String[] {"north", "east", "south", "west"}) {
                for (boolean formed : new boolean[] {false, true}) {
                    for (boolean working : new boolean[] {false, true}) {
                        for (LargeWorkstationModule module : LargeWorkstationModule.values()) {
                            for (boolean smartCooling : new boolean[] {false, true}) {
                                String key = "facing=" + facing + ",formed=" + formed + ",module="
                                    + module.getSerializedName() + ",smart_cooling=" + smartCooling + ",working=" + working;
                                assertTrue(variants.has(key), "Missing external-model variant: " + key);
                                String model = variants.getAsJsonObject(key).get("model").getAsString();
                                assertEquals("neoecoae:" + (formed ? module.modelPath(working, smartCooling)
                                    : "block/integrated_working_station" + (working ? "_on" : "")), model);
                                assertNotNull(getClass().getResource("/assets/neoecoae/models/" + model.substring(9) + ".json"));
                            }
                        }
                    }
                }
            }
        }
    }

    /** Generated atlas sprites must resolve to a registered palette and a real shared mask. */
    private String texturePath(String namespace, String texture) throws IOException {
        String prefix = "block/working_station/controller_formed/colored/";
        if (namespace.equals("neoecoae") && texture.startsWith(prefix)) {
            String[] parts = texture.substring(prefix.length()).split("/", 2);
            try (var atlas = getClass().getResourceAsStream("/assets/minecraft/atlases/blocks.json")) {
                assertNotNull(atlas);
                var source = JsonParser.parseReader(new InputStreamReader(atlas, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("sources").asList().stream().map(e -> e.getAsJsonObject())
                    .filter(e -> e.get("type").getAsString().equals("neoecoae:workstation_palette"))
                    .findFirst().orElseThrow();
                var palette = source.getAsJsonObject("palettes").get(parts[0]);
                assertNotNull(palette, texture);
                String[] id = palette.getAsString().split(":", 2);
                assertNotNull(getClass().getResource("/assets/" + id[0] + "/textures/" + id[1] + ".png"));
            }
            texture = "block/working_station/controller_formed/" + parts[1].replaceFirst("_working$", "");
        }
        return "/assets/" + namespace + "/textures/" + texture + ".png";
    }

    private BufferedImage read(String file) throws IOException {
        try (var stream = getClass().getResourceAsStream("/assets/neoecoae/textures/guis/workstation/" + file)) {
            assertNotNull(stream);
            return ImageIO.read(stream);
        }
    }
}
