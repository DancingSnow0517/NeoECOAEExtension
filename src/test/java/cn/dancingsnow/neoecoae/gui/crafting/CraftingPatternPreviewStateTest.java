package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingPatternPreviewStateTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

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

    @Test void cacheProbeWaitsForTheNewMenuAndUsesItsIdOnce() throws Exception {
        var f = new Fixture();
        var state = new CraftingPatternPreviewState(f.host, f.player);
        state.tick();
        assertTrue(f.requests.isEmpty(), "UI construction still sees the previous container");
        var menu = mock(ModularUIContainerMenu.class);
        setField(menu, "containerId", 42);
        setField(menu, "uiHolder", new BlockUIMenuType.BlockUIHolder(null, f.player, BlockPos.ZERO, null));
        f.player.containerMenu = menu;
        state.tick();
        state.tick();
        assertEquals(1, f.requests.size());
        assertEquals(42, f.requests.getFirst().getInt("menu"));
        assertEquals(-1, f.requests.getFirst().getInt("revision"));
    }

    @Test void equalCoordinatesInAnotherClientWorldCannotRestoreOldPatternsOrSearches() throws Exception {
        var first = new Fixture();
        first.sync.cacheClientSnapshot(5, 7, new PatternPreviewEntry[0]);
        first.sync.cacheClientView("iron", "gear", 3);
        var other = new Fixture();
        assertNull(other.sync.clientSnapshot());
        assertEquals(PatternPreviewSync.ClientView.EMPTY, other.sync.clientView());
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
            when(host.getLevel()).thenReturn(level);
            when(host.getBlockPos()).thenReturn(BlockPos.ZERO);
            sync = new PatternPreviewSync(host);
            when(host.getPatternPreviewSync()).thenReturn(sync);
            doAnswer(call -> { requests.add(((CompoundTag) call.getArgument(1)).copy()); return null; })
                    .when(host).rpcToServer(eq("openPatternPreview"), any(CompoundTag.class));
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
