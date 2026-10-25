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
            var act = CraftingPatternPreviewState.class.getDeclaredMethod("act", int.class, int.class, int.class);
            act.setAccessible(true);
            act.invoke(state, 0, 1, 0);
            var payload = ArgumentCaptor.forClass(CompoundTag.class);
            verify(f.host).rpcToServer(eq("actOnPatternPreview"), payload.capture());
            assertEquals(7, payload.getValue().getInt("revision"));
            assertEquals(0, payload.getValue().getInt("slot"));
            state.receive(f.page(false, true, true, -1, 7, 65_536, 65_535, 1));
            assertEquals(7, f.sync.clientSnapshot().revision());
            assertTrue(f.sync.clientSnapshot().entries()[1].stack().isEmpty());
            patterns.verifyNoInteractions();
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
        first.putIntArray("occupied", new int[]{0, 1});
        state.receive(first);
        var carried = new ItemStack(Items.DIAMOND);
        when(f.player.containerMenu.getCarried()).thenReturn(carried);
        try (var patterns = mockStatic(appeng.api.crafting.PatternDetailsHelper.class)) {
            patterns.when(() -> appeng.api.crafting.PatternDetailsHelper.isEncodedPattern(carried)).thenReturn(true);
            assertTrue(state.insertCarriedPattern(0));
            var payload = ArgumentCaptor.forClass(CompoundTag.class);
            verify(f.host).rpcToServer(eq("actOnPatternPreview"), payload.capture());
            assertEquals(2, payload.getValue().getInt("slot"), "an unreceived occupied slot is not free capacity");
        }
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
        var first = f.page(true, false, true, -1, 8, 3, 1, 1);
        var layout = new ListTag();
        var bus = new CompoundTag();
        bus.putLong("bus", 10);
        bus.putInt("count", 3);
        layout.add(bus);
        first.put("layout", layout);
        first.putIntArray("occupied", new int[]{1, 2});
        state.receive(first);
        assertFalse(preview.getChildren().get(0).isVisible());
        assertTrue(preview.getChildren().get(1).isVisible());
        assertFalse(preview.getChildren().get(2).isVisible());

        state.receive(f.page(false, true, true, -1, 8, 3, 2, 1));
        assertFalse(preview.getChildren().get(0).isVisible());
        assertTrue(preview.getChildren().get(1).isVisible());
        assertTrue(preview.getChildren().get(2).isVisible());
        var reopened = new CraftingPatternPreviewState(f.host, f.player);
        var reopenedPreview = CraftingInterfaceUI.patternPreview(reopened);
        assertTrue(reopenedPreview.getChildren().get(0).isVisible());
        assertFalse(reopenedPreview.getChildren().get(2).isVisible());
    }

    @Test void consecutiveShiftMovesKeepTheRemainingPhysicalAddresses() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.receive(f.page(true, true, true, -1, 7, 3, 0, 3));
        var preview = CraftingInterfaceUI.patternPreview(state);
        state.receive(f.emptyPage(false, 7, 8, 3, 0, 1));
        try (var ui = mockStatic(UIElement.class, CALLS_REAL_METHODS)) {
            ui.when(UIElement::isShiftDown).thenReturn(true);
            for (int visual : new int[]{1, 2}) {
                var event = new UIEvent(UIEvents.MOUSE_DOWN, preview.getChildren().get(visual), 0, 0, 0);
                event.target.dispatchEvent(event);
                state.endQuickMoveDrag();
            }
        }
        var moves = ArgumentCaptor.forClass(CompoundTag.class);
        verify(f.host, times(2)).rpcToServer(eq("quickMovePatternPreview"), moves.capture());
        for (int i = 0; i < 2; i++) {
            var payload = moves.getAllValues().get(i);
            assertEquals(8, payload.getInt("revision"));
            var targets = payload.getList("targets", net.minecraft.nbt.Tag.TAG_COMPOUND);
            assertEquals(1, targets.size());
            assertEquals(10, targets.getCompound(0).getLong("bus"));
            assertEquals(i + 1, targets.getCompound(0).getInt("slot"));
        }
        assertFalse(preview.getChildren().get(0).isVisible());
        assertTrue(preview.getChildren().get(1).isVisible());
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
        var event = new UIEvent(UIEvents.MOUSE_DOWN,null,0,0,0);
        event.target = first;
        event.button = 0;
        try (var ui = mockStatic(UIElement.class, CALLS_REAL_METHODS)) {
            ui.when(UIElement::isShiftDown).thenReturn(false);
            event.target.dispatchEvent(event);
        }
        var action = ArgumentCaptor.forClass(CompoundTag.class);
        verify(f.host).rpcToServer(eq("actOnPatternPreview"), action.capture());
        assertEquals(9, action.getValue().getInt("slot"));

        state.setScrollOffset(100_000);
        assertEquals(72, state.getScrollOffset());
        assertEquals(4, state.getScrollRow());
        assertFalse(preview.getChildren().get(54).isVisible());
        state.setInputSearch("missing");
        assertEquals(0, state.getScrollOffset());
        assertEquals(0, state.getMaxScrollOffset());
        assertTrue(preview.getChildren().stream().noneMatch(child -> child.isVisible()));
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
