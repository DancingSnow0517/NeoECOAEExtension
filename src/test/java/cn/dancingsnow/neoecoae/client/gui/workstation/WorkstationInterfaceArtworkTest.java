package cn.dancingsnow.neoecoae.client.gui.workstation;

import appeng.client.Point;
import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.client.gui.widget.WorkstationInterfaceButtonBar;
import cn.dancingsnow.neoecoae.client.gui.widget.WorkstationInterfaceUpgradesPanel;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationInterfaceArtworkTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.NeoECOAE");
        }
    }

    @Test
    void copiedArtworkIsByteIdenticalToThePinned121Release() throws Exception {
        var expected = Map.of(
            "states.png", "0996b0084c7bf37f65a97a745982ab681ebd86f142fade526f14c823c4727e55",
            "v19_2_18/small_scroller.png", "d06e44c1602297ecf6c700b52b01c3acface2f49e2b92a2570d1a6830d5ebc74");
        for (var asset : expected.entrySet()) {
            try (var stream = getClass().getResourceAsStream("/assets/neoecoae/textures/gui/vendor/ae2/" + asset.getKey())) {
                assertNotNull(stream);
                assertEquals(asset.getValue(), java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
            }
        }
        String manifestText;
        try (var stream = getClass().getResourceAsStream("/neoecoae/workstation-interface-artwork.json")) {
            assertNotNull(stream);
            manifestText = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var manifest = com.google.gson.JsonParser.parseString(manifestText).getAsJsonObject();
        for (var value : manifest.getAsJsonArray("assets")) {
            var entry = value.getAsJsonObject();
            String path = entry.get("target").getAsString().substring("src/main/resources".length());
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                assertEquals(entry.get("sha256").getAsString(), java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
            }
        }
    }

    @Test
    void modernButtonsUseDistinctNormalHoverAndFocusFramesAndThePriorityIcon() {
        assertEquals(176, WorkstationInterfaceArtwork.buttonBackground(false, false).getSrcX());
        assertEquals(194, WorkstationInterfaceArtwork.buttonBackground(false, true).getSrcX());
        assertEquals(212, WorkstationInterfaceArtwork.buttonBackground(true, true).getSrcX());
        assertEquals(18, WorkstationInterfaceArtwork.buttonBackground(true, false).getSrcWidth());
        assertEquals(20, WorkstationInterfaceArtwork.buttonBackground(true, false).getSrcHeight());
        assertEquals(144, WorkstationInterfaceArtwork.icon(Icon.WRENCH).getSrcX());
        assertEquals(64, WorkstationInterfaceArtwork.icon(Icon.WRENCH).getSrcY());
        for (boolean enabled : List.of(false, true)) {
            assertEquals(7, WorkstationInterfaceArtwork.scrollbar(enabled).getSrcWidth());
            assertEquals(15, WorkstationInterfaceArtwork.scrollbar(enabled).getSrcHeight());
        }
    }

    @Test
    void nativeUpgradeSlotsAndSideRailStayInsideTheSharedPanel() {
        List<Slot> slots = new ArrayList<>();
        var inventory = new appeng.util.inv.AppEngInternalInventory(5);
        for (int i = 0; i < 5; i++) slots.add(new appeng.menu.slot.AppEngSlot(inventory, i));
        var panel = new WorkstationInterfaceUpgradesPanel(slots);
        panel.setPosition(new Point(206, 0));
        panel.populateScreen(ignored -> {}, new Rect2i(100, 50, 228, 245), null);
        panel.updateBeforeRender();
        assertEquals(24, panel.getBounds().getWidth());
        assertEquals(100, panel.getBounds().getHeight());
        assertEquals(207, slots.get(0).x);
        assertEquals(6, slots.get(0).y);
        assertEquals(78, slots.get(4).y);
        assertTrue(slots.stream().allMatch(slot -> slot.x >= 4 && slot.x + 16 <= 224));
        assertEquals(228, panel.getBounds().getX() + panel.getBounds().getWidth());
        var rail = new WorkstationInterfaceButtonBar();
        rail.setPosition(new Point(22, 2));
        var first = Button.builder(Component.empty(), ignored -> {}).size(16, 16).build();
        var second = Button.builder(Component.empty(), ignored -> {}).size(16, 16).build();
        rail.add(first);
        rail.add(second);
        rail.populateScreen(ignored -> {}, new Rect2i(100, 50, 228, 245), null);
        rail.updateBeforeRender();
        assertEquals(22, second.getY() - first.getY(), "The 20px artwork needs the modern 6px button spacing");
        assertEquals(104, first.getX());
        assertEquals(54, first.getY());
        assertTrue(rail.getBounds().getX() >= 2);
        assertTrue(first.getX() + first.getWidth() < 100 + 27);
        var wing = WorkstationInterfaceArtwork.toolbarWing(4);
        assertEquals(72, wing.getSrcX());
        assertEquals(24, wing.getSrcWidth());
        assertEquals(98, wing.getSrcHeight());
        assertEquals(120, WorkstationInterfaceArtwork.toolbarWing(5).getSrcHeight());
    }

    @Test
    void attachedWingsContinueTheSourceFrameAndLeaveTheLowerSidesTransparent() throws Exception {
        try (var original = getClass().getResourceAsStream(
                "/assets/ae2/textures/guis/large_integrated_working_station_interface.png");
             var body = getClass().getResourceAsStream(
                "/assets/ae2/textures/guis/large_integrated_working_station_interface_integrated.png");
             var controls = getClass().getResourceAsStream(
                "/assets/ae2/textures/guis/large_integrated_working_station_interface_controls.png")) {
            var source = javax.imageio.ImageIO.read(original);
            var main = javax.imageio.ImageIO.read(body);
            var wings = javax.imageio.ImageIO.read(controls);
            int background = source.getRGB(10, 10), frame = source.getRGB(1, 30);
            assertEquals(0, main.getRGB(0, 130) >>> 24, "The lower left side is outside the stepped panel");
            assertEquals(0, main.getRGB(227, 110) >>> 24, "The upgrade wing ends above the player inventory");
            assertEquals(background, main.getRGB(206, 10), "Remove the body divider where the upgrade wing joins");
            assertEquals(frame, main.getRGB(206, 120), "Continue the body's right edge below the upgrade wing");
            for (int buttons : List.of(4, 5)) {
                int x = (buttons - 1) * 24, bottom = buttons * 22 + 6;
                assertEquals(frame, wings.getRGB(x + 1, 10));
                assertEquals(background, wings.getRGB(x + 21, 10), "The toolbar must join without a divider");
                assertEquals(0, wings.getRGB(x, bottom + 1) >>> 24);
                assertEquals(frame, wings.getRGB(x + 21, bottom + 3));
            }
        }
    }

    @Test
    void modernArtworkIsScopedToTheWorkstationCommunicationScreen() {
        try (var minecraft = mockStatic(Minecraft.class)) {
            var client = mock(Minecraft.class);
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            assertFalse(WorkstationInterfaceArtwork.active());
            client.screen = mock(LargeIntegratedWorkingStationPatternProviderScreen.class);
            assertTrue(WorkstationInterfaceArtwork.active());
            client.screen = mock(net.minecraft.client.gui.screens.TitleScreen.class);
            assertFalse(WorkstationInterfaceArtwork.active());
        }
    }
}
