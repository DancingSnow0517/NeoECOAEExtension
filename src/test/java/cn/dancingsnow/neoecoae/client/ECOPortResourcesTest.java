package cn.dancingsnow.neoecoae.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ECOPortResourcesTest {
    @Test
    void confirmationStyleAndReferencedBackgroundArePackaged() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/ae2/screens/eco_craft_confirm.json")) {
            assertNotNull(stream);
            var style = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
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
}
