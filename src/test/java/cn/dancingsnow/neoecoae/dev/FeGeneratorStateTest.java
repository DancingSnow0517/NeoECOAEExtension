package cn.dancingsnow.neoecoae.dev;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeGeneratorStateTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void energyConfigurationAndPausedStateSurviveReloadWithFreshSessionCounters() {
        var state = new GeneratorState();
        var settings = new FeWorkloadSettings(8, 100, Long.MAX_VALUE, 256, 1, 10000, 42, true);
        state.fe = new FeWorkload(settings);
        state.feRunning = false;
        var restored = GeneratorState.read(state.write(null), null);
        assertEquals(settings, restored.fe.settings);
        assertFalse(restored.feRunning);
        assertTrue(restored.selected.isEmpty());
        assertEquals(0L, restored.fe.report().get("activeTicks"));
        restored.controlFe("fe_start");
        assertTrue(restored.feRunning);
        restored.controlFe("fe_stop");
        assertFalse(restored.feRunning);
    }

    @Test void storageFailurePausesWithoutRetryOrOrdinaryItemFallback() {
        var state = new GeneratorState();
        state.fe = new FeWorkload(new FeWorkloadSettings(1, 1, 1, 0, 1, 1, 1, false));
        state.feRunning = true;
        var calls = new AtomicInteger();
        var saves = new AtomicInteger();
        var access = new FeWorkload.Access() {
            public long insert(long amount, boolean simulate) { calls.incrementAndGet(); throw new IllegalStateException("unknown acceptance"); }
            public long extract(long amount, boolean simulate) { fail("Unexpected extraction"); return 0; }
        };
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var source = mock(IActionSource.class);
        try (var bridge = mockStatic(FeNetworkAccess.class)) {
            bridge.when(() -> FeNetworkAccess.create(grid.getStorageService().getInventory(), source)).thenReturn(access);
            state.tick(grid, source, saves::incrementAndGet);
            state.tick(grid, source, saves::incrementAndGet);
        }
        assertFalse(state.feRunning);
        assertTrue(state.feFailure.contains("unknown acceptance"));
        assertEquals(1, calls.get());
        assertEquals(1, saves.get());
        verify(grid.getStorageService().getInventory(), never()).insert(any(), anyLong(), any(), any());
        assertThrows(IllegalArgumentException.class, () -> state.controlFe("fe_start"));
        state.controlFe("fe_reset");
        assertNull(state.feFailure);
        assertFalse(state.feRunning);
    }
}
