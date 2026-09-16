package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InfiniteMemberInteractionTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Class<?> type = ECOStorageSystemBlockEntity.class;
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static ECOStorageSystemBlockEntity host(UUID domain) throws Exception {
        var host = mock(ECOStorageSystemBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(true).when(host).isInfiniteMode();
        field(host, "infiniteDomainId", domain);
        field(host, "infiniteMemberIds", new HashSet<UUID>());
        return host;
    }

    @Test
    void acceptsExcludedSpecialStorageAndOwnMembersButRejectsForeignAndOrdinaryCells() throws Exception {
        UUID domain = UUID.randomUUID();
        var host = host(domain);
        ItemStack member = new ItemStack(Items.STONE);
        ECOInfiniteStorageMember.markMember(member, domain);
        assertTrue(host.canInsertStorageCell(member));
        ECOInfiniteStorageMember.markMember(member, UUID.randomUUID());
        assertFalse(host.canInsertStorageCell(member));

        ItemStack special = new ItemStack(Items.DIRT);
        IECOStorageCell cell = mock(IECOStorageCell.class);
        try (var cells = mockStatic(ECOStorageCells.class)) {
            cells.when(() -> ECOStorageCells.getCellInventory(special, null)).thenReturn(cell);
            when(cell.isInfiniteStorageEligible()).thenReturn(false);
            assertTrue(host.canInsertStorageCell(special));
            when(cell.isInfiniteStorageEligible()).thenReturn(true);
            assertFalse(host.canInsertStorageCell(special));
        }
    }

    @Test
    void missingIdentitySurvivesControllerPickupAndReturnsInAnotherSlot() throws Exception {
        UUID domain = UUID.randomUUID();
        var host = host(domain);
        ItemStack member = new ItemStack(Items.STONE);
        ECOInfiniteStorageMember.markMember(member, domain);
        field(host, "infiniteMemberIds", new HashSet<>(Set.of(ECOInfiniteStorageMember.identity(member))));
        assertEquals(1, host.getMissingInfiniteMembers());

        ItemStack controller = new ItemStack(Items.DIRT);
        field(host, "hostMode", cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode.FORMED_INFINITE);
        host.applyInfiniteDomainToControllerDrop(controller);
        var restored = host(domain);
        var load = ECOStorageSystemBlockEntity.class.getDeclaredMethod("loadInfiniteMembers", CompoundTag.class);
        load.setAccessible(true);
        load.invoke(restored, controller.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag());
        assertEquals(1, restored.getMissingInfiniteMembers());
        var drive = mock(ECODriveBlockEntity.class);
        when(drive.getCellStack()).thenReturn(member);
        var cluster = mock(NEStorageCluster.class);
        when(cluster.getDrives()).thenReturn(List.of(drive));
        field(restored, "cluster", cluster);
        assertEquals(0, restored.getMissingInfiniteMembers());
        assertFalse(restored.canInsertStorageCell(member.copy()));
        when(drive.getCellStack()).thenReturn(null);
        assertEquals(1, restored.getMissingInfiniteMembers());
        assertTrue(restored.canInsertStorageCell(member));
    }

    @Test
    void infiniteInterfaceStorageIncludesSpecialCells() {
        var host = mock(ECOStorageSystemBlockEntity.class);
        when(host.storageHostMode()).thenReturn(cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode.FORMED_INFINITE);
        when(host.canUseHostDomainStorage()).thenReturn(true);
        var tier = mock(cn.dancingsnow.neoecoae.api.IECOTier.class);
        when(host.getTier()).thenReturn(tier);
        var engine = mock(cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine.class);
        var domain = mock(appeng.api.storage.MEStorage.class);
        when(host.getInfiniteEngine()).thenReturn(engine);
        when(host.createInfiniteStorageView(engine)).thenReturn(domain);
        var cluster = mock(NEStorageCluster.class);
        var drive = mock(ECODriveBlockEntity.class);
        var cell = mock(IECOStorageCell.class);
        when(host.getCluster()).thenReturn(cluster);
        when(cluster.getDrives()).thenReturn(List.of(drive));
        when(drive.getCellInventory()).thenReturn(cell);
        when(cell.getTier()).thenReturn(tier);
        when(host.getBlockState()).thenReturn(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var storage = new ECOStorageInterfaceTransfer(host).getStorageInterfaceHostStorage();
        var key = appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE);
        var source = appeng.api.networking.security.IActionSource.empty();
        when(cell.extract(key, 64, appeng.api.config.Actionable.SIMULATE, source)).thenReturn(64L);
        assertNotNull(storage);
        assertEquals(64, storage.extract(key, 64, appeng.api.config.Actionable.SIMULATE, source));
        verify(domain).extract(key, 64, appeng.api.config.Actionable.SIMULATE, source);
    }

    @Test
    void missingMigrationSourceIsRememberedAndCanBeInsertedAgain() throws Exception {
        UUID domain = UUID.randomUUID();
        var host = host(domain);
        field(host, "hostMode",
                cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode.MIGRATING_TO_INFINITE);
        ItemStack source = new ItemStack(Items.STONE);
        UUID migration = ECOInfiniteStorageMember.beginMigration(source, domain);
        host.infiniteMigrationSourceIds().add(migration);
        var cluster = mock(NEStorageCluster.class);
        when(cluster.getDrives()).thenReturn(List.of());
        field(host, "cluster", cluster);

        assertTrue(host.canInsertStorageCell(source));

        CompoundTag saved = new CompoundTag();
        var save = ECOStorageSystemBlockEntity.class.getDeclaredMethod("saveInfiniteMembers", CompoundTag.class);
        save.setAccessible(true);
        save.invoke(host, saved);
        var restored = host(domain);
        var load = ECOStorageSystemBlockEntity.class.getDeclaredMethod("loadInfiniteMembers", CompoundTag.class);
        load.setAccessible(true);
        load.invoke(restored, saved);
        assertTrue(restored.infiniteMigrationSourceIds().contains(migration));
    }
}
