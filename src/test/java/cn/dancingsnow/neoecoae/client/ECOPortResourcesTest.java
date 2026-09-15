package cn.dancingsnow.neoecoae.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ECOPortResourcesTest {
    private static final String GUI_TEXTURES = "/assets/neoecoae/textures/gui/";

    @Test
    void confirmationStyleAndReferencedBackgroundArePackaged() throws Exception {
        String styleResource = "/assets/ae2/screens/eco_planner_report.json";
        assertFalse(styleResource.contains("craft_confirm.json"));
        try (var stream = getClass().getResourceAsStream(styleResource)) {
            assertNotNull(stream);
            var style = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(style.getAsJsonObject("images").has("cycleItem"));
            assertTrue(style.getAsJsonObject("images").has("cycleItemHovered"));
            String texture = style.getAsJsonObject("background").get("texture").getAsString();
            String[] parts = texture.split(":", 2);
            try (var image = getClass().getResourceAsStream("/assets/" + parts[0] + "/" + parts[1])) {
                assertNotNull(image);
                var bitmap = javax.imageio.ImageIO.read(image);
                assertEquals(
                        style.getAsJsonObject("background").get("textureWidth").getAsInt(), bitmap.getWidth());
                assertEquals(
                        style.getAsJsonObject("background").get("textureHeight").getAsInt(), bitmap.getHeight());
            }
        }
    }

    @Test
    void highVersionHostAndButtonTexturesArePackagedAtReferenceDimensions() throws Exception {
        assertTexture("background.png", 16, 16);
        assertTexture("ae2_121/eco_button.png", 200, 20);
        assertTexture("ae2_121/eco_button_highlighted.png", 200, 20);
        assertTexture("ae2_121/eco_button_disabled.png", 200, 20);
        assertTexture("ae2_121/eco_states.png", 256, 256);
        assertTexture("slot.png", 18, 18);
        assertTexture("eco_button_slot_up.png", 23, 30);
        assertTexture("eco_button_slot_middle.png", 23, 24);
        assertTexture("eco_button_slot_down.png", 23, 27);
        assertTexture("eco_mirrored_button_slot_up.png", 23, 30);
        assertTexture("eco_mirrored_button_slot_middle.png", 23, 24);
        assertTexture("eco_mirrored_button_slot_down.png", 23, 27);
        assertTexture("eco_extra_panels.png", 80, 80);
        assertTexture("eco_large_integrated_working_station.png", 256, 256);
        assertTexture("eco_nbtbench.png", 256, 256);
    }

    private void assertTexture(String name, int width, int height) throws Exception {
        try (var image = getClass().getResourceAsStream(GUI_TEXTURES + name)) {
            assertNotNull(image, name);
            var bitmap = javax.imageio.ImageIO.read(image);
            assertNotNull(bitmap, name);
            assertEquals(width, bitmap.getWidth(), name);
            assertEquals(height, bitmap.getHeight(), name);
        }
    }
}
