package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.api.crafting.PatternDetailsHelper;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
            Class.forName("cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement");
            Class.forName("cn.dancingsnow.neoecoae.gui.theme.NETextures");

        }
    }

    @Test void previewDrawsItsItemChildrenAndRestoresTheClip() {
        var state = mock(CraftingPatternPreviewState.class);
        var slots = new ArrayList<PatternItemSlot>();
        when(state.createSlot(anyInt())).thenAnswer(call -> {
            var slot = spy(new PatternItemSlot());
            // Verify the real parent's traversal without needing a running graphics context for item rendering.
            doNothing().when(slot).drawInBackground(any(GuiGraphics.class),anyInt(),anyInt(),anyFloat());
            slots.add(slot);
            return slot;
        });
        var preview = CraftingInterfaceUI.patternPreview(state);
        // A running ModularUI computes inline styles before rendering each element.
        cn.dancingsnow.neoecoae.util.UiTestSupport.restyle(preview);
        var context = mock(GUIContext.class);
        cn.dancingsnow.neoecoae.util.UiTestSupport.set(context,"graphics",mock(GuiGraphics.class));
        context.elementColor = -1;
        preview.drawContents(context);
        assertEquals(63, slots.size());
        for (var slot : slots) {
            verify(slot).drawInBackground(context.graphics,(int)context.mouseX,(int)context.mouseY,context.partialTicks);
            cn.dancingsnow.neoecoae.util.UiTestSupport.restyle(slot);
            assertSame(IGuiTexture.EMPTY, slot.getStyle().backgroundTexture());
            assertSame(IGuiTexture.EMPTY, cn.dancingsnow.neoecoae.util.UiTestSupport.get(cn.dancingsnow.neoecoae.util.UiTestSupport.get(slot,"slotStyle"),"overlay"));
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
        var action = cn.dancingsnow.neoecoae.platform.JdkCompat.first(fixture.actions);
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
        var action = cn.dancingsnow.neoecoae.platform.JdkCompat.first(fixture.actions);
        assertEquals(3, action.getInt("slot"));
        assertEquals(1, action.getInt("button"));
        assertFalse(action.getCompound("stack").isEmpty(), "replacement must validate the selected pattern");
    }

    @Test void emptyCursorLeftClickStillTakesTheSelectedPattern() throws Exception {
        var fixture = new PatternClickFixture(patternEntry(3, new ItemStack(Items.STONE)));
        when(fixture.player.containerMenu.getCarried()).thenReturn(ItemStack.EMPTY);
        fixture.click(0, false);

        assertEquals(1, fixture.actions.size());
        assertEquals(3, cn.dancingsnow.neoecoae.platform.JdkCompat.first(fixture.actions).getInt("slot"));
    }

    @Test void leftClickOnAReadOnlyDiskRecipeInsertsWithoutTargetingTheDisk() throws Exception {
        var disk = new PatternPreviewEntry(10, 0, ItemStack.EMPTY, "", "",
                List.of(new PatternPreviewEntry.DiskPattern(new ItemStack(Items.STONE), "iron", "gear")), true);
        var fixture = new PatternClickFixture(disk, patternEntry(1, ItemStack.EMPTY));
        fixture.click(0, false);

        assertEquals(1, fixture.actions.size());
        assertEquals(1, cn.dancingsnow.neoecoae.platform.JdkCompat.first(fixture.actions).getInt("slot"));
        assertSame(fixture.preview, cn.dancingsnow.neoecoae.platform.JdkCompat.first(fixture.preview.getChildren())
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
                var event = new UIEvent(UIEvents.MOUSE_DOWN,null,0,0,0);
                event.target = cn.dancingsnow.neoecoae.platform.JdkCompat.first(preview.getChildren());
                event.button = button;
                event.target.dispatchEvent(event);
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
        assertEquals(List.of(network, primary, secondary), tooltip.tooltips());
        assertTrue(tooltip.tooltips().stream().noneMatch(line -> line.getString().contains("\n")));
        assertEquals(List.of(network, primary), CraftingInterfaceUI.statusTooltipLines(network, primary, Component.empty()));
    }

    @Test void wideNativeScrollbarStaysVisibleWithoutOverflowAndReachesTheTrackBottom() {
        var state = mock(CraftingPatternPreviewState.class);
        var scrollbar = CraftingInterfaceUI.patternPreviewScrollbar(state);
        var context = mock(GUIContext.class);
        cn.dancingsnow.neoecoae.util.UiTestSupport.set(context, "graphics", mock(GuiGraphics.class));
        cn.dancingsnow.neoecoae.util.UiTestSupport.set(context, "pose", spy(new PoseStack()));
        when(context.graphics.pose()).thenReturn(context.pose);
        var tessellator = mock(com.mojang.blaze3d.vertex.Tesselator.class);
        var vertices = mock(com.mojang.blaze3d.vertex.BufferBuilder.class, RETURNS_SELF);
        when(tessellator.getBuilder()).thenReturn(vertices);
        try (var render = mockStatic(com.mojang.blaze3d.systems.RenderSystem.class);
             var buffers = mockStatic(com.mojang.blaze3d.vertex.Tesselator.class)) {
            buffers.when(com.mojang.blaze3d.vertex.Tesselator::getInstance).thenReturn(tessellator);
            // Exercise the actual AE2 blitter while keeping OpenGL outside this headless test.
            scrollbar.drawBackgroundAdditional(context);
            verify(vertices, atLeastOnce()).uv(12F / 256, 0F);
            assertEquals(12, cn.dancingsnow.neoecoae.client.gui.widget.NEScrollbars.BIG.disabledBlitter().getSrcWidth());
            assertEquals(15, cn.dancingsnow.neoecoae.client.gui.widget.NEScrollbars.BIG.disabledBlitter().getSrcHeight());
            clearInvocations(vertices);

            when(state.getMaxScrollOffset()).thenReturn(54.0);
            when(state.getScrollOffset()).thenReturn(54.0);
            scrollbar.drawBackgroundAdditional(context);
            verify(vertices, atLeastOnce()).uv(0F, 0F);
            verify(context.pose).translate(0F, 91F, 0F);

            when(state.getScrollOffset()).thenReturn(9.5);
            scrollbar.drawBackgroundAdditional(context);
            verify(context.pose).translate(0F, 91F * (float) (9.5 / 54), 0F);
        }
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

        doReturn(301).when(indicator).getPositionX();
        doReturn(84).when(indicator).getPositionY();
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
        var order = new ArrayList<String>();
        var state = mock(CraftingPatternPreviewState.class);
        when(state.createSlot(anyInt())).thenAnswer(call -> new PatternItemSlot() {
            @Override protected void drawItemStack(GUIContext context, ItemStack item) { order.add("item"); }
        });
        var preview = CraftingInterfaceUI.patternPreview(state);
        var slot = (PatternItemSlot) preview.getChildren().get(0);
        cn.dancingsnow.neoecoae.util.UiTestSupport.restyle(slot);
        slot.setValue(new ItemStack(Items.DIAMOND), false);
        var context = mock(GUIContext.class);
        cn.dancingsnow.neoecoae.util.UiTestSupport.set(context, "graphics", mock(GuiGraphics.class));
        when(context.graphics.pose()).thenReturn(new PoseStack());
        doAnswer(call -> { order.add("backdrop"); return null; }).when(context)
            .drawTexture(NETextures.PATTERN_MANAGER_SLOT, 0F, 0F, 16F, 16F);
        slot.drawBackgroundAdditional(context);
        slot.widgets.get(0).drawInBackground(context.graphics, 0, 0, 0);
        assertEquals(List.of("item"), order);
        assertSame(IGuiTexture.EMPTY, slot.getStyle().backgroundTexture());
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
