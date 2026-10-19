package cn.dancingsnow.neoecoae.gui.workstation;

import appeng.client.gui.style.StyleManager;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
        assertEquals(188, style.getBackground().getSrcWidth());
        assertEquals(245, style.getBackground().getSrcHeight());
        assertNotNull(style.getWidget("verticalToolbar"));
        assertNotNull(style.getWidget("patternScrollbar"));
        assertEquals(70, style.getWidget("patternScrollbar").getHeight());
        assertEquals(7, style.getWidget("patternScrollbar").getWidth());
        assertTrue(style.getSlots().containsKey("PLAYER_INVENTORY"));
        assertTrue(style.getSlots().containsKey("ENCODED_PATTERN"));
        var bounds = new net.minecraft.client.renderer.Rect2i(0, 0, 188, 245);
        assertEquals(14, style.getSlots().get("PLAYER_INVENTORY").resolve(bounds).getX());
        assertEquals(14, style.getSlots().get("PLAYER_HOTBAR").resolve(bounds).getX());
        assertEquals(14, style.getSlots().get("STORAGE").resolve(bounds).getX());
        verify(resources).getResource(ResourceLocation.parse("ae2:screens/large_integrated_working_station_interface.json"));
    }
}
