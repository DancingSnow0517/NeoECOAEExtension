package cn.dancingsnow.neoecoae.compat.thunderbolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOThunderboltBatchBridgeTest {
    @Test
    void drainFillsEveryFreeLaneInsteadOfOneWorker() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var pattern = mock(IPatternDetails.class);
        var inputs = new KeyCounter[]{new KeyCounter()};
        var level = mock(Level.class);
        var job = UUID.randomUUID();
        try (var facade = mockStatic(ECOFastPathFacade.class)) {
            facade.when(() -> ECOFastPathFacade.prepareAllocated(
                    eq(bus), eq(pattern), eq(inputs), anyLong(), eq(level), eq(job)))
                .thenAnswer(invocation -> {
                    long requested = invocation.getArgument(3);
                    var batch = mock(ECOFastPathFacade.PreparedBatch.class);
                    when(batch.craftCount()).thenReturn(Math.min(3L, requested));
                    when(batch.submit(any())).thenReturn(true);
                    return batch;
                });
            assertEquals(0L, ECOThunderboltBatchBridge.push(bus, pattern, inputs, 8L, level, job));
            facade.verify(() -> ECOFastPathFacade.prepareAllocated(
                bus, pattern, inputs, 8L, level, job));
            facade.verify(() -> ECOFastPathFacade.prepareAllocated(
                bus, pattern, inputs, 5L, level, job));
            facade.verify(() -> ECOFastPathFacade.prepareAllocated(
                bus, pattern, inputs, 2L, level, job));
        }
    }

    @Test
    void jobIdIsForwardedToAllocatedFastPath() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var pattern = mock(IPatternDetails.class);
        var inputs = new KeyCounter[]{new KeyCounter()};
        var level = mock(Level.class);
        var job = UUID.randomUUID();
        var batch = mock(ECOFastPathFacade.PreparedBatch.class);
        when(batch.craftCount()).thenReturn(8L);
        when(batch.submit(any())).thenReturn(true);
        try (var facade = mockStatic(ECOFastPathFacade.class)) {
            facade.when(() -> ECOFastPathFacade.prepareAllocated(bus, pattern, inputs, 8L, level, job))
                .thenReturn(batch);
            assertEquals(0L, ECOThunderboltBatchBridge.push(bus, pattern, inputs, 8L, level, job));
            facade.verify(() -> ECOFastPathFacade.prepareAllocated(bus, pattern, inputs, 8L, level, job));
        }
    }

    @Test
    void ordinaryPushIsUsedWhenFastPathRejectsTheWholeRequest() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var pattern = mock(IPatternDetails.class);
        var inputs = new KeyCounter[]{new KeyCounter()};
        var level = mock(Level.class);
        var job = UUID.randomUUID();
        when(bus.pushPattern(pattern, inputs, job)).thenReturn(true);
        try (var facade = mockStatic(ECOFastPathFacade.class)) {
            facade.when(() -> ECOFastPathFacade.prepareAllocated(
                    eq(bus), eq(pattern), eq(inputs), anyLong(), eq(level), eq(job)))
                .thenReturn(null);
            assertEquals(7L, ECOThunderboltBatchBridge.push(bus, pattern, inputs, 8L, level, job));
            verify(bus, times(1)).pushPattern(pattern, inputs, job);
        }
    }

    @Test
    void rejectedOrdinaryFallbackReturnsEveryCopy() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var pattern = mock(IPatternDetails.class);
        var inputs = new KeyCounter[]{new KeyCounter()};
        var level = mock(Level.class);
        var job = UUID.randomUUID();
        when(bus.pushPattern(pattern, inputs, job)).thenReturn(false);
        try (var facade = mockStatic(ECOFastPathFacade.class)) {
            facade.when(() -> ECOFastPathFacade.prepareAllocated(
                    eq(bus), eq(pattern), eq(inputs), anyLong(), eq(level), eq(job)))
                .thenReturn(null);
            assertEquals(8L, ECOThunderboltBatchBridge.push(bus, pattern, inputs, 8L, level, job));
        }
    }

    @Test
    void capacityIsZeroWhenTheBusIsBusy() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        when(bus.isBusy()).thenReturn(true);
        assertEquals(0L, ECOThunderboltBatchBridge.capacity(bus, mock(IPatternDetails.class)));
    }

    @Test
    void capacityIsZeroForNonMolecularPatterns() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        when(bus.isBusy()).thenReturn(false);
        assertEquals(0L, ECOThunderboltBatchBridge.capacity(bus, mock(IPatternDetails.class)));
    }
}
