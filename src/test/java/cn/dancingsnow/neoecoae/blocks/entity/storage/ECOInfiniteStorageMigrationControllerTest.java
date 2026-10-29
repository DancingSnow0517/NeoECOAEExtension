package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOInfiniteStorageMigrationControllerTest {
    private final int savedKeysPerTick = NEConfig.infiniteMigrationKeysPerTick;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @AfterEach void restoreConfig() { NEConfig.infiniteMigrationKeysPerTick = savedKeysPerTick; }

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

    private static AEKey key(int index) {
        var item = new ArrayList<>(BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR).limit(64).toList()).get(index);
        var key = spy(AEItemKey.of(item));
        var tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
        doReturn(tag).when(key).toTagGeneric(any());
        return key;
    }

    private record Drive(ECODriveBlockEntity entity, ItemStack stack) {}

    private final List<String> completions = new ArrayList<>();

    private Drive drive(UUID domain, int x, int firstKey, int keys) {
        var contents = new Object2LongLinkedOpenHashMap<AEKey>();
        for (int i = 0; i < keys; i++) contents.put(key(firstKey + i), 1L + i);
        var tier = mock(IECOTier.class);
        when(tier.supportsFeature(any())).thenReturn(true);
        var cell = mock(IECOStorageMigrationCell.class);
        when(cell.getTier()).thenReturn(tier);
        when(cell.isInfiniteStorageEligible()).thenReturn(true);
        when(cell.migrationEntries()).thenAnswer(ignored -> contents.object2LongEntrySet().iterator());
        var stack = new ItemStack(Items.DIAMOND);
        var entity = mock(ECODriveBlockEntity.class);
        when(entity.getBlockPos()).thenReturn(new BlockPos(x, 0, 0));
        when(entity.getCellStack()).thenReturn(stack);
        when(entity.getCellInventory()).thenReturn(cell);
        when(entity.getMainNode()).thenReturn(mock(IManagedGridNode.class));
        doAnswer(call -> {
            ECOInfiniteStorageMember.markMember(stack, domain);
            completions.add("x" + x);
            return null;
        }).when(entity).convertCellToInfiniteMember(domain);
        return new Drive(entity, stack);
    }

    private ECOStorageSystemBlockEntity host(UUID domain, AtomicLong tick, List<Drive> drives) throws Exception {
        var host = mock(ECOStorageSystemBlockEntity.class, CALLS_REAL_METHODS);
        var level = mock(ServerLevel.class);
        when(level.getGameTime()).thenAnswer(ignored -> tick.get());
        when(level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        var cluster = mock(NEStorageCluster.class);
        when(cluster.getDrives()).thenReturn(drives.stream().map(Drive::entity).toList());
        field(host, "level", level);
        field(host, "cluster", cluster);
        field(host, "formed", true);
        field(host, "hostMode", ECOStorageHostMode.MIGRATING_TO_INFINITE);
        field(host, "infiniteDomainId", domain);
        field(host, "infiniteRestore", new ECOStorageInfiniteRestore(host));
        field(host, "infiniteModeController", new ECOInfiniteStorageModeController(host));
        field(host, "interfaceTransfer", new ECOStorageInterfaceTransfer(host));
        field(host, "infiniteMemberIds", new HashSet<UUID>());
        field(host, "infiniteMigrationSourceIds", new HashSet<UUID>());
        field(host, "stageRunner", new ECOStorageStageRunner(host));
        doReturn(Blocks.STONE.defaultBlockState()).when(host).getBlockState();
        doReturn(false).when(host).isStorageServerStopping();
        doNothing().when(host).setChanged();
        doNothing().when(host).markForUpdate();
        doNothing().when(host).invalidateStorageStatistics();
        doNothing().when(host).refreshDriveStorageProviders();
        doNothing().when(host).rememberInfiniteMembers();
        return host;
    }

    private static ECOInfiniteStorageModeController controller(ECOStorageSystemBlockEntity host) throws Exception {
        var field = ECOStorageSystemBlockEntity.class.getDeclaredField("infiniteModeController");
        field.setAccessible(true);
        return (ECOInfiniteStorageModeController) field.get(host);
    }

    private static void mount(ECOInfiniteStorageModeController controller, UUID domain,
                              ECOInfiniteStorageEngine engine) throws Exception {
        field(controller, "mountedEngine", engine);
        field(controller, "mountedDomainId", domain);
    }

    @Test void sourcesMigrateInPositionOrderSharingOneBudgetWithMonotonicProgress() throws Exception {
        NEConfig.infiniteMigrationKeysPerTick = 4;
        UUID domain = UUID.randomUUID();
        var tick = new AtomicLong(1);
        // Listed out of order on purpose: the controller migrates by position.
        var c = drive(domain, 2, 20, 2);
        var b = drive(domain, 1, 10, 5);
        var a = drive(domain, 0, 0, 3);
        var host = host(domain, tick, List.of(c, b, a));
        var controller = controller(host);
        controller.migrationClock = () -> 0L;
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        mount(controller, domain, engine);

        double last = controller.migrationProgress();
        List<Double> progress = new ArrayList<>();
        try (var providers = mockStatic(IStorageProvider.class)) {
            for (int i = 0; i < 10 && host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE; i++) {
                controller.update();
                tick.incrementAndGet();
                double now = controller.migrationProgress();
                if (host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
                    assertTrue(now >= last, "progress never decreases");
                    progress.add(now);
                }
                last = now;
            }
        }
        assertEquals(ECOStorageHostMode.FORMED_INFINITE, host.storageHostMode());
        assertEquals(List.of("x0", "x1", "x2"), completions);
        // Tick 1: A (3 types) completes and B starts with the remaining budget, so progress is already visible.
        assertEquals((1 + 1.0D / 5) / 3, progress.getFirst(), 1e-9);
        assertTrue(host.storageFaults().snapshot().isEmpty());
        for (Drive drive : List.of(a, b, c)) assertTrue(ECOInfiniteStorageMember.isMemberOf(drive.stack(), domain));
    }

    @Test void blockedDomainIsReportedWithoutCooldownAndRecoversWhenTheDomainDoes() throws Exception {
        UUID domain = UUID.randomUUID();
        var tick = new AtomicLong(1);
        var a = drive(domain, 0, 0, 3);
        var host = host(domain, tick, List.of(a));
        var controller = controller(host);
        controller.migrationClock = () -> 0L;
        var snapshot = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        var entry = new CompoundTag();
        entry.put("key", new CompoundTag());
        entry.put("amount", new CompoundTag());
        var entries = new ListTag();
        entries.add(entry);
        snapshot.put("entries", entries);
        mount(controller, domain,
            new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.load(snapshot, RegistryAccess.EMPTY)));

        try (var providers = mockStatic(IStorageProvider.class)) {
            controller.update();
            tick.incrementAndGet();
            var block = host.getInfiniteMigrationBlock();
            assertNotNull(block);
            assertEquals(ECOInfiniteStorageEngine.MigrationBlock.Kind.UNDECODABLE_ENTRIES, block.kind());
            assertEquals("1", block.detail());
            assertTrue(host.storageFaults().snapshot().stream()
                .anyMatch(fault -> fault.component().equals(ECOInfiniteStorageModeController.MIGRATION_FAULT)));
            assertTrue(host.storageStageRetryTicks().isEmpty(), "no 200-tick cooldown for a domain block");
            assertEquals(ECOStorageHostMode.MIGRATING_TO_INFINITE, host.storageHostMode());
            assertTrue(completions.isEmpty());

            mount(controller, domain, new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew()));
            controller.update();
            tick.incrementAndGet();
            assertNull(host.getInfiniteMigrationBlock());
            assertTrue(host.storageFaults().snapshot().stream()
                .noneMatch(fault -> fault.component().equals(ECOInfiniteStorageModeController.MIGRATION_FAULT)));
            assertEquals(List.of("x0"), completions);
        }
    }
}
