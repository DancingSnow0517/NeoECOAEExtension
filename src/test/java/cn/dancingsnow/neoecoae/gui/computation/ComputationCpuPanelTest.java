package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
            var panel = spy(new ComputationCpuPanel(mock(ComputationHostPanelUI.Config.class)));
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

    private static ComputationHostPanelUI.Config settingsConfig() {
        return new ComputationHostPanelUI.Config(
            () -> Component.literal("Test CPU"), () -> true, () -> 1, () -> 0L, () -> true,
            () -> 0L, () -> 1024L, () -> 1024L, () -> 0, () -> 8, () -> 4,
            () -> CpuSelectionMode.ANY, mock(IntConsumer.class), () -> RegistryAccess.EMPTY, List::of,
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
}
