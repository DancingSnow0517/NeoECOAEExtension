package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingPatternPreviewStateTest {
    @BeforeAll static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class);
             var registrate = mockStatic(NERegistrate.class)) {
            var modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement");
            Class.forName("cn.dancingsnow.neoecoae.gui.theme.NETextures");

        }
    }

    @Test void firstPageIsBrowsableWithoutSearchingAndFinalPageEndsLoading() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, false, true, -1, 7, 300, 0, 256));
        assertEquals(29, state.getRowCount(), "the received page must already be visible without typing");
        assertEquals("gui.neoecoae.crafting_interface.preview.rows", statusKey(state));
        state.setInputSearch("iron");
        assertEquals(29, state.getRowCount());
        state.receive(f.page(false, true, true, -1, 7, 300, 256, 44));
        assertEquals(34, state.getRowCount());
        assertEquals("gui.neoecoae.crafting_interface.preview.rows", statusKey(state));
        assertEquals(7, f.sync.clientSnapshot().revision());

        // Automation updates the snapshot in the background without hiding the completed preview.
        state.receive(f.page(true, false, false, 7, 8, 300, 0, 1));
        assertEquals("gui.neoecoae.crafting_interface.preview.rows", statusKey(state));
        assertEquals(34, state.getRowCount());
    }

    @Test void aSparseFirstPageCanBeTakenBeforeTheFinalPageWithoutBuildingSearchKeywords() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        var first = f.page(true, false, true, -1, 7, 65_536, 0, 1);
        var layout = new ListTag();
        var bus = new CompoundTag(); bus.putLong("bus", 10); bus.putInt("count", 65_536); layout.add(bus);
        first.put("layout", layout);
        first.putIntArray("occupied", new int[]{0, 65_535});
        try (var patterns = mockStatic(appeng.api.crafting.PatternDetailsHelper.class)) {
            state.receive(first);
            assertEquals(1, state.getRowCount());
            assertNull(f.sync.clientSnapshot(), "a partial view must not be cached as complete");
            patterns.verifyNoInteractions();
            var preview = CraftingInterfaceUI.patternPreview(state);
            var targets = NativePatternPreviewTestSupport.acknowledge(state);
            assertEquals(0, targets[0].slot());
            assertEquals(10, targets[0].bus());
            assertNull(((com.lowdragmc.lowdraglib.gui.widget.SlotWidget) preview.getChildren().get(0).widgets.get(0))
                .slotClick(0, net.minecraft.world.inventory.ClickType.PICKUP, f.player));
            state.receive(f.page(false, true, true, -1, 7, 65_536, 65_535, 1));
            assertEquals(7, f.sync.clientSnapshot().revision());
            assertTrue(f.sync.clientSnapshot().entries()[1].stack().isEmpty());
            patterns.verify(() -> appeng.api.crafting.PatternDetailsHelper.decodePattern(any(ItemStack.class), eq(f.level)), never());
            state.setInputSearch("iron");
            patterns.verify(() -> appeng.api.crafting.PatternDetailsHelper.decodePattern(any(ItemStack.class), eq(f.level)), times(1));
        }
    }

    @Test void insertionDuringLoadingSkipsOccupiedSlotsWhosePagesHaveNotArrived() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        var first = f.page(true, false, true, -1, 7, 3, 1, 1);
        var layout = new ListTag();
        var bus = new CompoundTag(); bus.putLong("bus", 10); bus.putInt("count", 3); layout.add(bus);
        first.put("layout", layout);
        first.putIntArray("occupied", new int[]{0, 1, 2});
        state.receive(first);
        assertFalse(state.hasEmptyPhysicalSlot(), "unreceived occupied slots are not free capacity");
        state.receive(f.emptyPage(false, 7, 8, 3, 1, 1));
        assertFalse(state.hasEmptyPhysicalSlot(), "an unrelated delta cannot change an unfinished full snapshot");
    }

    @Test void reopeningRestoresDirectionalQueriesScrollAndCachedResultsBeforeAnyPacket() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, true, true, -1, 7, 90, 0, 90));
        state.setInputSearch("iron");
        state.setOutputSearch("gear");
        state.setScrollRow(3);
        var reopened = new CraftingPatternPreviewState(f.host, f.player);
        assertEquals("iron", reopened.getInputSearch());
        assertEquals("gear", reopened.getOutputSearch());
        assertEquals(3, reopened.getScrollRow());
        assertEquals(10, reopened.getRowCount());

        var reuse = f.page(true, true, false, 7, 7, 90, 0, 0);
        reuse.putBoolean("reuse", true);
        reopened.receive(reuse);
        assertEquals(3, reopened.getScrollRow());
        assertEquals(10, reopened.getRowCount());
        reopened.setInputSearch("missing");
        reopened.setOutputSearch("");
        assertEquals(0, reopened.getRowCount());
        assertEquals(0, reopened.getScrollRow());
    }

    @Test void removingAWholeRowKeepsScrollUntilReopeningOrChangingTheQuery() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, true, true, -1, 7, 90, 0, 90));
        state.setScrollRow(4);
        state.receive(f.emptyPage(false, 7, 8, 90, 0, 9));
        assertEquals(10, state.getRowCount(), "live removals must leave the displayed rows in place");
        assertEquals(4, state.getScrollRow());
        assertEquals(72, state.getScrollOffset());

        var reopened = new CraftingPatternPreviewState(f.host, f.player);
        assertEquals(9, reopened.getRowCount(), "a new menu compacts the latest cached contents");
        assertEquals(3, reopened.getScrollRow());
        state.setOutputSearch("gear");
        assertEquals(9, state.getRowCount(), "an explicit query change also compacts the results");
        assertEquals(0, state.getScrollRow());
    }

    @Test void sparseFullRefreshKeepsExistingCellsWhilePagesArrive() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, true, true, -1, 7, 3, 0, 3));
        var preview = CraftingInterfaceUI.patternPreview(state);
        NativePatternPreviewTestSupport.acknowledge(state);
        state.nativeSlots().setStackInSlot(1, new ItemStack(Items.STONE));
        var first = f.page(true, false, true, -1, 8, 3, 1, 1);
        var layout = new ListTag();
        var bus = new CompoundTag(); bus.putLong("bus", 10); bus.putInt("count", 3); layout.add(bus);
        first.put("layout", layout);
        first.putIntArray("occupied", new int[]{1, 2});
        state.receive(first);
        var targets = NativePatternPreviewTestSupport.acknowledge(state);
        assertEquals(0, targets[0].slot(), "a temporarily empty source retains its physical native slot");
        assertEquals(1, targets[1].slot());
        assertTrue(((PatternItemSlot) preview.getChildren().get(1)).getValue().is(Items.STONE));
        state.receive(f.page(false, true, true, -1, 8, 3, 2, 1));
        targets = NativePatternPreviewTestSupport.acknowledge(state);
        assertEquals(2, targets[2].slot());
        var reopened = new CraftingPatternPreviewState(f.host, f.player);
        var reopenedPreview = CraftingInterfaceUI.patternPreview(reopened);
        var reopenedTargets = NativePatternPreviewTestSupport.acknowledge(reopened);
        assertEquals(1, reopenedTargets[0].slot());
        assertTrue(((PatternItemSlot) reopenedPreview.getChildren().get(0)).getValue().isEmpty(),
            "catalogue caching must not replace native slot synchronization");
    }

    @Test void consecutiveNativeRemovalsKeepTheRemainingPhysicalAddresses() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, true, true, -1, 7, 3, 0, 3));
        var preview = CraftingInterfaceUI.patternPreview(state);
        NativePatternPreviewTestSupport.acknowledge(state);
        for (int i = 0; i < 3; i++) state.nativeSlots().setStackInSlot(i, new ItemStack(Items.STONE));
        assertFalse(((PatternItemSlot) preview.getChildren().get(0)).getSlot().remove(1).isEmpty());
        state.receive(f.emptyPage(false, 7, 8, 3, 0, 1));
        assertFalse(((PatternItemSlot) preview.getChildren().get(1)).getSlot().remove(1).isEmpty());
        assertFalse(((PatternItemSlot) preview.getChildren().get(2)).getSlot().remove(1).isEmpty());
        var targets = NativePatternPreviewTestSupport.acknowledge(state);
        assertEquals(1, targets[1].slot());
        assertEquals(2, targets[2].slot());
        assertTrue(state.nativeSlots().getStackInSlot(0).isEmpty());
    }

    @Test void nativePickupCannotBeOverwrittenByAnOlderCatalogueRefresh() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, true, true, -1, 7, 3, 0, 3));
        var preview = CraftingInterfaceUI.patternPreview(state);
        NativePatternPreviewTestSupport.acknowledge(state);
        state.nativeSlots().setStackInSlot(0, new ItemStack(Items.STONE));
        var slot = (PatternItemSlot) preview.getChildren().get(0);
        assertEquals(1, slot.getSlot().remove(1).getCount());
        state.receive(f.page(true, true, true, -1, 7, 3, 0, 3));
        assertTrue(slot.getValue().isEmpty(), "stale search data must not flash the removed pattern back");
        assertTrue(state.nativeSlots().ready(), "unchanged physical addresses do not interrupt native interaction");
        state.receive(f.emptyPage(false, 7, 8, 3, 0, 1));
        assertTrue(slot.getValue().isEmpty());
    }

    @Test void cacheProbeWaitsForTheNewMenuAndUsesItsIdOnce() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.tick();
        assertTrue(f.requests.isEmpty(), "UI construction still sees the previous container");
        var menu = mock(ModularUIContainer.class);
        setField(menu, "containerId", 42);
        var nativeUi=mock(com.lowdragmc.lowdraglib.gui.modular.ModularUI.class);
        when(f.player.level().getBlockState(BlockPos.ZERO)).thenReturn(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        setField(nativeUi,"holder",new BlockUIMenuType.BlockUIHolder(f.player,BlockPos.ZERO));
        when(menu.getModularUI()).thenReturn(nativeUi);
        f.player.containerMenu = menu;
        state.tick();
        state.tick();
        assertEquals(1, f.requests.size());
        assertEquals(42, cn.dancingsnow.neoecoae.platform.JdkCompat.first(f.requests).getInt("menu"));
        assertEquals(-1, cn.dancingsnow.neoecoae.platform.JdkCompat.first(f.requests).getInt("revision"));
    }

    @Test void equalCoordinatesInAnotherClientWorldCannotRestoreOldPatternsOrSearches() throws Exception {
        var first = new Fixture();
        first.sync.cacheClientSnapshot(5, 7, new PatternPreviewEntry[0]);
        first.sync.cacheClientView("iron", "gear", 3);
        var other = new Fixture();
        assertNull(other.sync.clientSnapshot());
        assertEquals(PatternPreviewSync.ClientView.EMPTY, other.sync.clientView());
    }

    @Test void wheelAnimationRetainsFractionalOffsetsAndBothPartialEdgeRows() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, true, true, -1, 7, 90, 0, 90));
        var preview = CraftingInterfaceUI.patternPreview(state);
        state.scroll(1);
        state.advanceScroll(1.0 / 60);
        assertTrue(state.getScrollOffset() > 0 && state.getScrollOffset() < 18);
        assertNotEquals(Math.rint(state.getScrollOffset()), state.getScrollOffset());
        assertEquals(63, preview.getChildren().size());
        NativePatternPreviewTestSupport.acknowledge(state);
        assertTrue(cn.dancingsnow.neoecoae.platform.JdkCompat.first(preview.getChildren()).isVisible());
        assertTrue(preview.getChildren().get(54).isVisible());
        var first = cn.dancingsnow.neoecoae.platform.JdkCompat.first(preview.getChildren());
        cn.dancingsnow.neoecoae.util.UiTestSupport.restyle(first);
        assertEquals(-state.getScrollOffset(),
            cn.dancingsnow.neoecoae.util.UiTestSupport.translationY(first), 1e-5);

        state.stopScrolling();
        double clickedAt = state.getScrollOffset();
        state.advanceScroll(1);
        assertEquals(clickedAt, state.getScrollOffset());
    }

    @Test void pixelDraggingChangesTheFirstPhysicalAddressOnlyAfterCrossingARow() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, true, true, -1, 7, 90, 0, 90));
        var preview = CraftingInterfaceUI.patternPreview(state);
        state.setScrollOffset(17.5);
        assertEquals(0, state.getScrollRow());
        assertEquals(17.5, state.getRowOffset());
        state.setScrollOffset(18.5);
        assertEquals(1, state.getScrollRow());
        assertEquals(0.5, state.getRowOffset());
        var first = (PatternItemSlot) cn.dancingsnow.neoecoae.platform.JdkCompat.first(preview.getChildren());
        var targets = NativePatternPreviewTestSupport.acknowledge(state);
        assertEquals(9, targets[9].slot(), "the existing second row is reused at the top");
        assertEquals(63, targets[0].slot(), "only the row leaving the top is recycled at the bottom");

        state.setScrollOffset(100_000);
        assertEquals(72, state.getScrollOffset());
        assertEquals(4, state.getScrollRow());
        preview.arrange();
        assertTrue(preview.getChildren().get(27).getSelfPositionY() >= 108,
            "the spare row stays clipped below the viewport");
        state.setInputSearch("missing");
        assertEquals(0, state.getScrollOffset());
        assertEquals(0, state.getMaxScrollOffset());
        targets = NativePatternPreviewTestSupport.acknowledge(state);
        for (var target : targets) assertEquals(-1, target.slot());
    }

    @Test void wheelSpeedIsIndependentOfFrameRateAndFractionalWheelInputsAccumulate() throws Exception {
        double expected = animate(30);
        assertEquals(expected, animate(60), 1e-9);
        assertEquals(expected, animate(144), 1e-9);
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, true, true, -1, 7, 90, 0, 90));
        state.scroll(.25);
        state.scroll(.25);
        state.advanceScroll(1);
        assertEquals(9, state.getScrollOffset());
    }

    @Test void scrollingKeepsIconsVisibleAndNativePredictionsWhileRecyclingOnlyOneRow() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        var page = f.page(true, true, true, -1, 7, 90, 0, 90);
        var incoming = page.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND);
        var bottom = new PatternPreviewEntry(10, 63, new ItemStack(Items.DIAMOND),
            "iron", "gear", List.of(), false).encode(RegistryAccess.EMPTY);
        bottom.putInt("index", 63);
        incoming.set(63, bottom);
        state.receive(page);
        var preview = CraftingInterfaceUI.patternPreview(state);
        var before = NativePatternPreviewTestSupport.acknowledge(state);
        for (int i = 0; i < before.length; i++) state.nativeSlots().setStackInSlot(i, new ItemStack(Items.STONE));
        var removed = (PatternItemSlot) preview.getChildren().get(9);
        removed.getSlot().remove(1);
        preview.arrange();

        state.setScrollOffset(9.5);
        assertTrue(state.nativeSlots().ready());
        assertFalse(state.nativeSlots().needsView(), "sub-row motion sends no viewport request");
        assertFalse(preview.isLayoutDirty(), "moving icons must not relayout the whole menu");
        state.setScrollOffset(18.5);
        assertFalse(state.nativeSlots().ready());
        for (var child : preview.getChildren()) assertTrue(child.isVisible(), "a pending mapping cannot blank icons");
        assertTrue(removed.getDisplayedStack().isEmpty(), "scrolling must preserve a native predicted removal");
        var recycled = (PatternItemSlot) preview.getChildren().get(0);
        assertTrue(recycled.getValue().is(Items.STONE), "the native packet buffer is not rewritten by the catalogue");
        assertTrue(recycled.getDisplayedStack().is(Items.DIAMOND), "the new edge row can render before native sync");
        var after = NativePatternPreviewTestSupport.acknowledge(state);
        int changed = 0;
        for (int i = 0; i < before.length; i++) if (!before[i].equals(after[i])) changed++;
        assertEquals(9, changed, "one row scroll changes nine native addresses instead of all 63");
    }

    private static double animate(int fps) throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, true, true, -1, 7, 90, 0, 90));
        state.scroll(3);
        for (int i = 0; i < fps / 6; i++) state.advanceScroll(1.0 / fps);
        return state.getScrollOffset();
    }

    private static String statusKey(CraftingPatternPreviewState state) {
        return ((TranslatableContents) state.status().getContents()).getKey();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class Fixture {
        final ECOMachineInterfaceBlockEntity<?> host = mock(ECOMachineInterfaceBlockEntity.class);
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        final PatternPreviewSync sync;
        final List<CompoundTag> requests = new ArrayList<>();

        Fixture() throws Exception {
            setField(level, "isClientSide", true);
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
            when(player.level()).thenReturn(level);
            player.containerMenu = mock(AbstractContainerMenu.class);
            when(player.containerMenu.getCarried()).thenReturn(ItemStack.EMPTY);
            when(host.getLevel()).thenReturn(level);
            when(host.getBlockPos()).thenReturn(BlockPos.ZERO);
            sync = new PatternPreviewSync(host);
            when(host.getPatternPreviewSync()).thenReturn(sync);
            doAnswer(call -> { requests.add(((CompoundTag) call.getArgument(1)).copy()); return null; })
                    .when(host).rpcToServer(eq("openPatternPreview"), any(CompoundTag.class));
        }

        CompoundTag emptyPage(boolean full, int base, int revision, int size, int start, int count) {
            var payload = page(true, true, full, base, revision, size, start, count);
            var contents = payload.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < contents.size(); i++) contents.getCompound(i).put("stack", new CompoundTag());
            return payload;
        }

        CompoundTag page(boolean first, boolean last, boolean full, int base, int revision,
                         int size, int start, int count) {
            var payload = new CompoundTag();
            payload.putInt("menu", player.containerMenu.containerId);
            payload.putLong("epoch", 5);
            payload.putInt("revision", revision);
            payload.putInt("base", base);
            payload.putInt("size", size);
            payload.putBoolean("first", first);
            payload.putBoolean("last", last);
            payload.putBoolean("full", full);
            var entries = new ListTag();
            for (int i = start; i < start + count; i++) {
                var entry = new PatternPreviewEntry(10, i, new ItemStack(Items.STONE),
                        "iron", "gear", List.of(), false).encode(RegistryAccess.EMPTY);
                entry.putInt("index", i);
                entries.add(entry);
            }
            payload.put("entries", entries);
            return payload;
        }
    }
}
