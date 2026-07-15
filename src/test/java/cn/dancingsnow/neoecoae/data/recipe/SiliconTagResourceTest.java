package cn.dancingsnow.neoecoae.data.recipe;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SiliconTagResourceTest {
    @Test
    void siliconRecipeTagsHaveAe2Fallbacks() throws IOException {
        assertTagContains("forge", "dusts/silicon", "ae2:silicon");
        assertTagContains("forge", "plates/silicon", "ae2:printed_silicon");
        assertTagContains("c", "dusts/silicon", "ae2:silicon");
        assertTagContains("c", "plates/silicon", "ae2:printed_silicon");
    }

    private static void assertTagContains(String namespace, String path, String expectedItem) throws IOException {
        String resourcePath = "/data/" + namespace + "/tags/items/" + path + ".json";
        try (InputStream stream = SiliconTagResourceTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Missing item tag resource " + resourcePath);
            JsonArray values = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonArray("values");
            assertTrue(
                    values.asList().stream().anyMatch(value -> expectedItem.equals(value.getAsString())),
                    () -> resourcePath + " must contain " + expectedItem);
        }
    }
}
