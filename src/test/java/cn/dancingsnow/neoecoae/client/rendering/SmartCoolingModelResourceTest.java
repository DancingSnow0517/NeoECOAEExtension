package cn.dancingsnow.neoecoae.client.rendering;

import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmartCoolingModelResourceTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/neoecoae");
    private static final String TEXTURES = "textures/block/working_station/controller_formed/";

    @Test
    void ordinaryModelsStaySeparateAndEverySmartThemeRetainsItsModuleTextures() throws Exception {
        for (boolean working : new boolean[] {false, true}) {
            var ordinary = read("models/" + LargeWorkstationModule.REGULAR.modelPath(working) + ".json");
            assertFalse(ordinary.getAsJsonObject("textures").has("cooling_screen"));
            var smart = read("models/" + LargeWorkstationModule.REGULAR.modelPath(working, true) + ".json");
            assertTrue(smart.getAsJsonObject("textures").has("cooling_screen"));
            for (var module : LargeWorkstationModule.values()) {
                if (module == LargeWorkstationModule.REGULAR) continue;
                var regularChild = read("models/" + module.modelPath(working) + ".json");
                var smartChild = read("models/" + module.modelPath(working, true) + ".json");
                assertEquals(regularChild.get("textures"), smartChild.get("textures"), module.toString());
                assertEquals("neoecoae:" + LargeWorkstationModule.REGULAR.modelPath(working, true),
                    smartChild.get("parent").getAsString());
            }
        }
    }

    @Test
    void lightLayersCoverOnlyTheTwoInputFacesAndRetainFullBrightness() throws Exception {
        for (boolean working : new boolean[] {false, true}) {
            var smart = read("models/" + LargeWorkstationModule.REGULAR.modelPath(working, true) + ".json");
            var glow = smart.getAsJsonArray("elements").asList().stream().map(e -> e.getAsJsonObject())
                .filter(e -> e.has("name") && e.get("name").getAsString().equals("input_hatch_light"))
                .findFirst().orElseThrow();
            assertFalse(glow.get("shade").getAsBoolean());
            assertEquals(JsonParser.parseString("[16,-16,16]"), glow.get("from"));
            assertEquals(JsonParser.parseString("[32,0,32]"), glow.get("to"));
            assertEquals(java.util.Set.of("east", "south"), glow.getAsJsonObject("faces").keySet());
            for (var face : glow.getAsJsonObject("faces").entrySet()) {
                var light = face.getValue().getAsJsonObject().getAsJsonObject("neoforge_data");
                assertEquals(15, light.get("block_light").getAsInt());
                assertEquals(15, light.get("sky_light").getAsInt());
                assertFalse(light.get("ambient_occlusion").getAsBoolean());
            }
        }
    }

    @Test
    void animationMasksFollowTheDisplayWhileLeavingTheDarkCasingUnlit() throws Exception {
        for (String kind : new String[] {"front", "side"}) {
            var base = ImageIO.read(ROOT.resolve(TEXTURES + "smart_cooling_module_" + kind + ".png").toFile());
            var glow = ImageIO.read(ROOT.resolve(TEXTURES + "smart_cooling_module_" + kind + "_light.png").toFile());
            assertEquals(base.getWidth(), glow.getWidth());
            assertEquals(base.getHeight(), glow.getHeight());
            int lit = 0, darkCasing = 0;
            for (int y = 0; y < base.getHeight(); y++) for (int x = 0; x < base.getWidth(); x++) {
                int pixel = glow.getRGB(x, y);
                if ((pixel >>> 24) != 0) {
                    lit++;
                    assertEquals(base.getRGB(x, y), pixel);
                }
                if ((base.getRGB(x, y) & 0xFFFFFF) == 0x393749) {
                    darkCasing++;
                    assertEquals(0, pixel >>> 24, "Casing must follow world light");
                }
                if (kind.equals("side") && x >= 4 && x <= 11 && y >= 4 && y <= 11) {
                    assertEquals(0, pixel >>> 24, "Metal fins must follow world light");
                }
            }
            assertTrue(lit > 0);
            assertTrue(darkCasing > 0);
            assertTrue(lit < base.getWidth() * base.getHeight() / 2);
        }
        var animation = read(TEXTURES + "smart_cooling_module_front_light.png.mcmeta").getAsJsonObject("animation");
        assertEquals(4, animation.get("frametime").getAsInt());
        assertFalse(animation.get("interpolate").getAsBoolean());
        var glow = ImageIO.read(ROOT.resolve(TEXTURES + "smart_cooling_module_front_light.png").toFile());
        assertEquals(16, glow.getWidth());
        assertEquals(12 * 16, glow.getHeight());
    }

    private static JsonObject read(String path) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();
    }
}
