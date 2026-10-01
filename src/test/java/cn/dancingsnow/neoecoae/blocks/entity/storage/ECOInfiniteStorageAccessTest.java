package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.*;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
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
        doNothing().when(host).setChanged();
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
