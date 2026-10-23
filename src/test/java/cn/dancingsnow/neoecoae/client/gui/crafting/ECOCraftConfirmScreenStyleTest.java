package cn.dancingsnow.neoecoae.client.gui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOCraftConfirmScreenStyleTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void reportStyleLoadsAllRequiredImagesAndWidgetsThroughAe2() throws Exception {
        ScreenStyle style = loadStyle();
        var screen = mock(AEBaseScreen.class);
        when(screen.getStyle()).thenReturn(style);
        assertDoesNotThrow(() -> new ECOCycleItemListRenderer(screen, 237, 27));
        for (String image : List.of("cycleItem", "cycleItemHovered")) assertNotNull(style.getImage(image));
        for (String widget : List.of("verticalToolbar", "scrollbar", "cycleScrollbar", "selectCpu",
                "start", "cancel", "bookmarkMissing")) assertNotNull(style.getWidget(widget));
        for (String text : List.of("dialog_title", "cycle_status", "cpu_status", "complex_tree_hint"))
            assertTrue(style.getText().containsKey(text), "Missing report text: " + text);
        assertEquals(330, style.getBackground().getSrcWidth());
        assertEquals(260, style.getBackground().getSrcHeight());
        var bounds = new Rect2i(0, 0, 330, 260);
        assertEquals(312, style.getWidget("cycleScrollbar").resolve(bounds).getX());
        assertEquals(160, style.getWidget("cycleScrollbar").getHeight());
        assertDoesNotThrow(style::validate);
    }

    @Test
    void cycleImagesStayInsideThePackagedReportTexture() throws Exception {
        ScreenStyle style = loadStyle();
        try (var input = getClass().getResourceAsStream(
                "/assets/neoecoae/textures/guis/eco_craftingreport_cycle.png")) {
            assertNotNull(input);
            var image = ImageIO.read(input);
            assertEquals(384, image.getWidth());
            assertEquals(384, image.getHeight());
            assertEquals(67, style.getImage("cycleItem").getSrcWidth());
            assertEquals(22, style.getImage("cycleItemHovered").getSrcHeight());
        }
    }

    private ScreenStyle loadStyle() throws Exception {
        ResourceManager resources = mock(ResourceManager.class);
        PackResources pack = mock(PackResources.class);
        when(pack.packId()).thenReturn("eco-crafting-report-regression");
        when(resources.getResource(any())).thenAnswer(call -> {
            ResourceLocation id = call.getArgument(0);
            var source = getClass().getResource("/assets/" + id.getNamespace() + "/" + id.getPath());
            return source == null ? Optional.empty() : Optional.of(new Resource(pack, source::openStream));
        });
        when(resources.openAsReader(any())).thenAnswer(call -> {
            ResourceLocation id = call.getArgument(0);
            var input = getClass().getResourceAsStream("/assets/" + id.getNamespace() + "/" + id.getPath());
            if (input == null) throw new FileNotFoundException(id.toString());
            return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        });
        StyleManager.initialize(resources);
        return StyleManager.loadStyleDoc(ECOCraftConfirmScreenIntegration.REPORT_STYLE_PATH);
    }
}
