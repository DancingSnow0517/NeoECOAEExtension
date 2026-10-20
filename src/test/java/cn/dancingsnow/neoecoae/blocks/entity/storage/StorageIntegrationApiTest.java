package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.networking.IManagedGridNode;
import appeng.api.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.api.storage.ICellHost;
import cn.dancingsnow.neoecoae.api.storage.IECOStoragePriorityHost;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageIntegrationApiTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void oldCellHostImplementationsExposeThePublicApi() {
        assertTrue(ICellHost.class.isAssignableFrom(cn.dancingsnow.neoecoae.util.ICellHost.class));
        assertTrue(cn.dancingsnow.neoecoae.util.ICellHost.class.isAssignableFrom(ECODriveBlockEntity.class));
        assertTrue(cn.dancingsnow.neoecoae.util.ICellHost.class.isAssignableFrom(ECOComputationDriveBlockEntity.class));
    }

    @Test
    void emptyStorageSlotHasNoExtractionRestrictionAndRejectsEmptyStack() {
        var drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        ICellHost cellHost = drive;
        assertNull(cellHost.getCellStack());
        assertTrue(cellHost.canExtractCell());
        assertNull(cellHost.getCellExtractionBlockReasonText());
        assertFalse(cellHost.isItemValid(ItemStack.EMPTY));
        cellHost.setCellStack(ItemStack.EMPTY);
        assertNull(cellHost.getCellStack());
        verify(drive, never()).setChanged();
    }

    @Test
    void computationDriveRejectsEmptyAndInvalidStacksButNullClearsTheSlot() throws Exception {
        var drive = mock(ECOComputationDriveBlockEntity.class, CALLS_REAL_METHODS);
        doNothing().when(drive).setChanged();
        var installed = new ItemStack(Items.STONE);
        var field = ECOComputationDriveBlockEntity.class.getDeclaredField("cellStack");
        field.setAccessible(true);
        field.set(drive, installed);
        ICellHost cellHost = drive;

        assertFalse(cellHost.isItemValid(ItemStack.EMPTY));
        cellHost.setCellStack(ItemStack.EMPTY);
        cellHost.setCellStack(new ItemStack(Items.DIRT));
        assertSame(installed, cellHost.getCellStack());
        verify(drive, never()).setChanged();

        assertTrue(cellHost.canExtractCell());
        assertNull(cellHost.getCellExtractionBlockReasonText());
        cellHost.setCellStack(null);
        assertNull(cellHost.getCellStack());
        verify(drive).setChanged();
    }

    @Test
    void priorityChangesPersistSyncAndRefreshControllerAndDriveMounts() throws Exception {
        var host = priorityHost(mock(ServerLevel.class));
        var cluster = mock(NEStorageCluster.class);
        var field = NEBlockEntity.class.getDeclaredField("cluster");
        field.setAccessible(true);
        field.set(host, cluster);
        var first = mock(ECODriveBlockEntity.class);
        var second = mock(ECODriveBlockEntity.class);
        var hostNode = mock(IManagedGridNode.class);
        var firstNode = mock(IManagedGridNode.class);
        var secondNode = mock(IManagedGridNode.class);
        when(host.getMainNode()).thenReturn(hostNode);
        when(first.getMainNode()).thenReturn(firstNode);
        when(second.getMainNode()).thenReturn(secondNode);
        when(cluster.getDrives()).thenReturn(List.of(first, second));
        IECOStoragePriorityHost priorityHost = host;

        try (var mounts = mockStatic(IStorageProvider.class)) {
            priorityHost.setStoragePriority(-42);
            assertEquals(-42, priorityHost.getStoragePriority());
            verify(host).setChanged();
            verify(host).markForUpdate();
            mounts.verify(() -> IStorageProvider.requestUpdate(hostNode));
            mounts.verify(() -> IStorageProvider.requestUpdate(firstNode));
            mounts.verify(() -> IStorageProvider.requestUpdate(secondNode));

            clearInvocations(host);
            mounts.clearInvocations();
            priorityHost.setStoragePriority(-42);
            verify(host, never()).setChanged();
            verify(host, never()).markForUpdate();
            mounts.verifyNoInteractions();
        }
    }

    @Test
    void unformedHostRetainsFullSignedPriorityRange() {
        var host = priorityHost(mock(ServerLevel.class));
        IECOStoragePriorityHost priorityHost = host;
        for (int priority : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE, 0}) {
            priorityHost.setStoragePriority(priority);
            assertEquals(priority, priorityHost.getStoragePriority());
        }
        verify(host, times(3)).setChanged();
        verify(host, times(3)).markForUpdate();
    }

    @Test
    void priorityDoesNotMutateClientOrDetachedHosts() {
        var client = mock(Level.class);
        when(client.isClientSide()).thenReturn(true);
        var clientHost = priorityHost(client);
        var detachedHost = priorityHost(null);
        for (var host : List.of(clientHost, detachedHost)) {
            ((IECOStoragePriorityHost) host).setStoragePriority(42);
            assertEquals(0, host.getStoragePriority());
            verify(host, never()).setChanged();
            verify(host, never()).markForUpdate();
            verify(host, never()).refreshDriveStorageProviders();
        }
    }

    private static ECOStorageSystemBlockEntity priorityHost(Level level) {
        var host = mock(ECOStorageSystemBlockEntity.class, CALLS_REAL_METHODS);
        host.setLevel(level);
        doNothing().when(host).setChanged();
        doNothing().when(host).markForUpdate();
        return host;
    }
}
