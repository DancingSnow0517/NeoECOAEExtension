package cn.dancingsnow.neoecoae.gui.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.gui.storage.StorageHostActionUI;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildController;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.ValueWire;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextField;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.UiTestSupport;
import com.lowdragmc.lowdraglib.LDLib;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MultiblockBuildUITest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class); var registrate = mockStatic(NERegistrate.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(UIElement.class.getName());
            Class.forName(NETextures.class.getName());
        }
    }

    @Test
    void returnClosesTheFourRowWindowAndRetainsTheEditedLength() {
        try (var ldlib = mockStatic(LDLib.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            installFont(minecraft);
            Fixture fixture = fixture();
            var ui = MultiblockBuildUI.create(fixture.host(), fixture.player());
            assertFalse(ui.window().isDisplayed());
            assertEquals(4, ui.window().getChildren().stream().filter(UIElement::isDisplayed).count());
            assertEquals(176F, ui.window().getLayoutStyle().width);
            assertEquals(96F, ui.window().getLayoutStyle().height);

            clientClick(ui.openButton());
            assertTrue(ui.window().isDisplayed());
            TextField field = (TextField) node(ui.window(), "length");
            field.setValue("7");
            clientClick(node(ui.window(), "back"));
            assertFalse(ui.window().isDisplayed());
            clientClick(ui.openButton());
            assertEquals("7", field.getValue());
            verify(fixture.controller(), never()).autoBuild(any());
        }
    }

    @Test
    void editsStayLocalUntilTheServerAcceptsThemAndButtonsUseTheSameSettings() {
        try (var ldlib = mockStatic(LDLib.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            installFont(minecraft);
            Fixture fixture = fixture();
            var ui = MultiblockBuildUI.create(fixture.host(), fixture.player());
            TextField field = spy((TextField) node(ui.window(), "length"));
            field.setValue("7");
            assertEquals(3, fixture.length().get());

            doReturn(true).when(field).activeMenu();
            sendString(field, "7");
            assertEquals(7, fixture.length().get());
            sendString(field, "");
            sendString(field, "bad input");
            assertEquals(7, fixture.length().get());
            sendString(field, "999");
            assertEquals(16, fixture.length().get());
            serverClick(node(ui.window(), "minus-ten"));
            assertEquals(6, fixture.length().get());
            serverClick(node(ui.window(), "mirror"));
            assertTrue(fixture.mirrored().get());
            doNothing().when(fixture.controller()).autoBuild(fixture.player());
            serverClick(node(ui.window(), "start"));
            verify(fixture.controller()).autoBuild(fixture.player());
        }
    }

    @Test
    void mirrorStateAndEditableStateFollowServerUpdates() {
        try (var ldlib = mockStatic(LDLib.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            installFont(minecraft);
            Fixture fixture = fixture();
            var ui = MultiblockBuildUI.create(fixture.host(), fixture.player());
            Button mirror = (Button) node(ui.window(), "mirror");
            assertSame(NETextures.SWITCH_OFF, mirror.getButtonStyle().baseTexture());
            UIElement mirrored = mirror.getChildren().stream().filter(BindableValue.class::isInstance).findFirst().orElseThrow();
            receiveBoolean(mirrored, true);
            assertSame(NETextures.SWITCH_ON, mirror.getButtonStyle().baseTexture());

            UIElement editable = ui.window().getChildren().stream().filter(BindableValue.class::isInstance).findFirst().orElseThrow();
            receiveBoolean(editable, false);
            assertFalse(node(ui.window(), "length").isActive());
            assertFalse(node(ui.window(), "plus-one").isActive());
            assertFalse(mirror.isActive());
            assertFalse(node(ui.window(), "start").isActive());
            assertTrue(node(ui.window(), "back").isActive());
            receiveBoolean(editable, true);
            assertTrue(node(ui.window(), "start").isActive());
        }
    }

    @Test
    void builderReusesThePriorityArtworkAndTheAE2FieldAndButtons() {
        try (var ldlib = mockStatic(LDLib.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            installFont(minecraft);
            Fixture fixture = fixture();
            var ui = MultiblockBuildUI.create(fixture.host(), fixture.player());
            SpriteTexture background = (SpriteTexture) ui.window().getStyle().backgroundTexture();
            assertEquals("neoecoae:textures/guis/priority.png", background.getImageLocation().toString());
            GUIContext graphics = mock(GUIContext.class);
            TextField field = (TextField) node(ui.window(), "length");
            field.drawBackgroundTexture(graphics);
            verify(graphics).drawTexture(eq(NETextures.PRIORITY_TEXT_FIELD), anyFloat(), anyFloat(), anyFloat(), anyFloat());
            node(ui.window(), "plus-one").drawBackgroundAdditional(graphics);
            verify(graphics).drawTexture(eq(NETextures.AE2_BUTTON), anyFloat(), anyFloat(), anyFloat(), anyFloat());
        }
    }

    @Test
    void serverMenuConstructionDoesNotNeedAClientFont() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(false);
            Fixture fixture = fixture();
            var ui = MultiblockBuildUI.create(fixture.host(), fixture.player());
            assertEquals("3", ((TextField) node(ui.window(), "length")).getValue());
        }
    }

    @Test
    void serverButtonsBecomeUsableWhenAnotherPlayersBuildFinishes() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(false);
            Fixture fixture = fixture();
            when(fixture.host().isBuildInProgress()).thenReturn(true);
            var ui = MultiblockBuildUI.create(fixture.host(), fixture.player());
            UIElement editable = ui.window().getChildren().stream().filter(BindableValue.class::isInstance).findFirst().orElseThrow();
            assertFalse(node(ui.window(), "start").isActive());
            when(fixture.host().isBuildInProgress()).thenReturn(false);
            editable.detectAndSendChanges();
            assertTrue(node(ui.window(), "start").isActive());
            when(fixture.host().isFormed()).thenReturn(true);
            editable.detectAndSendChanges();
            assertFalse(node(ui.window(), "start").isActive());
        }
    }

    @Test
    void storageToolbarSwitchesBetweenPriorityAndBuilderWithoutOverlappingThem() {
        try (var ldlib = mockStatic(LDLib.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            installFont(minecraft);
            Fixture fixture = fixture();
            var builder = MultiblockBuildUI.create(fixture.host(), fixture.player());
            var actions = StorageHostActionUI.create(new StorageHostActionUI.Config(fixture.player(), () -> 0,
                ignored -> {}, ignored -> {}));
            UIElement root = new UIElement();
            actions.addTo(root, builder);
            clientClick(builder.openButton());
            assertTrue(builder.window().isDisplayed());
            assertFalse(actions.priorityWindow().isDisplayed());
            clientClick(root.selectId("storage-priority-open").findFirst().orElseThrow());
            assertFalse(builder.window().isDisplayed());
            assertTrue(actions.priorityWindow().isDisplayed());
            clientClick(builder.openButton());
            assertTrue(builder.window().isDisplayed());
            assertFalse(actions.priorityWindow().isDisplayed());
        }
    }

    private static void installFont(MockedStatic<Minecraft> minecraft) {
        Minecraft client = mock(Minecraft.class);
        Font font = mock(Font.class);
        UiTestSupport.set(client, "font", font);
        minecraft.when(Minecraft::getInstance).thenReturn(client);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
    }

    private record Fixture(MultiBlockBuildController.Host host, Player player, MultiBlockBuildController controller,
                           AtomicInteger length, AtomicBoolean mirrored) {}

    private static Fixture fixture() {
        var host = mock(MultiBlockBuildController.Host.class);
        Player player = mock(Player.class);
        AtomicInteger length = new AtomicInteger(3);
        AtomicBoolean mirrored = new AtomicBoolean();
        when(host.getMinBuildLength()).thenReturn(1);
        when(host.getMaxBuildLength()).thenReturn(16);
        when(host.getSelectedBuildLength()).thenAnswer(call -> length.get());
        doAnswer(call -> { length.set(call.getArgument(0)); return null; }).when(host).setSelectedBuildLength(anyInt());
        when(host.isMirrorBuild()).thenAnswer(call -> mirrored.get());
        doAnswer(call -> { mirrored.set(call.getArgument(0)); return null; }).when(host).setMirrorBuild(anyBoolean());
        when(host.canPlayerInteract(player)).thenReturn(true);
        when(host.getBuildDefinition()).thenReturn(mock(MultiBlockDefinition.class));
        var controller = spy(new MultiBlockBuildController(host));
        when(host.getBuildController()).thenReturn(controller);
        return new Fixture(host, player, controller, length, mirrored);
    }

    private static UIElement node(UIElement root, String suffix) {
        return root.selectId("multiblock-build-" + suffix).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static void clientClick(UIElement element) {
        ((Consumer<UIEvent>) UiTestSupport.get(element, "click")).accept(new UIEvent(UIEvents.CLICK, element, 0, 0, 0));
    }

    private static void serverClick(UIElement element) {
        UiTestSupport.serverClick(element, new UIEvent(UIEvents.CLICK, element, 0, 0, 0));
    }

    private static void sendString(TextField field, String value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(0);
            ValueWire.write(buffer, String.class, value);
            field.handleClientAction(700, buffer);
        } finally {
            buffer.release();
        }
    }

    private static void receiveBoolean(UIElement element, boolean value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(0);
            ValueWire.write(buffer, Boolean.class, value);
            element.readUpdateInfo(700, buffer);
        } finally {
            buffer.release();
        }
    }
}
