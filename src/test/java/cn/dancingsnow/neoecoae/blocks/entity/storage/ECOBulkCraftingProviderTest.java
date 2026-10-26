package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider.Batch;
import cn.dancingsnow.neoecoae.impl.storage.bulk.ECOBulkConversionPattern;
import cn.dancingsnow.neoecoae.impl.storage.bulk.ECOBulkStorageCell;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOBulkCraftingProviderTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    static class Fixture {
        final AEItemKey ingot = AEItemKey.of(Items.IRON_INGOT), nugget = AEItemKey.of(Items.IRON_NUGGET);
        final ECOStorageSystemBlockEntity host = mock(ECOStorageSystemBlockEntity.class);
        final IManagedGridNode node = mock(IManagedGridNode.class);
        final ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class);
        final ECOBulkStorageCell cell = mock(ECOBulkStorageCell.class);
        final ECOBulkConversionPattern pattern = new ECOBulkConversionPattern(ingot, 1, nugget, 9);
        final ECOBulkCraftingProvider provider;
        Fixture() {
            when(host.getMainNode()).thenReturn(node); when(host.isFormed()).thenReturn(true);
            when(node.isActive()).thenReturn(true); when(host.getTier()).thenReturn(ECOTier.L9);
            when(host.getStorageDrivesForIntegration()).thenReturn(List.of(drive));
            when(drive.getCellInventory()).thenReturn(cell); when(cell.getTier()).thenReturn(ECOTier.L9);
            when(cell.getConversionPatterns()).thenReturn(List.of(pattern)); provider = new ECOBulkCraftingProvider(host);
        }
        KeyCounter[] inputs(long count) { var counter = new KeyCounter(); counter.add(ingot, count); return new KeyCounter[] {counter}; }
        ECOBatchDispatchContext context() {
            return new ECOBatchDispatchContext(pattern, List.of(List.of(new GenericStack(ingot, 1))),
                List.of(new GenericStack(nugget, 9)), List.of(), null, null);
        }
    }
    @Test void acceptsExactlyThePublishedInputsAndPersistsOutputsOnTheAcceptingDisk() {
        var f = new Fixture(); var inputs = f.inputs(1);
        assertTrue(f.provider.pushPattern(f.pattern, inputs));
        assertTrue(inputs[0].isEmpty()); verify(f.cell).enqueueOutput(f.nugget, BigInteger.valueOf(9));
        assertFalse(f.provider.pushPattern(f.pattern, f.inputs(2)));
        when(f.cell.getConversionPatterns()).thenReturn(List.of());
        assertFalse(f.provider.pushPattern(f.pattern, f.inputs(1)));
        verify(f.cell, times(1)).enqueueOutput(any(), any());
    }
    @Test void unformedUnpoweredAndInfiniteHostsWithdrawTheirPatterns() {
        var f = new Fixture(); assertEquals(List.of(f.pattern), f.provider.getAvailablePatterns());
        when(f.host.isFormed()).thenReturn(false); assertTrue(f.provider.getAvailablePatterns().isEmpty());
        when(f.host.isFormed()).thenReturn(true); when(f.node.isActive()).thenReturn(false);
        assertTrue(f.provider.isBusy()); assertFalse(f.provider.pushPattern(f.pattern, f.inputs(1)));
        when(f.node.isActive()).thenReturn(true); when(f.host.isInfiniteMode()).thenReturn(true);
        assertTrue(f.provider.getAvailablePatterns().isEmpty());
    }
    @Test void fastPathRejectsMalformedBatchesAndQueuesTheWholeAcceptedBatch() {
        var f = new Fixture(); var preparation = f.provider.eco$prepareFastPath(f.context()); assertNotNull(preparation);
        var valid = new Batch(100, List.of(new GenericStack(f.ingot, 100)), List.of(new GenericStack(f.nugget, 900)), List.of());
        assertTrue(preparation.push(valid)); verify(f.cell).enqueueOutput(f.nugget, BigInteger.valueOf(900));
        assertFalse(preparation.push(new Batch(100, valid.inputTotal(), List.of(new GenericStack(f.nugget, 901)), List.of())));
        assertFalse(preparation.push(new Batch(100, List.of(), valid.outputTotal(), List.of())));
        verify(f.cell, times(1)).enqueueOutput(any(), any());
    }
    @Test void exactFastPathNeverTruncatesHugeConversionOutputs() {
        var f = new Fixture(); var count = BigInteger.TEN.pow(40);
        var prepared = f.provider.eco$prepareExactFastPath(f.context(), count); assertNotNull(prepared);
        assertEquals(count, prepared.craftCount()); assertTrue(prepared.dispatch().getAsBoolean());
        verify(f.cell).enqueueOutput(f.nugget, count.multiply(BigInteger.valueOf(9)));
    }
    @Test void removingTheDiskAfterPreparationRejectsDispatchWithoutAcceptingInputs() {
        var f = new Fixture(); var preparation = f.provider.eco$prepareFastPath(f.context()); assertNotNull(preparation);
        when(f.drive.getCellInventory()).thenReturn(null);
        var batch = new Batch(1, List.of(new GenericStack(f.ingot, 1)), List.of(new GenericStack(f.nugget, 9)), List.of());
        assertFalse(preparation.push(batch));
        var inputs = f.inputs(1); assertFalse(f.provider.pushPattern(f.pattern, inputs));
        assertEquals(1, inputs[0].get(f.ingot)); verify(f.cell, never()).enqueueOutput(any(), any());
    }
}
