package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.api.crafting.PatternDetailsHelper;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.EnhancedPoseStack;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CraftingInterfaceRenderingTest {
    @BeforeAll static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        // LDLib discovers widget types from NeoForge's scan data, which the unit runner does not install.
        try (var modList = mockStatic(ModList.class);
             var registrate = mockStatic(NERegistrate.class)) {
            var mods = mock(ModList.class);
            when(mods.getAllScanData()).thenReturn(List.of());
            modList.when(ModList::get).thenReturn(mods);
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("com.lowdragmc.lowdraglib2.gui.ui.UIElement");
            Class.forName("cn.dancingsnow.neoecoae.gui.theme.NETextures");
            AccessorRegistries.init();
        }
    }

    @Test void previewDrawsItsItemChildrenAndRestoresTheClip() {
        var state = mock(CraftingPatternPreviewState.class);
        var slots = new ArrayList<PatternItemSlot>();
        when(state.createSlot(anyInt())).thenAnswer(call -> {
            var slot = spy(new PatternItemSlot());
            // Verify the real parent's traversal without needing a running graphics context for item rendering.
            doNothing().when(slot).drawInBackground(any(GUIContext.class));
            slots.add(slot);
            return slot;
        });
        var preview = CraftingInterfaceUI.patternPreview(state);
        // A running ModularUI computes inline styles before rendering each element.
        preview.getStyleBag().compute(0);
        var context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);
        context.elementColor = -1;
        preview.drawContents(context);
        assertEquals(63, slots.size());
        for (var slot : slots) {
            verify(slot).drawInBackground(context);
            slot.getStyleBag().compute(0);
            assertSame(IGuiTexture.EMPTY, slot.getStyle().backgroundTexture());
            assertSame(IGuiTexture.EMPTY, slot.getSlotStyle().slotOverlay());
        }
        verify(context, times(2)).enableScissor(anyFloat(), anyFloat(), anyFloat(), anyFloat());
        verify(context, times(2)).disableScissor();
        for (int row = 0; row <= 6; row++) {
            verify(context).drawTexture(NETextures.PATTERN_MANAGER_ROW, 0F, row * 18F, 162F, 18F);
        }
    }

    @Test void heldPatternLeftClickFindsAnOffscreenEmptySlotWhileSearching() throws Exception {
        var entries = new PatternPreviewEntry[61];
        for (int i = 0; i < 60; i++) entries[i] = patternEntry(i, new ItemStack(Items.STONE));
        entries[60] = patternEntry(60, ItemStack.EMPTY);
        var fixture = new PatternClickFixture(entries);
        fixture.state.setOutputSearch("gear");
        fixture.click(0, false);

        assertEquals(1, fixture.actions.size());
        var action = fixture.actions.getFirst();
        assertEquals(60, action.getInt("slot"));
        assertEquals(0, action.getInt("action"));
        assertEquals(0, action.getInt("button"));
        assertTrue(action.getCompound("stack").isEmpty(), "insertion must address an empty physical slot");
    }

    @Test void fullCatalogueConsumesLeftClicksWithoutReplacingOrQuickMoving() throws Exception {
        var fixture = new PatternClickFixture(patternEntry(3, new ItemStack(Items.STONE)));
        fixture.click(0, false);
        fixture.click(0, true);
        fixture.state.endQuickMoveDrag();

        assertTrue(fixture.actions.isEmpty(), "no empty slot must never fall through to a swap");
        verify(fixture.host, never()).rpcToServer(eq("quickMovePatternPreview"), any(CompoundTag.class));
        assertSame(fixture.carried, fixture.player.containerMenu.getCarried());
    }

    @Test void rightClickStillAddressesTheSelectedPhysicalPattern() throws Exception {
        var fixture = new PatternClickFixture(patternEntry(3, new ItemStack(Items.STONE)),
                patternEntry(4, ItemStack.EMPTY));
        fixture.click(1, false);

        assertEquals(1, fixture.actions.size());
        var action = fixture.actions.getFirst();
        assertEquals(3, action.getInt("slot"));
        assertEquals(1, action.getInt("button"));
        assertFalse(action.getCompound("stack").isEmpty(), "replacement must validate the selected pattern");
    }

    @Test void emptyCursorLeftClickStillTakesTheSelectedPattern() throws Exception {
        var fixture = new PatternClickFixture(patternEntry(3, new ItemStack(Items.STONE)));
        when(fixture.player.containerMenu.getCarried()).thenReturn(ItemStack.EMPTY);
        fixture.click(0, false);

        assertEquals(1, fixture.actions.size());
        assertEquals(3, fixture.actions.getFirst().getInt("slot"));
    }

    @Test void leftClickOnAReadOnlyDiskRecipeInsertsWithoutTargetingTheDisk() throws Exception {
        var disk = new PatternPreviewEntry(10, 0, ItemStack.EMPTY, "", "",
                List.of(new PatternPreviewEntry.DiskPattern(new ItemStack(Items.STONE), "iron", "gear")), true);
        var fixture = new PatternClickFixture(disk, patternEntry(1, ItemStack.EMPTY));
        fixture.click(0, false);

        assertEquals(1, fixture.actions.size());
        assertEquals(1, fixture.actions.getFirst().getInt("slot"));
        assertSame(fixture.preview, fixture.preview.getChildren().getFirst()
                .getFirstAncestorOfType(CraftingInterfaceUI.PatternPreview.class));
        try (var patterns = mockStatic(PatternDetailsHelper.class)) {
            patterns.when(() -> PatternDetailsHelper.isEncodedPattern(fixture.carried)).thenReturn(true);
            assertNotNull(fixture.preview.collectHoverTooltips(), "the insertion hint also belongs to occupied cells");
        }
    }

    private static PatternPreviewEntry patternEntry(int slot, ItemStack stack) {
        return new PatternPreviewEntry(10, slot, stack, "iron", "gear", List.of(), false);
    }

    private static final class PatternClickFixture {
        final ECOMachineInterfaceBlockEntity<?> host = mock(ECOMachineInterfaceBlockEntity.class);
        final Player player = mock(Player.class);
        final ItemStack carried = new ItemStack(Items.DIAMOND);
        final List<CompoundTag> actions = new ArrayList<>();
        final CraftingPatternPreviewState state;
        final UIElement preview;

        PatternClickFixture(PatternPreviewEntry... entries) throws Exception {
            var level = mock(Level.class);
            var clientSide = Level.class.getDeclaredField("isClientSide");
            clientSide.setAccessible(true);
            clientSide.setBoolean(level, true);
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
            when(player.level()).thenReturn(level);
            player.containerMenu = mock(AbstractContainerMenu.class);
            when(player.containerMenu.getCarried()).thenReturn(carried);
            when(host.getLevel()).thenReturn(level);
            when(host.getBlockPos()).thenReturn(BlockPos.ZERO);
            var sync = new PatternPreviewSync(host);
            when(host.getPatternPreviewSync()).thenReturn(sync);
            sync.cacheClientSnapshot(5, 7, entries);
            state = new CraftingPatternPreviewState(host, player);
            var reuse = new CompoundTag();
            reuse.putInt("menu", player.containerMenu.containerId);
            reuse.putLong("epoch", 5);
            reuse.putInt("base", 7);
            reuse.putInt("revision", 7);
            reuse.putInt("size", entries.length);
            reuse.putBoolean("reuse", true);
            state.receive(reuse);
            preview = CraftingInterfaceUI.patternPreview(state);
            doAnswer(call -> { actions.add(((CompoundTag) call.getArgument(1)).copy()); return null; })
                    .when(host).rpcToServer(eq("actOnPatternPreview"), any(CompoundTag.class));
        }

        void click(int button, boolean shift) {
            try (var patterns = mockStatic(PatternDetailsHelper.class);
                 var ui = mockStatic(UIElement.class, CALLS_REAL_METHODS)) {
                patterns.when(() -> PatternDetailsHelper.isEncodedPattern(carried)).thenReturn(true);
                ui.when(UIElement::isShiftDown).thenReturn(shift);
                var event = UIEvent.create(UIEvents.MOUSE_DOWN);
                event.target = preview.getChildren().getFirst();
                event.button = button;
                UIEventDispatcher.dispatchEvent(event, true, true, false);
                assertTrue(event.hasHandler);
            }
        }
    }

    @Test void statusTooltipUsesSeparateLinesAndNoNewlineGlyph() {
        var network = Component.literal("网络：已连接");
        var primary = Component.literal("整理完成：回收无效样板 0 个，重复样板 0 个");
        var secondary = Component.literal("背包空间不足，仍有 1 个样板待回收");
        var lines = CraftingInterfaceUI.statusTooltipLines(network, primary, secondary);
        var tooltip = HoverTooltips.empty().append(lines.toArray(Component[]::new));
        assertEquals(List.of(network, primary, secondary), tooltip.tooltipTexts());
        assertTrue(tooltip.tooltipTexts().stream().noneMatch(line -> line.getString().contains("\n")));
        assertEquals(List.of(network, primary), CraftingInterfaceUI.statusTooltipLines(network, primary, Component.empty()));
    }

    @Test void wideNativeScrollbarStaysVisibleWithoutOverflowAndReachesTheTrackBottom() {
        var state = mock(CraftingPatternPreviewState.class);
        var scrollbar = CraftingInterfaceUI.patternPreviewScrollbar(state);
        var context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);
        context.pose = mock(EnhancedPoseStack.class);
        context.pose = mock(EnhancedPoseStack.class);

        // Empty and single-page lists still draw AE2's disabled handle.
        scrollbar.drawBackgroundAdditional(context);
        verify(context.graphics).blitSprite(new ResourceLocation("ae2", "big_scroller_disabled"),
                1, 1, 12, 15);

        when(state.getMaxScrollOffset()).thenReturn(54.0);
        when(state.getScrollOffset()).thenReturn(54.0);
        scrollbar.drawBackgroundAdditional(context);
        verify(context.graphics).blitSprite(new ResourceLocation("ae2", "big_scroller"),
                1, 1, 12, 15);
        verify(context.pose).translate(0F, 91F, 0F);

        when(state.getScrollOffset()).thenReturn(9.5);
        scrollbar.drawBackgroundAdditional(context);
        verify(context.pose).translate(0F, 91F * (float) (9.5 / 54), 0F);
    }

    @Test void operationIndicatorDistinguishesServerOperationsAndRendersTheHoverRowWithoutStretching() throws Exception {
        var host = mock(ECOMachineInterfaceBlockEntity.class, CALLS_REAL_METHODS);
        var indicator = spy(new CraftingInterfaceUI.PatternOperationIndicator(host));
        assertEquals(0, indicator.getValue());
        var transfer = ECOMachineInterfaceBlockEntity.class.getDeclaredField("patternTransferInProgress");
        transfer.setAccessible(true);
        transfer.setBoolean(host, true);
        assertEquals(1, new CraftingInterfaceUI.PatternOperationIndicator(host).getValue());
        transfer.setBoolean(host, false);
        var organize = ECOMachineInterfaceBlockEntity.class.getDeclaredField("patternOrganizeInProgress");
        organize.setAccessible(true);
        organize.setBoolean(host, true);
        assertTrue(host.isPatternTransferInProgress(), "the old combined progress flag also includes organizing");
        assertEquals(2, new CraftingInterfaceUI.PatternOperationIndicator(host).getValue());
        organize.setBoolean(host, false);
        assertEquals(0, new CraftingInterfaceUI.PatternOperationIndicator(host).getValue());

        doReturn(301F).when(indicator).getPositionX();
        doReturn(84F).when(indicator).getPositionY();
        var context = mock(GUIContext.class);
        for (int state = 0; state < 3; state++) {
            final int spriteX = 8 + state * 10;
            indicator.setValue(state);
            doReturn(false).when(indicator).isHover();
            indicator.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == spriteX && sprite.spritePosition.y == 129),
                eq(301F), eq(84F), eq(10F), eq(12F));
            doReturn(true).when(indicator).isHover();
            indicator.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == spriteX && sprite.spritePosition.y == 142),
                eq(301F), eq(85F), eq(10F), eq(11F));
        }
        indicator.setValue(0);
        assertEquals(0, indicator.getValue(), "completion returns the synchronized lamp to green");
    }

    @Test void occupiedPreviewUsesTheContinuousRowInsteadOfCoveringItsGridWithAnotherSprite() {
        var state = mock(CraftingPatternPreviewState.class);
        when(state.createSlot(anyInt())).thenAnswer(call -> new PatternItemSlot());
        var preview = CraftingInterfaceUI.patternPreview(state);
        var slot = spy((PatternItemSlot) preview.getChildren().getFirst());
        slot.getStyleBag().compute(0);
        var item = new ItemStack(Items.DIAMOND);
        slot.setValue(item, false);
        doReturn(16F).when(slot).getContentWidth();
        doReturn(16F).when(slot).getContentHeight();
        var context = mock(GUIContext.class);
        context.graphics = mock(GuiGraphics.class);
        context.pose = mock(EnhancedPoseStack.class);
        context.modularUI = mock(ModularUI.class);
        context.elementColor = -1;
        var order = new ArrayList<String>();
        doAnswer(call -> { order.add("backdrop"); return null; }).when(context)
            .drawTexture(NETextures.PATTERN_MANAGER_SLOT, 0F, 0F, 16F, 16F);
        try (var drawer = mockStatic(DrawerHelper.class)) {
            drawer.when(() -> DrawerHelper.drawItemStack(context.graphics, item, 0, 0, -1, null))
                .thenAnswer(call -> { order.add("item"); return null; });
            slot.drawBackgroundAdditional(context);
            assertEquals(List.of("item"), order);
        }
    }

    @Test void gridRowsFillEmptyCellsAndMoveWithTheFractionalOffset() {
        var state = mock(CraftingPatternPreviewState.class);
        when(state.getRowOffset()).thenReturn(9.5);
        when(state.createSlot(anyInt())).thenAnswer(call -> new PatternItemSlot());
        var preview = CraftingInterfaceUI.patternPreview(state);
        var context = mock(GUIContext.class);
        preview.drawBackgroundAdditional(context);
        for (int row = 0; row <= 6; row++) {
            verify(context).drawTexture(NETextures.PATTERN_MANAGER_ROW, 0F, row * 18F - 9.5F, 162F, 18F);
        }
    }
}
