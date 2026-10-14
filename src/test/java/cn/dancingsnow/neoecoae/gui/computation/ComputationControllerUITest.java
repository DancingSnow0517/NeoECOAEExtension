package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ComputationControllerUITest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            // NeoECOAE's static initialiser (used by theme icons) needs a ModList while the class loads.
            Class.forName(NeoECOAE.class.getName());
            AccessorRegistries.init();
        }
    }

    @Test
    void loadsLayoutWithArtworkSwitchesInventoryAndToolbar() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement root = ComputationControllerUI.create(
                config(mock(Runnable.class), mock(Runnable.class), mock(Runnable.class)),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement(), new UIElement(), new UIElement());

            assertNotNull(find(root, "computation_artwork"));
            assertNotNull(find(root, "computation_switch_substitutions"));
            assertNotNull(find(root, "computation_switch_cycle_planning"));
            assertNotNull(find(root, "computation_switch_fast_planner"));
            assertInstanceOf(InventorySlots.class, find(root, "computation_player_inventory"));
            assertNotNull(find(root, "computation_toolbar"));
            assertNotNull(find(root, "computation_drive_grid"));
            assertNotNull(find(root, "computation_main"));
            assertNotNull(find(root, "computation_cpu_selector"));
            assertNotNull(find(root, "computation-info"));
            assertNotNull(find(root, "computation-cpu-list-toggle"));
            assertNotNull(find(root, "computation_status"));
        }
    }

    @Test
    void driveGridHasOneSlotPerDriveWithoutDrives() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement root = ComputationControllerUI.create(
                config(mock(Runnable.class), mock(Runnable.class), mock(Runnable.class)),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement(), new UIElement(), new UIElement());

            UIElement grid = find(root, "computation_drive_grid");
            assertNotNull(grid);
            // One BindableValue child carries the synced snapshot and is not a slot.
            assertEquals(22, grid.getChildren().stream().filter(ItemSlot.class::isInstance).count());
        }
    }

    @Test
    void switchesRunTheirServerActions() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Runnable substitutions = mock(Runnable.class);
            Runnable cyclePlanning = mock(Runnable.class);
            Runnable fastPlanner = mock(Runnable.class);
            UIElement root = ComputationControllerUI.create(config(substitutions, cyclePlanning, fastPlanner),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement(), new UIElement(), new UIElement());

            serverClick(find(root, "computation_switch_substitutions"), 0);
            verify(substitutions).run();
            verifyNoMoreInteractions(cyclePlanning, fastPlanner);

            serverClick(find(root, "computation_switch_cycle_planning"), 0);
            verify(cyclePlanning).run();
            verifyNoMoreInteractions(substitutions, fastPlanner);

            serverClick(find(root, "computation_switch_fast_planner"), 0);
            verify(fastPlanner).run();
            verifyNoMoreInteractions(substitutions, cyclePlanning);
        }
    }

    @Test
    void switchTooltipListsNameStateDescriptionAndCount() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement root = ComputationControllerUI.create(
                config(mock(Runnable.class), mock(Runnable.class), mock(Runnable.class)),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement(), new UIElement(), new UIElement());

            UIElement substitutions = find(root, "computation_switch_substitutions");
            assertNotNull(substitutions);
            UIEvent tooltip = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
            tooltip.target = substitutions;
            UIEventDispatcher.dispatchEvent(tooltip, false, false, false);
            assertNotNull(tooltip.hoverTooltips);
            assertEquals(4, tooltip.hoverTooltips.tooltipTexts().size());
        }
    }

    private static ComputationControllerUI.Config config(Runnable toggleSubstitutions, Runnable toggleCycle,
                                                         Runnable toggleFast) {
        return new ComputationControllerUI.Config(
            () -> true, () -> 1, () -> 0, () -> 0,
            () -> 0L, () -> 1024L, () -> 1024L,
            () -> 0, () -> 8, () -> 4, () -> 16,
            () -> CpuSelectionMode.ANY, mock(IntConsumer.class),
            () -> false, () -> 3, toggleSubstitutions,
            () -> true, toggleCycle,
            () -> true, toggleFast,
            () -> 1, mock(IntConsumer.class),
            () -> ItemStack.EMPTY, List::of, List::of, null,
            (position, expected, toInventory) -> false, (position, expected) -> false,
            () -> 0L, () -> true, List::of, cpu -> {}, cpu -> {},
            () -> Component.empty());
    }

    private static UIElement find(UIElement element, String id) {
        return element.selectId(id).findFirst().orElse(null);
    }

    private static void serverClick(UIElement element, int button) {
        assertNotNull(element);
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.button = button;
        element.getBaubleServerEvent(UIEvents.MOUSE_DOWN).executor().apply(new Object[] {event});
    }
}
