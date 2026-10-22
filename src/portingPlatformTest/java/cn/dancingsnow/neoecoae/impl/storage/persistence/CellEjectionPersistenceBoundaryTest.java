package cn.dancingsnow.neoecoae.impl.storage.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CellEjectionPersistenceBoundaryTest {
    @BeforeAll static void bootstrap() { PlatformTestBootstrap.initialize(); }
    @Test void failedCheckpointRejectsExtractionWithoutCreatingACopy() {
        var host = mock(ICellHost.class); ItemStack original = new ItemStack(Items.PAPER);
        when(host.getCellStack()).thenReturn(original); when(host.canExtractCell()).thenReturn(true);
        var handler = new CellHostItemHandler(host);
        assertTrue(handler.extractItem(0, 1, false).isEmpty());
        assertSame(original, host.getCellStack()); assertEquals(1, original.getCount());
    }
    @Test void simulationDoesNotInvokeThePersistenceBoundary() {
        var host = mock(ICellHost.class); when(host.getCellStack()).thenReturn(new ItemStack(Items.PAPER));
        when(host.canExtractCell()).thenReturn(true);
        assertFalse(new CellHostItemHandler(host).extractItem(0, 1, true).isEmpty());
        verify(host, never()).setCellStack(any());
    }
    @Test void successfulEjectionCopiesTheLatestPersistedReference() {
        var host = mock(ICellHost.class); ItemStack original = new ItemStack(Items.PAPER);
        java.util.concurrent.atomic.AtomicReference<ItemStack> slot = new java.util.concurrent.atomic.AtomicReference<>(original);
        when(host.getCellStack()).thenAnswer(invocation -> slot.get()); when(host.canExtractCell()).thenReturn(true);
        doAnswer(invocation -> { original.getOrCreateTag().putLong("savedRevision", 42); slot.set(null); return null; })
            .when(host).setCellStack(null);
        var extracted = new CellHostItemHandler(host).extractItem(0, 1, false);
        assertEquals(42, extracted.getTag().getLong("savedRevision")); assertNull(host.getCellStack());
    }
    @Test void rejectedInstallationDoesNotConsumeTheOfferedDisk() {
        var host = mock(ICellHost.class); when(host.isItemValid(any())).thenReturn(true);
        var offered = new ItemStack(Items.PAPER, 2);
        assertSame(offered, new CellHostItemHandler(host).insertItem(0, offered, false));
        assertEquals(2, offered.getCount());
    }
}
