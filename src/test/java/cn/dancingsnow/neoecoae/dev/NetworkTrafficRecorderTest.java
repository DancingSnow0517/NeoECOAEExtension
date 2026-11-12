package cn.dancingsnow.neoecoae.dev;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NetworkTrafficRecorderTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void measuresActualAcceptanceOnceAndSeparatesChannelsAndSimulation() {
        long[] meter = {0, 0};
        var recorder = new NetworkTrafficRecorder(0, 1000, () -> meter[0], () -> meter[1]);
        var storage = mock(MEStorage.class);
        var item = AEItemKey.of(Items.STONE);
        var fluid = AEFluidKey.of(Fluids.WATER);
        when(storage.insert(any(), anyLong(), any(), any())).thenAnswer(call -> {
            meter[0] += 10;
            meter[1] += 32;
            return 3L;
        });
        assertEquals(3, recorder.insert(storage, item, 10, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(3, recorder.insert(storage, fluid, 1000, Actionable.SIMULATE, IActionSource.empty()));
        verify(storage, times(2)).insert(any(), anyLong(), any(), any());
        var actual = path(recorder, 0, "insertExecute");
        assertEquals(BigInteger.TEN, actual.get("requested"));
        assertEquals(BigInteger.valueOf(3), actual.get("returned"));
        assertEquals(10.0, actual.get("averageNanosPerCall"));
        assertEquals(32.0, actual.get("bytesPerCall"));
        assertEquals(0L, path(recorder, 0, "insertSimulate").get("calls"));
        assertEquals(1L, path(recorder, 1, "insertSimulate").get("calls"));
        assertEquals(0L, path(recorder, 1, "insertExecute").get("calls"));
    }

    @Test void failureIsUnknownAndNeverRetriesAndInvalidReturnsAreUnmodified() {
        var recorder = new NetworkTrafficRecorder(0, 1000, () -> 0, () -> -1);
        var storage = mock(MEStorage.class);
        var key = AEItemKey.of(Items.STONE);
        var failure = new IllegalStateException("notification failed after possible acceptance");
        when(storage.insert(any(), anyLong(), any(), any())).thenThrow(failure).thenReturn(11L);
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> recorder.insert(storage, key, 10, Actionable.MODULATE, IActionSource.empty())));
        assertEquals(11, recorder.insert(storage, key, 10, Actionable.MODULATE, IActionSource.empty()));
        verify(storage, times(2)).insert(any(), anyLong(), any(), any());
        var stats = path(recorder, 0, "insertExecute");
        assertEquals(BigInteger.ZERO, stats.get("returned"));
        assertEquals(1L, stats.get("exceptionsWithUnknownAcceptance"));
        assertEquals(1L, stats.get("invalidReturns"));
        assertNull(stats.get("bytesPerCall"));
    }

    @Test void exactTotalsDoNotOverflowAndExpiredOrClosedWindowsDoNotCollect() {
        long[] time = {0};
        var recorder = new NetworkTrafficRecorder(0, 1000, () -> time[0], () -> -1);
        var storage = mock(MEStorage.class);
        var key = AEItemKey.of(Items.STONE);
        when(storage.insert(any(), anyLong(), any(), any())).thenAnswer(call -> call.getArgument(1));
        recorder.tick();
        for (int i = 0; i < 2; i++) recorder.insert(storage, key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), path(recorder, 0, "insertExecute").get("returned"));
        time[0] = 1000;
        recorder.tick();
        assertEquals(10, recorder.insert(storage, key, 10, Actionable.MODULATE, IActionSource.empty()));
        time[0] = 0;
        recorder.close();
        recorder.tick();
        assertEquals(10, recorder.insert(storage, key, 10, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(2L, path(recorder, 0, "insertExecute").get("calls"));
        assertEquals(1L, recorder.snapshot().get("activeGeneratorTicks"));
        verify(storage, times(4)).insert(any(), anyLong(), any(), any());
    }

    @Test void feWrapperPreservesRealInputsAndSimulationAndReuseState() {
        var recorder = new NetworkTrafficRecorder(0, 1000, () -> 0, () -> -1);
        var delegate = mock(FeWorkload.Access.class);
        when(delegate.insert(10, true)).thenReturn(7L);
        when(delegate.insert(7, false)).thenReturn(5L);
        when(delegate.extract(6, false)).thenReturn(3L);
        when(delegate.keyReuseEnabled()).thenReturn(true);
        var wrapped = recorder.wrap(delegate);
        assertEquals(7, wrapped.insert(10, true));
        assertEquals(5, wrapped.insert(7, false));
        assertEquals(3, wrapped.extract(6, false));
        assertEquals(true, wrapped.keyReuseEnabled());
        verify(delegate).insert(10, true);
        verify(delegate).insert(7, false);
        verify(delegate).extract(6, false);
        assertEquals(BigInteger.valueOf(5), path(recorder, 0, "insertExecute").get("returned"));
        assertEquals(BigInteger.valueOf(3), path(recorder, 0, "extractExecute").get("returned"));
        assertEquals(0L, path(recorder, 0, "extractSimulate").get("calls"));
    }

    @Test void installedGeneratorTickUsesTheMeasuredPathWithoutChangingAcceptedOutput() {
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var inventory = grid.getStorageService().getInventory();
        when(inventory.insert(any(), eq(10L), eq(Actionable.MODULATE), any())).thenReturn(7L);
        var state = new GeneratorState();
        state.settings = new GeneratorSettings(1, 10, 10, 1);
        state.selected.add(AEItemKey.of(Items.STONE));
        state.recording = new NetworkTrafficRecorder(0, 1000, () -> 0, () -> -1);
        state.tick(grid, IActionSource.empty(), () -> fail("Configured resources do not require reroll"));
        assertEquals(7, state.accepted);
        assertEquals(BigInteger.valueOf(7), path(state.recording, 0, "insertExecute").get("returned"));
        verify(inventory).insert(any(), eq(10L), eq(Actionable.MODULATE), any());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> path(NetworkTrafficRecorder recorder, int channel, String path) {
        var channels = (List<Map<String, Object>>) recorder.snapshot().get("channels");
        return (Map<String, Object>) channels.get(channel).get(path);
    }
}
