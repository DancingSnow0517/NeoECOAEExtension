package cn.dancingsnow.neoecoae.dev;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneratorStateTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void fixedDistinctPaletteSurvivesRateChangesAndOnlyRerollsOnRequest() {
        List<AEKey> pool = List.of(AEItemKey.of(Items.STONE), AEItemKey.of(Items.DIRT), AEItemKey.of(Items.DIAMOND));
        try (var discovery = mockStatic(GeneratorPool.class)) {
            discovery.when(GeneratorPool::keys).thenReturn(pool);
            var state = new GeneratorState();
            state.configure(new GeneratorSettings(2, 1, 64, 20));
            var selected = List.copyOf(state.selected);
            assertEquals(2, selected.stream().distinct().count());
            state.configure(new GeneratorSettings(2, 100, 200, 1));
            assertEquals(selected, state.selected);
            discovery.when(GeneratorPool::keys).thenReturn(List.of(AEItemKey.of(Items.GOLD_INGOT), AEItemKey.of(Items.IRON_INGOT)));
            state.reroll();
            assertEquals(2, state.selected.size());
            assertTrue(state.selected.stream().noneMatch(selected::contains));
            var settings = state.settings;
            assertThrows(IllegalArgumentException.class, () -> state.configure(new GeneratorSettings(3, 1, 1, 1)));
            assertEquals(settings, state.settings);
        }
    }

    @Test void insertsIntoActualStorageAtIntervalAndDiscardsRejectedOutput() {
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        MEStorage storage = grid.getStorageService().getInventory();
        var source = mock(IActionSource.class);
        AEKey key = AEItemKey.of(Items.STONE);
        var state = new GeneratorState();
        state.settings = new GeneratorSettings(1, 10, 10, 3);
        state.selected.add(key);
        var saves = new AtomicInteger();
        when(storage.insert(key, 10, Actionable.MODULATE, source)).thenReturn(4L, 0L, 10L);
        state.tick(grid, source, saves::incrementAndGet);
        state.tick(grid, source, saves::incrementAndGet);
        verify(storage, never()).insert(any(), anyLong(), any(), any());
        for (int round = 0; round < 3; round++) {
            state.tick(grid, source, saves::incrementAndGet);
            assertEquals(new long[]{4, 0, 10}[round], state.accepted);
            if (round < 2) {
                state.tick(grid, source, saves::incrementAndGet);
                state.tick(grid, source, saves::incrementAndGet);
            }
        }
        verify(storage, times(3)).insert(key, 10, Actionable.MODULATE, source);
        assertEquals(List.of(key), state.selected);
        assertEquals(0, saves.get());
    }
}
