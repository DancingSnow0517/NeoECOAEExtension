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
                boolean wire = baked == 0xFF696D88;
                if (wire) assertEquals(baked, atlas.getRGB(x, 34 + y),
                    "The neutral circuit wires must share the baked origin; chip artwork may be replaced");
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
    }

    @Test
    void artistAtlasPlacesDataBeforeOtherWithoutChangingTheExistingFamilies() {
        assertEquals(75, WorkstationTextures.module(LargeWorkstationModule.DATA_ENERGISTICS).spritePosition.getX());
        assertEquals(140, WorkstationTextures.circuit(LargeWorkstationModule.DATA_ENERGISTICS).spritePosition.getX());
        assertEquals(90, WorkstationTextures.module(LargeWorkstationModule.OTHER).spritePosition.getX());
        assertEquals(168, WorkstationTextures.circuit(LargeWorkstationModule.OTHER).spritePosition.getX());
        assertEquals(60, WorkstationTextures.module(LargeWorkstationModule.LIGHTNING_TECH).spritePosition.getX());
        assertEquals(105, WorkstationTextures.module(LargeWorkstationModule.APPLIED_GENERATORS).spritePosition.getX());
        assertEquals(196, WorkstationTextures.circuit(LargeWorkstationModule.APPLIED_GENERATORS).spritePosition.getX());
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
        for (String family : new String[] {"light_compat/appgen", "light_compat/appgen_on", "screen_compat/appgen_on"}) {
            String path = root + "block/working_station/controller_formed/controller_formed_" + family;
            try (var actualStream = getClass().getResourceAsStream(path + ".png");
                 var expectedStream = getClass().getResourceAsStream(path.replace("appgen", "other") + ".png")) {
                assertNotNull(actualStream);
                assertNotNull(expectedStream);
                var actual = ImageIO.read(actualStream);
                var expected = ImageIO.read(expectedStream);
                assertEquals(expected.getWidth(), actual.getWidth());
                assertEquals(expected.getHeight(), actual.getHeight());
                boolean recolored = false;
                for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++) {
                    assertEquals(expected.getRGB(x, y) >>> 24, actual.getRGB(x, y) >>> 24);
                    recolored |= actual.getRGB(x, y) != expected.getRGB(x, y);
                }
                assertTrue(recolored, family);
            }
            if (family.endsWith("_on")) assertNotNull(getClass().getResource(path + ".png.mcmeta"));
        }
    }

    @Test
    void workstationControlSlotsAndLightBarsAreMirrored() throws IOException {
        BufferedImage background = read("large_integrated_working_station.png");
        for (int y = 89; y <= 114; y++) {
            for (int x = 0; x < 15; x++) {
                assertEquals(background.getRGB(102 + x, y), background.getRGB(160 - x, y),
                    "The cooling control slot must mirror the overclock slot at " + x + "," + y);
            }
        }
        for (int y : new int[] {92, 93, 110, 111}) {
            for (int x = 0; x < 3; x++) {
                assertEquals(background.getRGB(99 + x, y), background.getRGB(163 - x, y),
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
                            String key = "facing=" + facing + ",formed=" + formed + ",module="
                                + module.getSerializedName() + ",working=" + working;
                            assertTrue(variants.has(key), "Missing external-model variant: " + key);
                            String model = variants.getAsJsonObject(key).get("model").getAsString();
                            assertEquals("neoecoae:" + (formed ? module.modelPath(working)
                                : "block/integrated_working_station" + (working ? "_on" : "")), model);
                            assertNotNull(getClass().getResource("/assets/neoecoae/models/" + model.substring(9) + ".json"));
                        }
                    }
                }
            }
        }
    }

    private BufferedImage read(String file) throws IOException {
        try (var stream = getClass().getResourceAsStream("/assets/neoecoae/textures/guis/workstation/" + file)) {
            assertNotNull(stream);
            return ImageIO.read(stream);
        }
    }
}
