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

    @Test
    void allTextureFilesAreDecodablePngs() throws Exception {
        try (var paths = Files.walk(ROOT.resolve("textures"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".png")).toList()) {
                try (var input = Files.newInputStream(path)) {
                    assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10},
                        input.readNBytes(8), path.toString());
                }
                assertNotNull(ImageIO.read(path.toFile()), path.toString());
            }
        }
    }

    @Test
    void smartCoolingItemUsesTheExistingCoolingControllerModel() throws Exception {
        var generated = Path.of("src/generated/resources/assets/neoecoae");
        var item = JsonParser.parseString(Files.readString(generated.resolve(
            "items/large_integrated_working_station_smart_cooling_module.json"))).getAsJsonObject();
        var modelId = item.getAsJsonObject("model").get("model").getAsString();
        var model = JsonParser.parseString(Files.readString(generated.resolve(
            "models/" + modelId.substring("neoecoae:".length()) + ".json"))).getAsJsonObject();
        assertEquals("neoecoae:block/large_integrated_working_station_cooling_controller",
            model.get("parent").getAsString());
        assertTrue(Files.isRegularFile(ROOT.resolve(
            "models/block/large_integrated_working_station_cooling_controller.json")));
    }

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
    void smartControllerScreenIsEmissiveWithoutLightingTheCasing() throws Exception {
        for (boolean working : new boolean[] {false, true}) {
            var smart = read("models/" + LargeWorkstationModule.REGULAR.modelPath(working, true) + ".json");
            var elements = smart.getAsJsonArray("elements").asList().stream().map(e -> e.getAsJsonObject()).toList();
            var screen = elements.stream().filter(e -> e.get("name").getAsString().equals("back_screen"))
                .findFirst().orElseThrow();
            assertFalse(screen.get("shade").getAsBoolean());
            assertEquals(JsonParser.parseString("[3,3,32]"), screen.get("from"));
            assertEquals(JsonParser.parseString("[13,13,32]"), screen.get("to"));
            assertEquals(java.util.Set.of("south"), screen.getAsJsonObject("faces").keySet());
            var face = screen.getAsJsonObject("faces").getAsJsonObject("south");
            assertEquals("#cooling_screen", face.get("texture").getAsString());
            assertEquals(15, face.getAsJsonObject("neoforge_data").get("light_emission").getAsInt());
            assertFalse(face.getAsJsonObject("neoforge_data").get("ambient_occlusion").getAsBoolean());
            var bodies = elements.stream().filter(e -> e.get("name").getAsString().startsWith("body")).toList();
            assertFalse(bodies.isEmpty());
            for (var body : bodies) for (var entry : body.getAsJsonObject("faces").entrySet()) {
                assertFalse(entry.getValue().getAsJsonObject().has("neoforge_data"), "Casing must follow world light");
            }
        }
    }

    @Test
    void coolingScreenAnimationResolvesEveryFrameForBothWorkingStates() throws Exception {
        String texture = "block/working_station/cooling_controller/screen_on";
        for (boolean working : new boolean[] {false, true}) {
            var smart = read("models/" + LargeWorkstationModule.REGULAR.modelPath(working, true) + ".json");
            assertEquals("neoecoae:" + texture, smart.getAsJsonObject("textures").get("cooling_screen").getAsString());
        }
        var screen = ImageIO.read(ROOT.resolve("textures/" + texture + ".png").toFile());
        assertEquals(16, screen.getWidth());
        assertEquals(64, screen.getHeight());
        var animation = read("textures/" + texture + ".png.mcmeta").getAsJsonObject("animation");
        assertTrue(animation.get("frametime").getAsInt() > 0);
        assertEquals(JsonParser.parseString("[0,1,2,3,2,1]"), animation.get("frames"));
        for (var frame : animation.getAsJsonArray("frames")) {
            assertTrue(frame.getAsInt() >= 0 && frame.getAsInt() < screen.getHeight() / screen.getWidth());
        }
    }

    private static JsonObject read(String path) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();
    }
}
