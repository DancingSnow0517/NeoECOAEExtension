package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib.LDLib;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory.InventorySlots;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

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

        }
    }

    @Test
    void loadsLayoutWithArtworkSwitchesInventoryAndToolbar() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement root = ComputationControllerUI.create(
                config(mock(Runnable.class), mock(Runnable.class), mock(Runnable.class)),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement());

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
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement root = ComputationControllerUI.create(
                config(mock(Runnable.class), mock(Runnable.class), mock(Runnable.class)),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement());

            UIElement grid = find(root, "computation_drive_grid");
            assertNotNull(grid);
            // One BindableValue child carries the synced snapshot and is not a slot.
            assertEquals(22, grid.getChildren().stream().filter(ItemSlot.class::isInstance).count());
        }
    }

    @Test
    void switchesRunTheirServerActions() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            Runnable substitutions = mock(Runnable.class);
            Runnable cyclePlanning = mock(Runnable.class);
            Runnable fastPlanner = mock(Runnable.class);
            UIElement root = ComputationControllerUI.create(config(substitutions, cyclePlanning, fastPlanner),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement());

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
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement root = ComputationControllerUI.create(
                config(mock(Runnable.class), mock(Runnable.class), mock(Runnable.class)),
                () -> ComputationControllerPanelState.UNFORMED, new UIElement());

            UIElement substitutions = find(root, "computation_switch_substitutions");
            assertNotNull(substitutions);
            UIEvent tooltip = new UIEvent(UIEvents.HOVER_TOOLTIPS,null,0,0,0);
            tooltip.target = substitutions;
            tooltip.target.dispatchEvent(tooltip);
            assertNotNull(tooltip.hoverTooltips);
            assertEquals(4, tooltip.hoverTooltips.tooltips().size());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void allThreeGaugesOpenToTheirActualRatiosAndKeepFollowingSyncedValues() throws ReflectiveOperationException {
        AtomicLong now = new AtomicLong();
        try (var ldlib = mockStatic(LDLib.class); var minecraft = mockStatic(Minecraft.class);
             var clock = mockStatic(Util.class, CALLS_REAL_METHODS)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            clock.when(Util::getMillis).thenAnswer(call -> now.get());
            Minecraft client = mock(Minecraft.class);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(client, mock(Font.class));
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            UIElement root = ComputationControllerUI.create(config(() -> {}, () -> {}, () -> {}),
                () -> new ComputationControllerPanelState(true, 1, 0, 0, 0, 0),
                new UIElement());
            UIElement artwork = find(root, "computation_artwork");
            BindableValue<Tag> synced = (BindableValue<Tag>) cn.dancingsnow.neoecoae.platform.JdkCompat.last(artwork.getChildren());
            CompoundTag update = ((CompoundTag) synced.getValue()).copy();
            update.putLong("usedBytes", 512);
            update.putInt("usedThreads", 2);
            synced.setValue(update);
            GUIContext context = mock(GUIContext.class);
            cn.dancingsnow.neoecoae.util.UiTestSupport.set(context,"graphics",mock(GuiGraphics.class));
            PoseStack pose = new PoseStack();
            when(context.graphics.pose()).thenReturn(pose);

            artwork.drawBackgroundAdditional(context);
            verify(context, never()).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y >= 154 && sprite.spritePosition.y <= 168),
                anyFloat(), anyFloat(), anyFloat(), eq(7F));
            now.set(250);
            artwork.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 154), eq(83F), eq(131F), eq(11F), eq(7F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 161), eq(140F), eq(131F), eq(6F), eq(7F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 168), eq(197F), eq(131F), eq(6F), eq(7F));

            clearInvocations(context);
            now.set(500);
            artwork.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 154), eq(83F), eq(131F), eq(22F), eq(7F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 161), eq(140F), eq(131F), eq(11F), eq(7F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 168), eq(197F), eq(131F), eq(11F), eq(7F));

            clearInvocations(context);
            update = update.copy();
            update.putLong("usedBytes", 256);
            synced.setValue(update);
            artwork.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 154), eq(83F), eq(131F), eq(11F), eq(7F));
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
        UIEvent event = new UIEvent(UIEvents.MOUSE_DOWN,null,0,0,0);
        event.button = button;
        cn.dancingsnow.neoecoae.util.UiTestSupport.serverClick(element,event);
    }
}
