package cn.dancingsnow.neoecoae.gui.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextElement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NeoUiJsonLoaderTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            Class.forName(UIElement.class.getName());
        }
    }

    @Test
    void buildsStaticTreeAndAppliesIdentityAndLayout() {
        JsonObject root = JsonParser.parseString("""
            {"type":"container","id":"root","class":["panel","test"],
             "layout":{"position":"absolute","left":3,"top":"25%","width":80,"height":40},
             "children":[{"type":"label","id":"title","text":"gui.title","translate":true},
                          {"type":"button","id":"start","text":"Start","action":"start"}]}
            """).getAsJsonObject();
        AtomicBoolean configured = new AtomicBoolean();
        UIElement result = NeoUiJsonLoader.load(root, new ResourceLocation("test.json"),
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
    void runtimeDisplayCanRevealAndHideAnInitiallyHiddenJsonPanel() {
        JsonObject document = JsonParser.parseString("""
            {"type":"element","id":"root","layout":{"width":100,"height":80},
             "children":[{"type":"element","id":"details",
                          "layout":{"position":"absolute","display":"none",
                                    "left":5,"top":7,"width":60,"height":40}}]}
            """).getAsJsonObject();
        UIElement root = NeoUiJsonLoader.load(document, new ResourceLocation("neoecoae", "hidden-panel.json"),
            NeoUiJsonContext.EMPTY);
        UIElement details = root.selectId("details").findFirst().orElseThrow();
        root.arrange();
        assertFalse(details.isDisplayed());
        assertFalse(details.isVisible());

        for (int visit = 0; visit < 2; visit++) {
            details.setDisplay(true);
            root.arrange();
            assertTrue(details.isDisplayed());
            assertTrue(details.isVisible());
            assertEquals(60, details.getSizeWidth());
            assertEquals(40, details.getSizeHeight());
            assertSame(details, root.hitTest(10, 12));

            details.setDisplay(false);
            root.arrange();
            assertFalse(details.isDisplayed());
            assertFalse(details.isVisible());
            assertSame(root, root.hitTest(10, 12));
        }
    }

    @Test
    void rejectsUnknownTypesAndUnregisteredCustomElements() {
        JsonObject unknown = JsonParser.parseString("{\"type\":\"eval\"}").getAsJsonObject();
        assertThrows(NeoUiJsonException.class, () -> NeoUiJsonLoader.load(unknown,
            new ResourceLocation("unknown.json"), NeoUiJsonContext.EMPTY));
        JsonObject custom = JsonParser.parseString("{\"type\":\"custom\",\"name\":\"missing\"}").getAsJsonObject();
        assertThrows(NeoUiJsonException.class, () -> NeoUiJsonLoader.load(custom,
            new ResourceLocation("custom.json"), NeoUiJsonContext.EMPTY));
    }
}
