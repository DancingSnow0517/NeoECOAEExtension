package cn.dancingsnow.neoecoae.gui.workstation;

import appeng.client.gui.style.StyleManager;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.layout.SlotGridLayout;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationPatternScreenStyleTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void ae2LoadsTheCompleteScreenIncludingSharedStylesAndTheNewScrollbar() throws Exception {
        var style = loadStyle();
        var bounds = new net.minecraft.client.renderer.Rect2i(0, 0, 228, 245);
        assertEquals(228, style.getBackground().getSrcWidth());
        assertEquals(245, style.getBackground().getSrcHeight());
        assertNotNull(style.getWidget("verticalToolbar"));
        assertEquals(22, style.getWidget("verticalToolbar").resolve(bounds).getX());
        assertEquals(2, style.getWidget("verticalToolbar").resolve(bounds).getY());
        assertNotNull(style.getWidget("patternScrollbar"));
        assertEquals(194, style.getWidget("patternScrollbar").resolve(bounds).getX());
        assertEquals(124, style.getWidget("patternScrollbar").getHeight());
        assertEquals(8, style.getWidget("patternScrollbar").getWidth());
        assertTrue(style.getSlots().containsKey("PLAYER_INVENTORY"));
        assertTrue(style.getSlots().containsKey("ENCODED_PATTERN"));
        assertEquals(28, style.getSlots().get("PLAYER_INVENTORY").resolve(bounds).getX());
        assertEquals(28, style.getSlots().get("PLAYER_HOTBAR").resolve(bounds).getX());
        assertTrue(style.getSlots().get("STORAGE").isHidden(), "Keep the compatibility anchor without drawing a return row");
        assertFalse(style.getText().containsKey("interface_stored_items"));
        assertEquals(23, style.getSlots().get("ENCODED_PATTERN").resolve(bounds).getY());
        assertEquals(206, style.getSlots().get("UPGRADE").resolve(bounds).getX());
        assertEquals(0, style.getSlots().get("UPGRADE").resolve(bounds).getY());
        assertEquals(206, style.getWidget("upgrades").resolve(bounds).getX(),
            "The composite panel repositions slots after the slot style has been applied");
        assertEquals(0, style.getWidget("upgrades").resolve(bounds).getY());
        var priority = style.getWidget("openPriority").resolve(bounds);
        assertEquals(184, priority.getX());
        assertEquals(-5, priority.getY());
        assertTrue(priority.getX() + 22 <= 20 + 188);
        assertTrue(priority.getY() + 22 < 22, "The raised priority tab must stay clear of the pattern grid");
        assertEquals(SlotGridLayout.VERTICAL, style.getSlots().get("UPGRADE").getGrid());
    }

    @Test
    void releasedDataEnergisticsPatchAcceptsTheStyleAndRetainsTheRightUpgradePanel() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.fish_dan_.data_energistics.client.screen.AePatternProviderSlotStylePatch");
        } catch (ClassNotFoundException absent) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Data Energistics is outside the 1.20.1 scope");
            return;
        }
        var patch = type.getMethod("inlineSingleUpgrade", String.class).invoke(null, "ae2:pattern_provider");
        var apply = type.getMethod("apply", ScreenStyle.class);
        var brokenStyle = loadStyle();
        brokenStyle.getSlots().remove("UPGRADE");
        brokenStyle.getSlots().remove("STORAGE");
        var failure = assertThrows(InvocationTargetException.class, () -> apply.invoke(patch, brokenStyle));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("Missing required slot style STORAGE"));

        var fixedStyle = loadStyle();
        var upgrades = fixedStyle.getSlots().get("UPGRADE");
        assertDoesNotThrow(() -> apply.invoke(patch, fixedStyle));
        assertSame(upgrades, fixedStyle.getSlots().get("UPGRADE"),
            "The addon must not replace our vertical panel with its inline slot at the second pattern row");
        assertTrue(fixedStyle.getSlots().get("STORAGE").isHidden());
        fixedStyle.validate();
    }

    private ScreenStyle loadStyle() throws Exception {
        ResourceManager resources = mock(ResourceManager.class);
        PackResources pack = mock(PackResources.class);
        when(pack.packId()).thenReturn("workstation-screen-regression");
        when(resources.getResource(any())).thenAnswer(call -> {
            ResourceLocation id = call.getArgument(0);
            String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
            var source = getClass().getResource(path);
            if (source == null) return Optional.empty();
            return Optional.of(new Resource(pack, source::openStream));
        });
        when(resources.openAsReader(any())).thenAnswer(call -> {
            ResourceLocation id = call.getArgument(0);
            var stream = getClass().getResourceAsStream("/assets/" + id.getNamespace() + "/" + id.getPath());
            if (stream == null) throw new FileNotFoundException(id.toString());
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        });
        StyleManager.initialize(resources);
        var style = StyleManager.loadStyleDoc("/screens/large_integrated_working_station_interface.json");
        verify(resources).getResource(new ResourceLocation("ae2:screens/large_integrated_working_station_interface.json"));
        return style;
    }

    @Test
    void sevenPatternRowsFitTheSuppliedTextureWithoutCoveringThePlayerInventory() throws Exception {
        try (var stream = getClass().getResourceAsStream(
            "/assets/ae2/textures/guis/large_integrated_working_station_interface_integrated.png")) {
            assertNotNull(stream);
            var texture = ImageIO.read(stream);
            assertEquals(256, texture.getWidth());
            assertEquals(256, texture.getHeight());
            assertEquals(126, WorkstationPatternScroll.VIEWPORT_HEIGHT);
            int bottom = 22 + WorkstationPatternScroll.VIEWPORT_HEIGHT - 1;
            assertEquals(0xFFF2F2F2, texture.getRGB(27, 22));
            assertEquals(0xFFF2F2F2, texture.getRGB(27, bottom));
            assertEquals(0xFFF2F2F2, texture.getRGB(188, bottom));
            assertNotEquals(0xFFF2F2F2, texture.getRGB(27, bottom + 1));
            assertEquals(0xFFF2F2F2, texture.getRGB(27, 160));
            assertTrue(bottom < 150, "Keep the inventory label and first player row outside the scissor");
        }
    }
}
