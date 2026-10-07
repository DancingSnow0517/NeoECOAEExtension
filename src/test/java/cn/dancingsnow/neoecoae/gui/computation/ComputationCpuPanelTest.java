package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.util.AEColor;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.EnhancedPoseStack;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEvent;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSpriteManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ComputationCpuPanelTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            // LDLib2 discovers its UI registry from the launcher's scan data during class loading.
            Class.forName(ComputationCpuPanel.class.getName());
            AccessorRegistries.init();
        }
    }

    @Test
    void drawingThePanelAlsoDrawsAttachedInventoryAndToolbar() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            ldlib.when(() -> LDLib2.id(anyString())).thenAnswer(invocation ->
                ResourceLocation.fromNamespaceAndPath("ldlib2", invocation.getArgument(0)));
            var panel = spy(new ComputationCpuPanel(mock(ComputationHostPanelUI.Config.class), ignored -> {}, new CpuSelectionState.Identities<>()));
            doNothing().when(panel).drawBackgroundAdditional(any());
            panel.clearAllChildren();
            UIElement inventory = spy(new UIElement());
            UIElement toolbar = spy(new UIElement());
            doNothing().when(inventory).drawInBackground(any());
            doNothing().when(toolbar).drawInBackground(any());
            panel.addChildren(inventory, toolbar);
            GUIContext context = mock(GUIContext.class);
            context.elementColor = -1;

            panel.drawContents(context);

            verify(inventory).drawInBackground(context);
            verify(toolbar).drawInBackground(context);
        }
    }

    @Test
    void settingsNavigationPreservesTheCpuPanelAndPlayerInventory() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var root = ComputationHostPanelUI.create(settingsConfig(), new UIElement(), new UIElement(), new UIElement());
            UIElement main = root.getChildren().getFirst();
            UIElement settings = find(root, "computation-settings");
            InventorySlots inventory = (InventorySlots) main.getChildren().stream()
                .filter(InventorySlots.class::isInstance).findFirst().orElseThrow();
            assertTrue(displayed(main));
            assertFalse(displayed(settings));

            click(find(root, "computation-settings-open"));
            assertFalse(displayed(main));
            assertTrue(displayed(settings));

            click(find(root, "computation-settings-back"));
            assertTrue(displayed(main));
            assertFalse(displayed(settings));
            assertSame(main, root.getChildren().getFirst());
            assertTrue(main.getChildren().contains(inventory));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void cpuRowsSelectBeforeOpeningDetails(int initialSelection) {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var root = ComputationHostPanelUI.create(settingsConfig(), new UIElement(), new UIElement(), new UIElement());
            var main = (ComputationCpuPanel) root.getChildren().getFirst();
            UIElement status = root.getChildren().stream().filter(ComputationCpuStatusPanel.class::isInstance)
                .findFirst().orElseThrow();
            var first = new ComputationCpuEntry(1, "CPU 1", null, BigInteger.ZERO, BigInteger.ZERO,
                0, 0, 1024, 1, CpuSelectionMode.ANY, ResourceLocation.parse("neoecoae:test"), "idle");
            var second = new ComputationCpuEntry(2, "CPU 2", null, BigInteger.ZERO, BigInteger.ZERO,
                0, 0, 1024, 1, CpuSelectionMode.ANY, ResourceLocation.parse("neoecoae:test"), "idle");
            CompoundTag snapshot = new CompoundTag();
            snapshot.putInt("total", 2);
            snapshot.putInt("selectedSerial", initialSelection);
            ListTag page = new ListTag();
            page.add(first.write(RegistryAccess.EMPTY));
            page.add(second.write(RegistryAccess.EMPTY));
            snapshot.put("page", page);
            if (initialSelection == 1) snapshot.put("selected", first.write(RegistryAccess.EMPTY));
            main.setValue(snapshot, false);
            UIElement firstRow = find(main, "computation-cpu-row-0");
            UIElement secondRow = find(main, "computation-cpu-row-1");

            mouse(secondRow, UIEvents.MOUSE_UP, 0, 0);
            assertTrue(displayed(main));
            assertFalse(displayed(status));
            mouse(secondRow, UIEvents.MOUSE_UP, 0, 0);
            assertFalse(displayed(main));
            assertTrue(displayed(status));

            click(find(status, "computation-status-back"));
            mouse(firstRow, UIEvents.MOUSE_UP, 0, 0);
            assertTrue(displayed(main));
            assertFalse(displayed(status));
            mouse(firstRow, UIEvents.MOUSE_UP, 0, 0);
            assertFalse(displayed(main));
            assertTrue(displayed(status));
        }
    }

    @Test
    void settingsSwitchesCallTheExistingServerActions() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var config = settingsConfig();
            UIElement settings = new ComputationSettingsPanel(config);

            serverClick(find(settings, "computation-settings-substitutions"), 0);
            serverClick(find(settings, "computation-settings-cycle_planning"), 0);
            serverClick(find(settings, "computation-settings-fast_planner"), 0);

            verify(config.toggleIgnoringPatternSubstitutions()).run();
            verify(config.toggleCyclePlanning()).run();
            verify(config.toggleFastPlanner()).run();
        }
    }

    @Test
    void cpuModeAndFrequencyKeepBothCyclingDirections() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var config = settingsConfig();
            UIElement root = ComputationHostPanelUI.create(config, new UIElement(), new UIElement(), new UIElement());
            UIElement main = root.getChildren().getFirst();
            UIElement settings = find(root, "computation-settings");
            for (String id : List.of("computation-cpu-mode", "computation-frequency")) {
                assertNull(find(settings, id));
                serverClick(find(main, id), 0);
                serverClick(find(main, id), 1);
            }

            verify(config.adjustCpuSelectionMode()).accept(1);
            verify(config.adjustCpuSelectionMode()).accept(-1);
            verify(config.adjustNetworkFrequency()).accept(1);
            verify(config.adjustNetworkFrequency()).accept(-1);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void returnTabUsesAe2NativeSpritesWithoutShrinkingOnHoverOrPress(boolean focused) {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var settings = new ComputationSettingsPanel(settingsConfig());
            settings.addBackButton(() -> {});
            Button button = spy((Button) find(settings, "computation-settings-back"));
            doReturn(focused).when(button).isFocused();
            doReturn(12F).when(button).getPositionX();
            doReturn(34F).when(button).getPositionY();
            ECOIcon background = focused ? ECOIcon.TAB_BUTTON_BACKGROUND_FOCUS : ECOIcon.TAB_BUTTON_BACKGROUND;
            GUIContext context = mock(GUIContext.class);
            for (Button.State state : Button.State.values()) {
                doReturn(state).when(button).getState();
                clearInvocations(context);

                button.drawBackgroundAdditional(context);

                verify(context).drawTexture(AETextures.icon(background), 12F, 34F, background.width, background.height);
                verify(context).drawTexture(AETextures.icon(ECOIcon.BACK), 14F, 35F, 16F, 16F);
            }
        }
    }

    @Test
    void statusPageCropsTheAtlasAndKeepsItsNativeAspectRatio() throws IOException {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement root = ComputationHostPanelUI.create(settingsConfig(), new UIElement(), new UIElement(), new UIElement());
            UIElement status = root.getChildren().stream().filter(ComputationCpuStatusPanel.class::isInstance)
                .findFirst().orElseThrow();
            SpriteTexture background = assertInstanceOf(SpriteTexture.class,
                status.getStyleBag().computeCandidate(PropertyRegistry.BACKGROUND));
            try (var source = getClass().getResourceAsStream("/assets/neoecoae/textures/gui/eco_craftingcpu.png")) {
                assertNotNull(source);
                var atlas = ImageIO.read(source);
                assertTrue(atlas.getWidth() > background.spriteSize.width);
                assertTrue(atlas.getHeight() > background.spriteSize.height);
                assertEquals(0, background.spritePosition.x);
                assertEquals(0, background.spritePosition.y);
                assertEquals(326, background.spriteSize.width);
                assertEquals(254, background.spriteSize.height);
                assertEquals(background.spriteSize.width, status.getStyleBag().computeCandidate(LayoutProperties.WIDTH).getValue());
                assertEquals(background.spriteSize.height, status.getStyleBag().computeCandidate(LayoutProperties.HEIGHT).getValue());
                assertEquals(TaffyPosition.ABSOLUTE, status.getStyleBag().computeCandidate(LayoutProperties.POSITION));
            }
        }
    }

    @Test
    void toolbarKeepsNativeIconSizeAndMovesBothLayersTogether() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Button button = spy(HostSideButtonBar.createButton().noText().addPreIcon(AETextures.icon(ECOIcon.CRAFT_HAMMER)));
            HostSideButtonBar.placeButton(button, 12, 34);
            doReturn(12F).when(button).getPositionX();
            doReturn(34F).when(button).getPositionY();
            UIElement icon = button.getChildren().stream().filter(child -> child != button.text).findFirst().orElseThrow();
            assertEquals(16, icon.getStyleBag().computeCandidate(LayoutProperties.WIDTH).getValue());
            assertEquals(16, icon.getStyleBag().computeCandidate(LayoutProperties.HEIGHT).getValue());
            PoseStack pose = new PoseStack();
            GUIContext context = mock(GUIContext.class);
            context.pose = new EnhancedPoseStack(pose);
            context.graphics = mock(GuiGraphics.class);
            context.elementColor = -1;
            UIElement renderedIcon = spy(new UIElement());
            doAnswer(invocation -> {
                assertEquals(button.getState() == Button.State.DEFAULT ? 0F : 1F, pose.last().pose().m31());
                return null;
            }).when(renderedIcon).drawInBackground(context);
            button.clearAllChildren();
            button.addChild(renderedIcon);
            doAnswer(invocation -> {
                int y = invocation.getArgument(2);
                assertEquals(34F + (button.getState() == Button.State.DEFAULT ? 0 : 1), y + pose.last().pose().m31());
                return null;
            }).when(context.graphics).blit(eq(ECOIcon.ATLAS), eq(11), anyInt(), eq(2),
                anyFloat(), anyFloat(), eq(18), eq(20), eq(256), eq(256));
            for (Button.State state : Button.State.values()) {
                doReturn(state).when(button).getState();
                clearInvocations(context.graphics);
                button.drawContents(context);
                assertEquals(0F, pose.last().pose().m31());
                var order = inOrder(context.graphics);
                order.verify(context.graphics).flush();
                order.verify(context.graphics).blit(eq(ECOIcon.ATLAS), eq(11), eq(34), eq(2),
                    anyFloat(), anyFloat(), eq(18), eq(20), eq(256), eq(256));
            }
            verify(renderedIcon, times(Button.State.values().length)).drawInBackground(context);
        }
    }

    private static ComputationHostPanelUI.Config settingsConfig() {
        return new ComputationHostPanelUI.Config(
            () -> Component.literal("Test CPU"), () -> true, () -> 1, () -> 0L, () -> true,
            () -> 0L, () -> 1024L, () -> 1024L, () -> 0, () -> 8, () -> 4,
            () -> CpuSelectionMode.ANY, mock(IntConsumer.class), () -> RegistryAccess.EMPTY, List::of,
            ignored -> {}, ignored -> {},
            () -> false, () -> 3, mock(Runnable.class), () -> true, mock(Runnable.class),
            () -> true, mock(Runnable.class), () -> 1, mock(IntConsumer.class));
    }

    private static UIElement find(UIElement element, String id) {
        return element.selectId(id).findFirst().orElse(null);
    }

    private static boolean displayed(UIElement element) {
        // The game applies these pending styles during layout; this test has no game render loop.
        return element.getStyleBag().computeCandidate(LayoutProperties.DISPLAY) != TaffyDisplay.NONE;
    }

    private static void click(UIElement element) {
        assertNotNull(element);
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = element;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static void serverClick(UIElement element, int button) {
        assertNotNull(element);
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.button = button;
        element.getBaubleServerEvent(UIEvents.MOUSE_DOWN).executor().apply(new Object[] {event});
    }

    @ParameterizedTest
    @ValueSource(strings = {"12K", "0 / 70K", "0 B / 22 TB", "22 TB"})
    void scaledMetricRetainsItsLastDigitAndUnit(String value) {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(invocation ->
            invocation.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            int width = invocation.getArgument(1);
            return text.substring(0, Math.clamp(width / 6, 0, text.length()));
        });
        GUIContext context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);
        when(context.graphics.pose()).thenReturn(new PoseStack());

        ComputationCpuPanel.metric(context, font, "parallel_count", value, 0, 0);

        verify(context.graphics).drawString(eq(font), eq(value), eq(0), eq(0), anyInt(), eq(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"17.949 GB", "8 EB", "1,024 KB"})
    void narrowCpuRowsRenderTheCompleteCapacityIncludingItsUnit(String value) {
        Font font = mock(Font.class);
        when(font.width(value)).thenReturn(value.length() * 6);
        GUIContext context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);
        PoseStack pose = new PoseStack();
        when(context.graphics.pose()).thenReturn(pose);
        doAnswer(invocation -> {
            assertTrue(font.width(value) * pose.last().pose().m00() <= 15.001F);
            return 0;
        }).when(context.graphics).drawString(eq(font), eq(value), eq(0), eq(0), anyInt(), eq(false));

        ComputationCpuPanel.fittedText(context, font, value, 0, 0, 15, 0.666F, 0x413F54);

        verify(context.graphics).drawString(eq(font), eq(value), eq(0), eq(0), anyInt(), eq(false));
        verify(font, never()).plainSubstrByWidth(anyString(), anyInt());
    }

    @Test
    void statusTextAlignsTowardTheItemAndLeavesOnePixelBetweenLines() {
        Font font = mock(Font.class);
        List<Component> lines = List.of(Component.literal("Stored: 100"), Component.literal("Crafting: 1"));
        when(font.width(lines.get(0))).thenReturn(40);
        when(font.width(lines.get(1))).thenReturn(60);
        GUIContext context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);
        PoseStack pose = new PoseStack();
        when(context.graphics.pose()).thenReturn(pose);
        List<Float> positions = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            Component line = invocation.getArgument(1);
            assertEquals(56F, pose.last().pose().m30() + font.width(line) * pose.last().pose().m00());
            positions.add(pose.last().pose().m31());
            return 0;
        }).when(context.graphics).drawString(eq(font), any(Component.class), eq(0), eq(0), anyInt(), eq(false));

        ComputationCpuStatusPanel.drawItemDescription(context, font, lines, 10, 20);

        assertEquals(2, positions.size());
        assertEquals(font.lineHeight * 0.5F + 1, positions.get(1) - positions.get(0));
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void batchingChangesOnlyEcoCellsAndCentersTheNativeIcon(boolean batched, boolean colored) {
        try (var minecraft = mockStatic(Minecraft.class); var keys = mockStatic(AEKeyRendering.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            Font font = mock(Font.class);
            when(font.width(any(Component.class))).thenReturn(40);
            GUIContext context = mock(GUIContext.class);
            context.graphics = mock(GuiGraphics.class);
            when(context.graphics.pose()).thenReturn(new PoseStack());
            var key = mock(AEKey.class);
            var entry = new ComputationCpuItemEntry(key, BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO, batched);

            ComputationCpuStatusPanel.drawItem(context, font, entry, 10, 20, colored);

            if (colored) {
                int tint = (batched ? AEColor.PURPLE : AEColor.GREEN).blackVariant | 0x5A000000;
                verify(context.graphics).fill(10, 20, 77, 42, tint);
            } else {
                verify(context.graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            }
            verify(context.graphics, times(batched ? 1 : 0)).blit(
                ResourceLocation.parse("neoecoae:textures/gui/eco_batching.png"),
                11, 23, 0F, 0F, 16, 16, 16, 16);
            keys.verify(() -> AEKeyRendering.drawInGui(any(), eq(context.graphics), eq(0), eq(0), eq(key)));
        }
    }

    @Test
    void wideItemDescriptionsKeepAe2FontScaleAndAlignment() {
        Font font = mock(Font.class);
        List<Component> lines = List.of(Component.literal("Crafting: 123.456 EB"), Component.literal("Scheduled: 9.876 ZB"));
        when(font.width(lines.getFirst())).thenReturn(140);
        when(font.width(lines.getLast())).thenReturn(120);
        GUIContext context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);
        PoseStack pose = new PoseStack();
        when(context.graphics.pose()).thenReturn(pose);
        doAnswer(invocation -> {
            Component line = invocation.getArgument(1);
            assertEquals(0.5F, pose.last().pose().m00());
            assertEquals(56F, pose.last().pose().m30() + font.width(line) * pose.last().pose().m00(), 0.001F);
            return 0;
        }).when(context.graphics).drawString(eq(font), any(Component.class), eq(0), eq(0), anyInt(), eq(false));

        ComputationCpuStatusPanel.drawItemDescription(context, font, lines, 10, 20);

        verify(context.graphics, times(2)).drawString(eq(font), any(Component.class), eq(0), eq(0), anyInt(), eq(false));
        verify(font, never()).plainSubstrByWidth(anyString(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void itemPayloadPreservesBatchingAndAcceptsOlderUnmarkedPayloads(boolean batched) {
        var key = mock(AEKey.class);
        var keyTag = new CompoundTag();
        when(key.toTagGeneric(RegistryAccess.EMPTY)).thenReturn(keyTag);
        var entry = new ComputationCpuItemEntry(key, BigInteger.TEN.pow(100), BigInteger.TEN, BigInteger.ONE, batched);
        try (var keys = mockStatic(AEKey.class)) {
            keys.when(() -> AEKey.fromTagGeneric(RegistryAccess.EMPTY, keyTag)).thenReturn(key);
            var payload = entry.write(RegistryAccess.EMPTY);
            assertEquals(entry, ComputationCpuItemEntry.read(payload, RegistryAccess.EMPTY));
            payload.remove("batched");
            assertFalse(ComputationCpuItemEntry.read(payload, RegistryAccess.EMPTY).batched());
        }
    }

    @Test
    void statusTitleKeepsAe2EtaForOrdersBeyondLongRange() {
        BigInteger requested = BigInteger.TEN.pow(400);
        var entry = new ComputationCpuEntry(1, "CPU", null, requested, requested.divide(BigInteger.TWO),
            0.5F, 2_000_000_000L, 0, 0, CpuSelectionMode.ANY, ResourceLocation.parse("neoecoae:test"), "running");
        // Game language resources are absent in this unit-test JVM; inspect the formatter input instead.
        try (var durations = mockStatic(org.apache.commons.lang3.time.DurationFormatUtils.class)) {
            durations.when(() -> org.apache.commons.lang3.time.DurationFormatUtils.formatDuration(eq(2000L), anyString()))
                .thenReturn("00:00:02");
            assertTrue(ComputationCpuStatusPanel.title(entry, true).getString().endsWith(" - 00:00:02"));
            durations.verify(() -> org.apache.commons.lang3.time.DurationFormatUtils.formatDuration(eq(2000L), anyString()));
            assertFalse(ComputationCpuStatusPanel.title(entry, false).getString().contains(" - "));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void statusActionButtonsUseNativeAe2TextOffsetsAndColors(boolean active) throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Minecraft client = mock(Minecraft.class);
            Font font = mock(Font.class);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(client, font);
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            var root = ComputationHostPanelUI.create(settingsConfig(), new UIElement(), new UIElement(), new UIElement());
            Button button = spy((Button) find(root, "computation-status-cancel"));
            button.clearAllChildren();
            doReturn(active).when(button).isActive();
            doReturn(10F).when(button).getPositionX();
            doReturn(20F).when(button).getPositionY();
            doReturn(50F).when(button).getSizeWidth();
            doReturn(20F).when(button).getSizeHeight();
            when(font.width(any(Component.class))).thenReturn(20);
            when(font.width(any(net.minecraft.util.FormattedCharSequence.class))).thenReturn(20);
            GUIContext context = mock(GUIContext.class);
            context.elementColor = -1;
            context.graphics = mock(GuiGraphics.class);
            for (Button.State state : Button.State.values()) {
                doReturn(state).when(button).getState();
                clearInvocations(context.graphics);
                button.drawContents(context);
                int offset = !active ? -1 : state == Button.State.DEFAULT ? 1 : 0;
                int color = !active ? 0xFF413F54 : state == Button.State.DEFAULT ? 0xFFF2F2F2 : 0xFF517497;
                var order = inOrder(context.graphics);
                order.verify(context.graphics).flush();
                order.verify(context.graphics).drawString(eq(font), any(net.minecraft.util.FormattedCharSequence.class),
                    eq(25), eq(26 - offset), eq(color), eq(false));
            }
        }
    }

    @Test
    void scrollbarPagesOnTheTrackAndDragsOnlyWhenTheHandleWasPressed() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Minecraft client = mock(Minecraft.class);
            GuiSpriteManager sprites = mock(GuiSpriteManager.class);
            TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
            SpriteContents contents = mock(SpriteContents.class);
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            when(client.getGuiSprites()).thenReturn(sprites);
            when(sprites.getSprite(any())).thenReturn(sprite);
            when(sprite.contents()).thenReturn(contents);
            when(contents.width()).thenReturn(12);
            when(contents.height()).thenReturn(15);
            UIElement owner = new UIElement();
            IntConsumer scroll = mock(IntConsumer.class);
            var scrollbar = new ComputationScrollbar(owner, 0, 0, 100, scroll);
            owner.addChild(scrollbar);
            scrollbar.update(0, 30, 3);

            mouse(scrollbar, UIEvents.MOUSE_DOWN, 15, 100);
            verify(scroll).accept(3);
            mouse(owner, UIEvents.MOUSE_MOVE, 15, 30);
            verifyNoMoreInteractions(scroll);
            mouse(owner, UIEvents.MOUSE_UP, 15, 30);

            scrollbar.update(0, 30, 3);
            mouse(scrollbar, UIEvents.MOUSE_DOWN, 5, 5);
            mouse(owner, UIEvents.MOUSE_MOVE, 15, 500);
            verify(scroll).accept(30);
            mouse(owner, UIEvents.MOUSE_MOVE, 15, -500);
            verify(scroll).accept(0);
            mouse(owner, UIEvents.MOUSE_UP, 15, -500);
            clearInvocations(scroll);
            mouse(owner, UIEvents.MOUSE_MOVE, 15, 500);
            verifyNoInteractions(scroll);

            // Releasing outside the owner still clears the held-click repeat on the next UI tick.
            ModularUI ui = mock(ModularUI.class);
            when(ui.getLastMouseDownButton()).thenReturn(-1);
            var uiField = UIElement.class.getDeclaredField("modularUI");
            uiField.setAccessible(true);
            uiField.set(scrollbar, ui);
            mouse(scrollbar, UIEvents.MOUSE_DOWN, 15, 100);
            scrollbar.screenTick();
            clearInvocations(scroll);
            mouse(owner, UIEvents.MOUSE_MOVE, 15, 500);
            verifyNoInteractions(scroll);
        }
    }

    private static void mouse(UIElement element, String type, float x, float y) {
        UIEvent event = UIEvent.create(type);
        event.target = element;
        event.x = x;
        event.y = y;
        event.button = 0;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    @Test
    void cpuActionsCarryTheirTargetAndRejectRemovedCpus() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            ECOCraftingCPU first = mock(ECOCraftingCPU.class), target = mock(ECOCraftingCPU.class);
            ECOCraftingCPU replacement = mock(ECOCraftingCPU.class);
            var cpus = new java.util.concurrent.atomic.AtomicReference<>(List.of(first, target));
            var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
            var config = mock(ComputationHostPanelUI.Config.class);
            when(config.cpus()).thenReturn(cpus::get);
            when(config.canInteract()).thenReturn(allowed::get);
            Consumer<ECOCraftingCPU> cancel = mock(Consumer.class), pause = mock(Consumer.class);
            when(config.cancelCpu()).thenReturn(cancel);
            when(config.toggleCpuSuspended()).thenReturn(pause);
            var identities = new CpuSelectionState.Identities<ECOCraftingCPU>();
            var main = new CpuSelectionState<ECOCraftingCPU>(8, identities);
            main.update(cpus.get(), ECOCraftingCPU::isBusy, cpu -> null);
            int serial = main.serial(target);
            var panel = new ComputationCpuStatusPanel(config, identities);
            var field = UIElement.class.getDeclaredField("rpcEvents");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<RPCEvent> events = (List<RPCEvent>) field.get(panel);
            RPCEvent action = events.getFirst();

            // The status server has not received a selection request; the action still targets CPU #2.
            action.executor().apply(new Object[] {serial, 1});
            action.executor().apply(new Object[] {serial, 2});
            verify(pause).accept(target);
            verify(cancel).accept(target);

            cpus.set(List.of(first, replacement));
            action.executor().apply(new Object[] {serial, 2});
            action.executor().apply(new Object[] {Integer.MAX_VALUE, 1});
            allowed.set(false);
            action.executor().apply(new Object[] {main.serial(first), 2});
            verifyNoMoreInteractions(pause, cancel);
        }
    }
}
