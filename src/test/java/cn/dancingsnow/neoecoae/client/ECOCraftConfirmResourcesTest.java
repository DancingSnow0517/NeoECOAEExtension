package cn.dancingsnow.neoecoae.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ECOCraftConfirmResourcesTest {
    @Test
    void originalReportTextureMatchesItsAe2StyleDimensions() throws Exception {
        var loader = getClass().getClassLoader();
        try (var styleData = loader.getResourceAsStream("assets/ae2/screens/eco_craft_confirm.json");
                var imageData =
                        loader.getResourceAsStream("assets/neoecoae/textures/guis/eco_craftingreport_cycle.png")) {
            assertNotNull(styleData);
            assertNotNull(imageData);
            var style = JsonParser.parseReader(new InputStreamReader(styleData, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var background = style.getAsJsonObject("background");
            var image = ImageIO.read(imageData);
            assertEquals(background.get("textureWidth").getAsInt(), image.getWidth());
            assertEquals(background.get("textureHeight").getAsInt(), image.getHeight());
            assertEquals(
                    "neoecoae:textures/guis/eco_craftingreport_cycle.png",
                    background.get("texture").getAsString());
            assertTrue(style.getAsJsonObject("widgets").has("start"));
            assertTrue(style.getAsJsonObject("widgets").has("cancel"));
            assertTrue(style.getAsJsonObject("widgets").has("cycleScrollbar"));
        }
    }
}
