package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.CombinedExactAmountSource;
import cn.dancingsnow.neoecoae.impl.storage.infinite.*;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOInfiniteStorageAccessTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static ECOStorageSystemBlockEntity host() throws Exception {
        var host = mock(ECOStorageSystemBlockEntity.class, CALLS_REAL_METHODS);
        field(host, "formed", true);
        field(host, "hostMode", ECOStorageHostMode.FORMED_INFINITE);
        field(host, "infiniteDomainId", UUID.randomUUID());
        field(host, "infiniteRestore", new ECOStorageInfiniteRestore(host));
        field(host, "infiniteModeController", new ECOInfiniteStorageModeController(host));
        field(host, "interfaceTransfer", new ECOStorageInterfaceTransfer(host));
        field(host, "infiniteMemberIds", new HashSet<UUID>());
        field(host, "infiniteMembersDirty", true);
        doReturn(Blocks.STONE.defaultBlockState()).when(host).getBlockState();
        doReturn(false).when(host).isStorageServerStopping();
        doNothing().when(host).setChanged();
        doNothing().when(host).markForUpdate();
        doNothing().when(host).invalidateStorageStatistics();
        doNothing().when(host).refreshDriveStorageProviders();
        return host;
    }

    private static ECOInfiniteStorageModeController controller(ECOStorageSystemBlockEntity host) throws Exception {
        var field = ECOStorageSystemBlockEntity.class.getDeclaredField("infiniteModeController");
        field.setAccessible(true);
        return (ECOInfiniteStorageModeController) field.get(host);
    }

    @Test void exitFormationModeAndBackendChangesInvalidateAccessInTheSameTick() throws Exception {
        var host = host();
        var controller = controller(host);
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        field(controller, "mountedEngine", engine);
        MEStorage storage = controller.createStorageView(engine);
        var key = AEItemKey.of(Items.STONE);
        var source = IActionSource.empty();
        assertEquals(10, storage.insert(key, 10, Actionable.MODULATE, source));
        host.requestInfiniteExit();
        assertEquals(0, storage.extract(key, 1, Actionable.MODULATE, source));
        assertTrue(storage.getAvailableStacks().isEmpty());
        host.clearInfiniteExitRequest();
        assertEquals(1, storage.extract(key, 1, Actionable.MODULATE, source));
        field(host, "formed", false);
        host.refreshInfiniteStorageAccess();
        assertEquals(0, storage.insert(key, 1, Actionable.MODULATE, source));
        field(host, "formed", true);
        host.refreshInfiniteStorageAccess();
        assertEquals(1, storage.insert(key, 1, Actionable.MODULATE, source));
        host.setStorageHostMode(ECOStorageHostMode.RESTORING_TO_NORMAL);
        host.setStorageHostMode(ECOStorageHostMode.FORMED_INFINITE);
        assertEquals(0, storage.insert(key, 1, Actionable.MODULATE, source), "old generation remains invalid");
        storage = controller.createStorageView(engine);
        assertEquals(1, storage.insert(key, 1, Actionable.MODULATE, source));
        controller.release();
        field(controller, "mountedEngine", engine);
        controller.refreshHostAccess();
        assertEquals(0, storage.insert(key, 1, Actionable.MODULATE, source), "reacquiring the same engine cannot revive an old view");
        storage = controller.createStorageView(engine);
        host.setInfiniteDomainId(UUID.randomUUID());
        assertEquals(0, storage.insert(key, 1, Actionable.MODULATE, source), "domain switches close the previous mount immediately");
    }

    @Test void restoreLocksAndUnreadableDataCloseAlreadyMountedViews() throws Exception {
        var host = host();
        var controller = controller(host);
        var data = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(data);
        field(controller, "mountedEngine", engine);
        var storage = controller.createStorageView(engine);
        var key = AEItemKey.of(Items.STONE);
        var source = IActionSource.empty();
        engine.insert(key, 10, Actionable.MODULATE);
        UUID transaction = UUID.randomUUID(), target = UUID.randomUUID();
        assertTrue(engine.reserveRestores(Map.of(key, transaction), Set.of(target), Map.of(key,
                Map.of(target, new ECOInfiniteStorageEngine.RestoreTargetAmounts(0, 10, 10)))));
        assertEquals(0, storage.insert(key, 1, Actionable.MODULATE, source));
        assertTrue(storage.getAvailableStacks().isEmpty());
        assertTrue(engine.finishRestore(key, transaction));
        assertEquals(1, storage.insert(key, 1, Actionable.MODULATE, source));
        data.markUnreadable("fixture");
        assertEquals(0, storage.extract(key, 1, Actionable.MODULATE, source));
    }

    @Test void removingAnyMemberImmediatelyClosesCachedViewsUntilEveryOriginalMemberReturns() throws Exception {
        var host = host();
        field(host, "level", mock(ServerLevel.class));
        var cluster = mock(NEStorageCluster.class);
        field(host, "cluster", cluster);
        var drives = new ArrayList<ECODriveBlockEntity>();
        var members = new ArrayList<ItemStack>();
        for (int i = 0; i < 13; i++) {
            var drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
            field(drive, "cluster", cluster);
            doReturn(mock(IManagedGridNode.class)).when(drive).getMainNode();
            doReturn(true).when(drive).isItemValid(any());
            doNothing().when(drive).setChanged();
            if (i < 12) {
                var member = new ItemStack(Items.DIAMOND);
                ECOInfiniteStorageMember.markMember(member, host.infiniteDomainId());
                members.add(member);
                field(drive, "cellStack", member);
            }
            drives.add(drive);
        }
        when(cluster.getController()).thenReturn(host);
        when(cluster.getDrives()).thenReturn(drives);
        host.rememberInfiniteMembers();
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        field(controller(host), "mountedEngine", engine);
        doReturn(engine).when(host).getInfiniteEngine();
        var key = AEItemKey.of(Items.STONE);
        var stored = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
        engine.insert(key, stored, Actionable.MODULATE);
        var storage = controller(host).createStorageView(engine);
        var interfaceStorage = host.storageInterfaceTransfer().getStorageInterfaceHostStorage();
        assertNotNull(interfaceStorage);
        assertEquals(Long.MAX_VALUE, storage.getAvailableStacks().get(key));
        clearInvocations(cluster, host);
        for (int i = 0; i < 20; i++) {
            assertEquals(1, storage.extract(key, 1, Actionable.SIMULATE, IActionSource.empty()));
        }
        verify(cluster, never()).getDrives();

        try (var providers = mockStatic(IStorageProvider.class)) {
            drives.getFirst().setCellStack(null);
            assertEquals(1, host.getMissingInfiniteMembers());
            assertOffline(host, storage, key);
            assertOfflineView(interfaceStorage, key);
            verify(host, atLeastOnce()).refreshDriveStorageProviders();

            var foreign = new ItemStack(Items.DIAMOND);
            ECOInfiniteStorageMember.markMember(foreign, UUID.randomUUID());
            drives.getFirst().setCellStack(foreign);
            assertOffline(host, storage, key);

            for (int i = 0; i < 12; i++) drives.get(i).setCellStack(null);
            assertEquals(12, host.getMissingInfiniteMembers());
            assertOffline(host, storage, key);
            for (int i = 1; i < 12; i++) drives.get(i).setCellStack(members.get(i));
            assertEquals(1, host.getMissingInfiniteMembers());
            assertOffline(host, storage, key);

            // Returning the last member to another slot restores the same domain and its cached view.
            drives.get(12).setCellStack(members.getFirst());
            assertEquals(0, host.getMissingInfiniteMembers());
            assertTrue(host.canUseHostDomainStorage());
            assertTrue(host.canInsertIntoInfiniteDomain());
            assertEquals(stored, engine.getAmount(key).toBigInteger());
            assertEquals(1, storage.extract(key, 1, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(1, interfaceStorage.extract(key, 1, Actionable.MODULATE, IActionSource.empty()));
            var mounts = mock(IStorageMounts.class);
            host.mountInventories(mounts);
            verify(mounts).mount(any(MEStorage.class), anyInt());
        }
    }

    @Test void rememberedMissingMembersKeepANewStorageViewOfflineAfterReload() throws Exception {
        var host = host();
        host.infiniteMemberIds().add(UUID.randomUUID());
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        field(controller(host), "mountedEngine", engine);
        doReturn(engine).when(host).getInfiniteEngine();
        var key = AEItemKey.of(Items.STONE);
        engine.insert(key, 64, Actionable.MODULATE);
        assertOffline(host, controller(host).createStorageView(engine), key);
        assertEquals(HugeAmount.of(64), engine.getAmount(key));
    }

    @Test void missingMembersAlsoDisconnectSpecialCellsAndPreviouslyCachedInterfaceStorage() throws Exception {
        var host = host();
        field(host, "level", mock(ServerLevel.class));
        var tier = mock(IECOTier.class);
        doReturn(tier).when(host).getTier();
        var cluster = mock(NEStorageCluster.class);
        field(host, "cluster", cluster);
        when(cluster.getController()).thenReturn(host);
        var member = new ItemStack(Items.DIAMOND);
        ECOInfiniteStorageMember.markMember(member, host.infiniteDomainId());
        var memberDrive = mock(ECODriveBlockEntity.class);
        when(memberDrive.getCellStack()).thenReturn(member);
        var specialDrive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        field(specialDrive, "cluster", cluster);
        field(specialDrive, "cellStack", new ItemStack(Items.DIRT));
        doNothing().when(specialDrive).setChanged();
        var special = mock(IECOStorageCell.class);
        doReturn(special).when(specialDrive).getCellInventory();
        when(special.getTier()).thenReturn(tier);
        when(cluster.getDrives()).thenReturn(List.of(memberDrive, specialDrive));
        host.rememberInfiniteMembers();
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        field(controller(host), "mountedEngine", engine);
        doReturn(engine).when(host).getInfiniteEngine();
        var storage = host.storageInterfaceTransfer().getStorageInterfaceHostStorage();
        assertNotNull(storage);
        var mounts = mock(IStorageMounts.class);
        specialDrive.mountInventories(mounts);
        verify(mounts).mount(eq(special), anyInt());

        when(memberDrive.getCellStack()).thenReturn(null);
        host.invalidateInfiniteMembers();
        clearInvocations(special, mounts);
        assertNull(host.storageInterfaceTransfer().getStorageInterfaceHostStorage());
        assertOfflineView(storage, AEItemKey.of(Items.STONE));
        specialDrive.mountInventories(mounts);
        verifyNoInteractions(mounts);
        verify(special, never()).insert(any(), anyLong(), any(), any());
        verify(special, never()).extract(any(), anyLong(), any(), any());
        verify(special, never()).getAvailableStacks(any());

        when(memberDrive.getCellStack()).thenReturn(member);
        host.invalidateInfiniteMembers();
        assertNotNull(host.storageInterfaceTransfer().getStorageInterfaceHostStorage());
        specialDrive.mountInventories(mounts);
        verify(mounts).mount(eq(special), anyInt());
    }

    private static void assertOffline(ECOStorageSystemBlockEntity host, MEStorage storage, AEKey key) {
        assertFalse(host.canUseHostDomainStorage());
        assertFalse(host.canInsertIntoInfiniteDomain());
        assertNull(host.storageInterfaceTransfer().getStorageInterfaceHostStorage());
        var mounts = mock(IStorageMounts.class);
        host.mountInventories(mounts);
        verifyNoInteractions(mounts);
        assertOfflineView(storage, key);
    }

    private static void assertOfflineView(MEStorage storage, AEKey key) {
        var source = IActionSource.empty();
        for (var mode : Actionable.values()) {
            assertEquals(0, storage.insert(key, 1, mode, source));
            assertEquals(0, storage.extract(key, 1, mode, source));
            assertEquals(BigInteger.ZERO, ECOBigIntegerStorage.insert(storage, key,
                    BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), mode, source));
        }
        assertTrue(storage.getAvailableStacks().isEmpty());
        assertFalse(storage.isPreferredStorageFor(key, source));
        var exactSource = (CombinedExactAmountSource) storage;
        Map<AEKey, ExactAmount> exact = new HashMap<>();
        exactSource.neoecoae$visitExactAmounts(exact::put);
        assertTrue(exact.isEmpty());
        var visible = new KeyCounter();
        exactSource.neoecoae$listWithExactAmounts(visible, exact::put);
        assertTrue(visible.isEmpty());
        assertTrue(exact.isEmpty());
    }

    @Test void stableMemberRosterDoesNotRescanAndInvalidationRecordsNewIdentities() throws Exception {
        var host = host();
        var level = mock(ServerLevel.class);
        field(host, "level", level);
        var cluster = mock(NEStorageCluster.class);
        field(host, "cluster", cluster);
        var drive = mock(ECODriveBlockEntity.class);
        var first = new ItemStack(Items.STONE);
        ECOInfiniteStorageMember.markMember(first, host.infiniteDomainId());
        when(drive.getCellStack()).thenReturn(first);
        when(cluster.getDrives()).thenReturn(List.of(drive));
        host.rememberInfiniteMembers();
        assertTrue(host.infiniteMemberIds().contains(ECOInfiniteStorageMember.identity(first)));
        clearInvocations(cluster);
        for (int i = 0; i < 20; i++) host.rememberInfiniteMembers();
        verify(cluster, never()).getDrives();
        var second = new ItemStack(Items.DIRT);
        ECOInfiniteStorageMember.markMember(second, host.infiniteDomainId());
        when(drive.getCellStack()).thenReturn(second);
        host.invalidateInfiniteMembers();
        host.rememberInfiniteMembers();
        assertEquals(Set.of(ECOInfiniteStorageMember.identity(first), ECOInfiniteStorageMember.identity(second)), host.infiniteMemberIds());
    }
}
