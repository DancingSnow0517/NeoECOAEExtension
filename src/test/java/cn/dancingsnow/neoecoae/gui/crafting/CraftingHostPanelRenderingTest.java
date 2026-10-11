package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.crafting.display.CraftingOutputDisplay;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.EnhancedPoseStack;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
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
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

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
            verify(context).drawTexture(any(), eq(296F), eq(181F), eq(8F), eq(23F));
            verify(context.graphics).renderItem(argThat(stack -> ItemStack.matches(hostItem, stack)), eq(129), eq(118));
            verify(context.graphics).drawString(font, "0 us", 349, 90, 0xFFE4E4EE, true);
            verify(context.graphics.pose()).translate(150.5D, 148.0D, 0.0D);
            verify(context.graphics.pose()).translate(137.5D, 164.0D, 0.0D);
            verify(context.graphics.pose()).translate(164.5D, 164.0D, 0.0D);
            verify(context.graphics.pose()).translate(209.5D, 148.0D, 0.0D);
            verify(context.graphics.pose()).translate(262.5D, 148.0D, 0.0D);
            verify(context.graphics.pose()).translate(300.0D, 148.0D, 0.0D);
            verify(context.graphics.pose()).translate(325.0D, 148.0D, 0.0D);
            verify(context.graphics.pose()).translate(356.0D, 148.0D, 0.0D);
            for (String heading : List.of("control_heading", "cooling_short", "overclock_short", "queue_heading", "parallel_heading",
                "energy_heading", "coolant_heading", "output_heading")) {
                verify(context.graphics).drawString(eq(font), eq(Component.translatable("gui.neoecoae.crafting.ui." + heading)),
                    eq(0), eq(0), eq(0xFFE4E4EE), eq(true));
            }
            verify(context.graphics.pose()).translate(316.0D, 214.0D, 0.0D);
            verify(context.graphics).drawString(font, "gui.neoecoae.crafting.ui.fx_short", 0, 0,
                0xFFE4E4EE, true);
            verify(context.graphics.pose()).translate(316.0D, 249.0D, 0.0D);
            verify(context.graphics).drawString(font, "gui.neoecoae.crafting.ui.coolant_overclock", 0, 0,
                0xFFB4B6CC, true);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 104 && sprite.spritePosition.y == 215),
                eq(186F), eq(194F), eq(22F), eq(7F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 104 && sprite.spritePosition.y == 223),
                eq(241F), eq(194F), eq(11F), eq(7F));
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
            update.putInt("queue", 250);
            update.putInt("energy", 0);
            update.putInt("executionTicks", 10);
            update.putLong("energyUsage", 0);
            update.putInt("coolantAmount", 0);
            update.putInt("coolantCapacity", 100);
            synced.setValue(update);
            artwork = positioned(artwork);
            GUIContext context = context();

            artwork.drawBackgroundAdditional(context);

            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 104 && sprite.spritePosition.y == 215),
                eq(186F), eq(194F), eq(11F), eq(7F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 104 && sprite.spritePosition.y == 223),
                eq(241F), eq(194F), eq(4F), eq(7F));
            verify(context).drawTexture(any(), eq(329F), eq(164F), eq(5F), eq(1F));
            verify(context, never()).drawTexture(any(), eq(296F), anyFloat(), eq(8F), anyFloat());
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
            verify(context.graphics).drawString(font, "0 us", 349, 90, 0xFFE4E4EE, true);
            verify(context.graphics).renderItem(argThat(stack -> ItemStack.matches(hostItem, stack)), eq(129), eq(118));
            verify(context.graphics, times(8)).pose();
            for (String heading : List.of("control_heading", "cooling_short", "overclock_short", "queue_heading", "parallel_heading",
                "energy_heading", "coolant_heading", "output_heading")) {
                verify(context.graphics).drawString(eq(font), eq(Component.translatable("gui.neoecoae.crafting.ui." + heading)),
                    eq(0), eq(0), eq(0xFFE4E4EE), eq(true));
            }
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
            List<CraftingOutputDisplay> items = new ArrayList<>(Collections.nCopies(11, CraftingOutputDisplay.EMPTY));
            items.set(10, new CraftingOutputDisplay(diamond, BigInteger.TEN.pow(30)));
            items.set(8, new CraftingOutputDisplay(emerald, BigInteger.valueOf(512)));
            UIElement artwork = positioned(artwork(state, ItemStack.EMPTY, items));
            BindableValue<Tag> synced = (BindableValue<Tag>) artwork.getChildren().getFirst();
            CompoundTag payload = (CompoundTag) synced.getValue();
            ListTag encoded = payload.getList("workerOutputs", Tag.TAG_COMPOUND);
            assertEquals(11, encoded.size());
            assertTrue(encoded.getCompound(9).isEmpty());
            var slots = workerSlots(artwork);
            assertEquals(11, slots.size());
            assertTrue(ItemStack.matches(diamond, slots.get(10).getValue()));
            assertTrue(ItemStack.matches(emerald, slots.get(8).getValue()));
            assertTrue(slots.get(9).getValue().isEmpty());
            assertEquals("1000000000000000000000000000000", encoded.getCompound(10).getString("amount"));
            assertTrue(slots.get(10).getFullTooltipTexts(false).getLast().getString().endsWith(
                "1,000,000,000,000,000,000,000,000,000,000"));

            CompoundTag countUpdate = payload.copy();
            countUpdate.getList("workerOutputs", Tag.TAG_COMPOUND).getCompound(10).putString("amount", "2000");
            synced.setValue(countUpdate);
            assertTrue(ItemStack.matches(diamond, slots.get(10).getValue()));
            assertTrue(slots.get(10).getFullTooltipTexts(false).getLast().getString().endsWith("2,000"));

            CompoundTag update = state.toTag();
            update.put("workerOutputs", new ListTag());
            synced.setValue(update);
            assertTrue(slots.stream().allMatch(slot -> slot.getValue().isEmpty()));
            assertTrue(slots.get(10).getFullTooltipTexts(false).isEmpty());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void outputSlotsShowNativeItemTooltipsHoverAndBottomRightCompactCounts() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(Minecraft.class);
             var drawer = mockStatic(DrawerHelper.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Font font = installFont(minecraft);
            when(font.width(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).length() * 6);
            ItemStack item = new ItemStack(Items.DIAMOND);
            var artwork = artwork(formedState(), ItemStack.EMPTY,
                List.of(new CraftingOutputDisplay(item, BigInteger.valueOf(32))));
            var synced = (BindableValue<Tag>) artwork.getChildren().getFirst();
            var slot = spy(workerSlots(artwork).getFirst());
            doReturn(true).when(slot).isHover();
            doReturn(16F).when(slot).getContentWidth();
            doReturn(16F).when(slot).getContentHeight();
            drawer.when(() -> DrawerHelper.getItemToolTip(any())).thenReturn(
                List.of(Component.literal("Actual item name"), Component.literal("Actual item details")));
            UIEvent event = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
            event.target = slot;
            UIEventDispatcher.dispatchEvent(event);
            assertEquals("Actual item details", event.hoverTooltips.tooltipTexts().get(1).getString());
            assertTrue(ItemStack.matches(item, event.hoverTooltips.tooltipStack()));
            assertTrue(event.hoverTooltips.tooltipTexts().getLast().getString().endsWith("32"));
            GUIContext context = context();
            context.modularUI = mock(ModularUI.class);
            context.pose = mock(EnhancedPoseStack.class);
            context.elementColor = -1;
            for (var count : List.of("32", "512", "2000")) {
                CompoundTag update = ((CompoundTag) synced.getValue()).copy();
                update.getList("workerOutputs", Tag.TAG_COMPOUND).getCompound(0).putString("amount", count);
                synced.setValue(update);
                var updatedSlot = spy(workerSlots(artwork).getFirst());
                doReturn(true).when(updatedSlot).isHover();
                doReturn(16F).when(updatedSlot).getContentWidth();
                doReturn(16F).when(updatedSlot).getContentHeight();
                updatedSlot.drawBackgroundAdditional(context);
                String displayed = count.equals("2000") ? "2k" : count;
                drawer.verify(() -> DrawerHelper.drawStringFixedCorner(eq(context.graphics), eq(displayed),
                    eq(16F), eq(16F), eq(-1), eq(true), anyFloat()));
            }
            verify(context, times(3)).drawTexture(NETextures.AE2_SLOT_HIGHLIGHT, 0F, 0F, 16F, 16F);
        }
    }

    private static List<ItemSlot> workerSlots(UIElement artwork) {
        return artwork.getChildren().stream().filter(ItemSlot.class::isInstance).map(ItemSlot.class::cast).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void modeButtonsKeepOverclockAndCoolingIndependentAndFollowSyncedHoverStates() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            AtomicBoolean overclocked = new AtomicBoolean(true);
            AtomicBoolean cooling = new AtomicBoolean(false);
            UIElement panel = CraftingHostPanelUI.create(config(formedState(), ItemStack.EMPTY, List.of(),
                overclocked::get, () -> overclocked.set(!overclocked.get()),
                cooling::get, () -> cooling.set(!cooling.get())));
            List<Button> buttons = panel.getChildren().stream()
                .filter(Button.class::isInstance).map(Button.class::cast).toList();
            assertEquals(2, buttons.size());
            Button overclockButton = buttons.getFirst();
            Button coolingButton = buttons.getLast();
            var syncedOverclock = (BindableValue<Boolean>) overclockButton.getChildren().getLast();
            var syncedCooling = (BindableValue<Boolean>) coolingButton.getChildren().getLast();
            overclockButton.getStyleBag().compute(0);
            coolingButton.getStyleBag().compute(0);
            assertEquals(173, ((SpriteTexture) overclockButton.getButtonStyle().baseTexture()).spritePosition.x);
            assertEquals(188, ((SpriteTexture) overclockButton.getButtonStyle().hoverTexture()).spritePosition.x);
            assertEquals(203, ((SpriteTexture) coolingButton.getButtonStyle().baseTexture()).spritePosition.x);
            assertEquals(218, ((SpriteTexture) coolingButton.getButtonStyle().hoverTexture()).spritePosition.x);

            UIEvent click = UIEvent.create(UIEvents.MOUSE_DOWN);
            click.button = 0;
            coolingButton.getBaubleServerEvent(UIEvents.MOUSE_DOWN).executor().apply(new Object[] {click});
            assertTrue(cooling.get());
            assertTrue(overclocked.get());
            overclockButton.getBaubleServerEvent(UIEvents.MOUSE_DOWN).executor().apply(new Object[] {click});
            assertFalse(overclocked.get());
            assertTrue(cooling.get());
            click.button = 1;
            coolingButton.getBaubleServerEvent(UIEvents.MOUSE_DOWN).executor().apply(new Object[] {click});
            assertTrue(cooling.get());

            UIEvent hover = UIEvent.create(UIEvents.MOUSE_ENTER);
            hover.target = coolingButton;
            UIEventDispatcher.dispatchEvent(hover, false, false, false);
            syncedCooling.setValue(true);
            syncedOverclock.setValue(false);
            overclockButton.getStyleBag().compute(1);
            coolingButton.getStyleBag().compute(1);
            assertEquals(173, ((SpriteTexture) coolingButton.getButtonStyle().baseTexture()).spritePosition.x);
            assertEquals(188, ((SpriteTexture) coolingButton.getButtonStyle().hoverTexture()).spritePosition.x);
            assertEquals(203, ((SpriteTexture) overclockButton.getButtonStyle().baseTexture()).spritePosition.x);
            GUIContext context = context();
            coolingButton.drawBackgroundAdditional(context);
            verify(context).drawTexture(eq(coolingButton.getButtonStyle().hoverTexture()),
                anyFloat(), anyFloat(), anyFloat(), anyFloat());
            UIEvent tooltip = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
            tooltip.target = coolingButton;
            UIEventDispatcher.dispatchEvent(tooltip, false, false, false);
            assertEquals("gui.neoecoae.crafting.active_cooling.on", tooltip.hoverTooltips.tooltipTexts().getFirst().getString());
        }
    }

    private static UIElement artwork(CraftingControllerPanelState state) {
        return artwork(state, ItemStack.EMPTY);
    }

    private static UIElement artwork(CraftingControllerPanelState state, ItemStack hostItem) {
        return artwork(state, hostItem, List.of());
    }

    private static UIElement artwork(CraftingControllerPanelState state, ItemStack hostItem, List<CraftingOutputDisplay> workerItems) {
        return CraftingHostPanelUI.create(config(state, hostItem, workerItems,
            () -> false, () -> {}, () -> false, () -> {})).getChildren().getFirst();
    }

    private static CraftingHostPanelUI.Config config(CraftingControllerPanelState state, ItemStack hostItem,
        List<CraftingOutputDisplay> workerItems, BooleanSupplier overclocked, Runnable toggleOverclocked,
        BooleanSupplier cooling, Runnable toggleCooling) {
        return new CraftingHostPanelUI.Config(
            Component::empty, Component::empty, () -> 1, () -> true, overclocked, () -> false, toggleOverclocked,
            cooling, toggleCooling, () -> 1, () -> 2, () -> 1, () -> 1L, () -> 0L,
            () -> 3, () -> 3, () -> 4, () -> 0L, () -> 500_000L,
            () -> 25, () -> 100, () -> 9, () -> FluidStack.EMPTY, () -> FluidStack.EMPTY, () -> 16000,
            () -> RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), List::of,
            () -> false, () -> 0, () -> {}, () -> 0, ignored -> {}, () -> state,
            () -> 1L, () -> 64L, () -> 0, () -> 0, () -> hostItem, () -> workerItems);
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
        when(context.graphics.pose()).thenReturn(spy(new PoseStack()));
        return context;
    }
}
