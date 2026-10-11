package cn.dancingsnow.neoecoae.gui.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NeoUiJsonLoaderTest {
    @Test
    void buildsStaticTreeAndAppliesIdentityAndLayout() {
        JsonObject root = JsonParser.parseString("""
            {"type":"container","id":"root","class":["panel","test"],
             "layout":{"position":"absolute","left":3,"top":"25%","width":80,"height":40},
             "children":[{"type":"label","id":"title","text":"gui.title","translate":true},
                          {"type":"button","id":"start","text":"Start","action":"start"}]}
            """).getAsJsonObject();
        AtomicBoolean configured = new AtomicBoolean();
        UIElement result = NeoUiJsonLoader.load(root, ResourceLocation.withDefaultNamespace("test.json"),
            new NeoUiJsonContext() {
                @Override
                public void configure(JsonObject node, UIElement element) {
                    if ("start".equals(node.get("id").getAsString())) {
                        configured.set(true);
                    }
                }
            });

        assertEquals("root", result.getId());
        assertTrue(result.hasClass("panel"));
        assertEquals(2, result.getChildren().size());
        assertTrue(result.selectId("title").anyMatch(TextElement.class::isInstance));
        assertTrue(result.selectId("start").anyMatch(Button.class::isInstance));
        assertTrue(configured.get());
    }

    @Test
    void rejectsUnknownTypesAndUnregisteredCustomElements() {
        JsonObject unknown = JsonParser.parseString("{\"type\":\"eval\"}").getAsJsonObject();
        assertThrows(NeoUiJsonException.class, () -> NeoUiJsonLoader.load(unknown,
            ResourceLocation.withDefaultNamespace("unknown.json"), NeoUiJsonContext.EMPTY));
        JsonObject custom = JsonParser.parseString("{\"type\":\"custom\",\"name\":\"missing\"}").getAsJsonObject();
        assertThrows(NeoUiJsonException.class, () -> NeoUiJsonLoader.load(custom,
            ResourceLocation.withDefaultNamespace("custom.json"), NeoUiJsonContext.EMPTY));
    }
}
