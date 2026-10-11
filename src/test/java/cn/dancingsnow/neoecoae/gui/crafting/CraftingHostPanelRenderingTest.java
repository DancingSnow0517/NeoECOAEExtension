package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CraftingHostPanelRenderingTest {
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
            AccessorRegistries.init();
        }
    }

    @Test
    void artworkTextAndGaugesFollowTheWindowPosition() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Font font = installFont(minecraft);
            ItemStack hostItem = new ItemStack(Items.DIAMOND);
            UIElement artwork = positioned(artwork(formedState(), hostItem));
            GUIContext context = context();

            artwork.drawBackgroundAdditional(context);

            verify(context).drawTexture(NETextures.Crafting.CONTROLLER, 120F, 80F, 253F, 214F);
            verify(context).drawTexture(any(), eq(143F), eq(85F), eq(13F), eq(18F));
            verify(context).drawTexture(any(), eq(169F), eq(109F), eq(16F), eq(34F));
            verify(context).drawTexture(any(), eq(218F), eq(171F), eq(34F), eq(9F));
            verify(context).drawTexture(any(), eq(218F), eq(193F), eq(17F), eq(9F));
            verify(context.graphics).renderItem(argThat(stack -> ItemStack.matches(hostItem, stack)), eq(129), eq(118));
            verify(context.graphics).drawString(font, "0 us", 349, 90, 0xFFE4E4EE, false);
            verify(context.graphics).drawString(font, "gui.neoecoae.crafting.ui.fx_active", 316, 214,
                0xFFE4E4EE, false);
            verify(context.graphics).drawString(font, "gui.neoecoae.crafting.ui.coolant_overclock", 316, 248,
                0xFFB4B6CC, false);
            verify(context.graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncedMetricsReplaceInitialValues() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            installFont(minecraft);
            UIElement artwork = artwork(formedState());
            BindableValue<Tag> synced = (BindableValue<Tag>) artwork.getChildren().getFirst();
            CompoundTag update = formedState().toTag();
            update.putInt("executionTicks", 10);
            update.putLong("energyUsage", 0);
            update.putInt("coolantAmount", 0);
            update.putInt("coolantCapacity", 100);
            synced.setValue(update);
            artwork = positioned(artwork);
            GUIContext context = context();

            artwork.drawBackgroundAdditional(context);

            verify(context).drawTexture(any(), eq(218F), eq(193F), eq(7F), eq(9F));
            verify(context.graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    void unformedPanelStillDrawsAtTheWindowPosition() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Font font = installFont(minecraft);
            ItemStack hostItem = new ItemStack(Items.DIAMOND);
            UIElement artwork = positioned(artwork(CraftingControllerPanelState.UNFORMED, hostItem));
            GUIContext context = context();

            artwork.drawBackgroundAdditional(context);

            verify(context).drawTexture(NETextures.Crafting.CONTROLLER, 120F, 80F, 253F, 214F);
            verifyNoMoreInteractions(context);
            verify(context.graphics).drawString(font, "0 us", 349, 90, 0xFFE4E4EE, false);
            verify(context.graphics).renderItem(argThat(stack -> ItemStack.matches(hostItem, stack)), eq(129), eq(118));
            verifyNoMoreInteractions(context.graphics);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void informationTooltipUsesSyncedLinesAndPreservesTheStatusColor() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement icon = CraftingHostPanelUI.createMaintenanceStatusIcon(() -> Component.literal("initial"));
            BindableValue<Component> synced = (BindableValue<Component>) icon.getChildren().getFirst();
            synced.setValue(Component.literal("status\nfirst condition\nsecond condition").withColor(0xD8B4FE), false);
            UIEvent event = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
            event.target = icon;
            UIEventDispatcher.dispatchEvent(event);

            List<Component> lines = event.hoverTooltips.tooltipTexts();
            assertEquals(List.of("status", "first condition", "second condition"),
                lines.stream().map(Component::getString).toList());
            assertEquals(0xD8B4FE, lines.getFirst().getStyle().getColor().getValue() & 0xFFFFFF);
            assertEquals(0xFF5555, lines.get(1).getStyle().getColor().getValue() & 0xFFFFFF);
            var infinite = CraftingHostPanelUI.maintenanceTooltipLines(Component.literal("infinite").withColor(0x55FF8A));
            assertEquals(1, infinite.size());
            assertEquals(0x55FF8A, infinite.getFirst().getStyle().getColor().getValue() & 0xFFFFFF);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void workerOutputsKeepEmptyCellsAndComponentsAndClearOnSyncedUpdates() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            installFont(minecraft);
            var state = CraftingControllerPanelState.of(3, List.of(), List.of(),
                3, true, false, false, 0, 0);
            ItemStack diamond = new ItemStack(Items.DIAMOND);
            diamond.set(DataComponents.CUSTOM_NAME, Component.literal("Worker output"));
            ItemStack emerald = new ItemStack(Items.EMERALD);
            List<ItemStack> items = new ArrayList<>(Collections.nCopies(11, ItemStack.EMPTY));
            items.set(10, diamond);
            items.set(8, emerald);
            UIElement artwork = positioned(artwork(state, ItemStack.EMPTY, items));
            BindableValue<Tag> synced = (BindableValue<Tag>) artwork.getChildren().getFirst();
            ListTag encoded = ((CompoundTag) synced.getValue()).getList("workerItems", Tag.TAG_COMPOUND);
            assertEquals(11, encoded.size());
            assertTrue(encoded.getCompound(9).isEmpty());
            GUIContext context = context();

            artwork.drawBackgroundAdditional(context);

            verify(context.graphics).renderItem(argThat(stack -> ItemStack.matches(diamond, stack)), eq(349), eq(118));
            verify(context.graphics).renderItem(argThat(stack -> ItemStack.matches(emerald, stack)), eq(313), eq(118));
            verify(context.graphics, times(2)).renderItem(any(ItemStack.class), anyInt(), anyInt());

            CompoundTag update = state.toTag();
            update.put("workerItems", new ListTag());
            synced.setValue(update);
            clearInvocations(context.graphics);
            artwork.drawBackgroundAdditional(context);

            verify(context.graphics, never()).renderItem(any(ItemStack.class), anyInt(), anyInt());
        }
    }

    private static UIElement artwork(CraftingControllerPanelState state) {
        return artwork(state, ItemStack.EMPTY);
    }

    private static UIElement artwork(CraftingControllerPanelState state, ItemStack hostItem) {
        return artwork(state, hostItem, List.of());
    }

    private static UIElement artwork(CraftingControllerPanelState state, ItemStack hostItem, List<ItemStack> workerItems) {
        var config = new CraftingHostPanelUI.Config(
            Component::empty, Component::empty, () -> 1, () -> true, () -> false, () -> {},
            () -> false, () -> {}, () -> 1, () -> 2, () -> 1, () -> 1L, () -> 0L,
            () -> 3, () -> 3, () -> 4, () -> 0L, () -> 500_000L,
            () -> 25, () -> 100, () -> 9, () -> FluidStack.EMPTY, () -> FluidStack.EMPTY, () -> 16000,
            () -> RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), List::of,
            () -> false, () -> 0, () -> {}, () -> 0, ignored -> {}, () -> state,
            () -> 1L, () -> 64L, () -> 0, () -> 0, () -> hostItem, () -> workerItems);
        return CraftingHostPanelUI.create(config).getChildren().getFirst();
    }

    private static UIElement positioned(UIElement element) {
        UIElement artwork = spy(element);
        doReturn(120F).when(artwork).getPositionX();
        doReturn(80F).when(artwork).getPositionY();
        return artwork;
    }

    private static CraftingControllerPanelState formedState() {
        return CraftingControllerPanelState.of(3, List.of(1, 2), List.of(3, 1),
            2, false, true, true, 500, 500);
    }

    private static Font installFont(org.mockito.MockedStatic<Minecraft> minecraft) throws ReflectiveOperationException {
        Minecraft client = mock(Minecraft.class);
        Font font = mock(Font.class);
        var field = Minecraft.class.getDeclaredField("font");
        field.setAccessible(true);
        field.set(client, font);
        minecraft.when(Minecraft::getInstance).thenReturn(client);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    private static GUIContext context() {
        GUIContext context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);
        return context;
    }
}
