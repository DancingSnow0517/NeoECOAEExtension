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
        field(host, "invalidInfiniteMemberIds", new ListTag());
        field(host, "invalidInfiniteMigrationSourceIds", new ListTag());
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

    private static ECOInfiniteStorageEngine memoryEngine() {
        var engine = spy(new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew()));
        doAnswer(ignored -> java.util.concurrent.CompletableFuture.completedFuture(engine.commit())).when(engine).checkpoint();
        return engine;
    }

    @Test void sourceSealMustReachDiskBeforeAnyDomainCredit() throws Exception {
        UUID domain = UUID.randomUUID();
        var tick = new AtomicLong(1);
        var source = drive(domain, 0, 0, 1);
        var host = host(domain, tick, List.of(source));
        var controller = controller(host);
        controller.migrationClock = () -> 0L;
        var engine = memoryEngine();
        mount(controller, domain, engine);
        var seal = new java.util.concurrent.CompletableFuture<Void>();
        try (var providers = mockStatic(IStorageProvider.class);
             var chunks = mockStatic(ECOStorageChunkPersistence.class)) {
            chunks.when(() -> ECOStorageChunkPersistence.checkpoint(eq(host), anyCollection())).thenReturn(seal);
            for (int i = 0; i < 3; i++) {
                controller.update();
                tick.incrementAndGet();
            }
            verify(engine, never()).insertOnce(anySet(), any(AEKey.class), anyLong());
            assertTrue(completions.isEmpty());
            seal.complete(null);
            controller.update();
            assertEquals(List.of("x0"), completions);
        }
    }

    @Test void restoreKeepsItsPlanUntilTargetWritesAndFinalDomainSnapshotSucceed() throws Exception {
        UUID domain = UUID.randomUUID();
        var tick = new AtomicLong(1);
        var target = drive(domain, 0, 0, 0);
        ECOInfiniteStorageMember.markMember(target.stack(), domain);
        var host = host(domain, tick, List.of(target));
        field(host, "hostMode", ECOStorageHostMode.RESTORING_TO_NORMAL);
        doNothing().when(host).refreshInfiniteStorageAccess();
        doNothing().when(host).exitInfiniteModeIfSafe();
        when(host.getLevel().getBlockEntity(target.entity().getBlockPos())).thenReturn(target.entity());
        var engine = memoryEngine();
        var key = key(1);
        engine.insert(key, 10, appeng.api.config.Actionable.MODULATE);
        mount(controller(host), domain, engine);
        var planSaved = new java.util.concurrent.CompletableFuture<ECOInfiniteStorageEngine.CommitResult>();
        var finalSaved = new java.util.concurrent.CompletableFuture<ECOInfiniteStorageEngine.CommitResult>();
        doReturn(planSaved, finalSaved).when(engine).checkpoint();
        var actual = (IECOStorageMigrationCell) target.entity().getCellInventory();
        var quantity = new AtomicLong();
        when(actual.getMigrationAmount(key)).thenAnswer(ignored -> quantity.get());
        when(actual.insertForMigration(eq(key), anyLong(), eq(appeng.api.config.Actionable.MODULATE), any()))
                .thenAnswer(call -> { long amount = call.getArgument(1); quantity.addAndGet(amount); return amount; });
        var simulated = mock(IECOStorageMigrationCell.class);
        var targetTier = actual.getTier();
        when(simulated.getTier()).thenReturn(targetTier);
        when(simulated.isInfiniteStorageEligible()).thenReturn(true);
        when(simulated.simulateInsertForMigration(eq(key), anyLong(), any(), anyLong(), anyLong()))
                .thenAnswer(call -> call.<Long>getArgument(1));
        var sealSaved = new java.util.concurrent.CompletableFuture<Void>();
        var failedTarget = new java.util.concurrent.CompletableFuture<Void>();
        var retryTarget = java.util.concurrent.CompletableFuture.<Void>completedFuture(null);
        try (var providers = mockStatic(IStorageProvider.class);
             var cells = mockStatic(cn.dancingsnow.neoecoae.api.storage.ECOStorageCells.class);
             var chunks = mockStatic(ECOStorageChunkPersistence.class)) {
            cells.when(() -> cn.dancingsnow.neoecoae.api.storage.ECOStorageCells.getCellInventory(any(), isNull()))
                    .thenReturn(simulated);
            chunks.when(() -> ECOStorageChunkPersistence.checkpoint(eq(host), anyCollection()))
                    .thenReturn(sealSaved, failedTarget, retryTarget);
            var restore = host.infiniteRestore();
            restore.restoreInfiniteDomainToNormalStorageIfPossible();
            assertFalse(engine.hasPendingRestore());
            assertEquals(0, quantity.get());
            sealSaved.complete(null);
            restore.restoreInfiniteDomainToNormalStorageIfPossible();
            assertTrue(engine.hasPendingRestore());
            assertEquals(0, quantity.get());
            restore.restoreInfiniteDomainToNormalStorageIfPossible();
            assertEquals(0, quantity.get(), "a pending domain plan must not touch the destination");
            planSaved.complete(new ECOInfiniteStorageEngine.CommitResult(true, 1, null));
            restore.restoreInfiniteDomainToNormalStorageIfPossible();
            assertEquals(10, quantity.get());
            assertEquals(10, engine.getRestoreAmount(key).toLongSaturated());
            failedTarget.completeExceptionally(new java.io.IOException("disk full"));
            restore.restoreInfiniteDomainToNormalStorageIfPossible();
            assertTrue(engine.hasPendingRestore());
            assertEquals(10, engine.getRestoreAmount(key).toLongSaturated());
            restore.restoreInfiniteDomainToNormalStorageIfPossible();
            restore.restoreInfiniteDomainToNormalStorageIfPossible();
            assertEquals(0, engine.getRestoreAmount(key).toLongSaturated());
            verify(host, never()).exitInfiniteModeIfSafe();
            finalSaved.complete(new ECOInfiniteStorageEngine.CommitResult(true, 2, null));
            restore.restoreInfiniteDomainToNormalStorageIfPossible();
            verify(host).exitInfiniteModeIfSafe();
            verify(actual, times(1)).insertForMigration(eq(key), eq(10L), eq(appeng.api.config.Actionable.MODULATE), any());
            assertEquals(10, quantity.get(), "retrying the failed target write must not insert the same items again");
        }
    }

    @Test void restorePreviewDoesNotCreateAnIdentityOrDirtyTheDrive() throws Exception {
        UUID domain = UUID.randomUUID();
        var target = drive(domain, 0, 0, 0);
        var tag = new CompoundTag();
        tag.putBoolean("neoecoae_infinite_member", true);
        tag.putUUID("neoecoae_infinite_domain", domain);
        target.stack().set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        var before = target.stack().copy();
        var host = host(domain, new AtomicLong(1), List.of(target));
        var engine = memoryEngine();
        engine.insert(key(1), 10, appeng.api.config.Actionable.MODULATE);
        mount(controller(host), domain, engine);
        var preview = ECOStorageInfiniteRestore.class.getDeclaredMethod("createInfiniteRestorePlan");
        preview.setAccessible(true);
        try (var cells = mockStatic(cn.dancingsnow.neoecoae.api.storage.ECOStorageCells.class)) {
            var inventory = target.entity().getCellInventory();
            cells.when(() -> cn.dancingsnow.neoecoae.api.storage.ECOStorageCells.getCellInventory(any(), isNull()))
                    .thenReturn(inventory);
            assertDoesNotThrow(() -> preview.invoke(host.infiniteRestore()));
        }
        assertTrue(ItemStack.matches(before, target.stack()));
        assertTrue(ECOInfiniteStorageMember.getIdentity(target.stack()).isEmpty());
        verify(target.entity(), never()).setChanged();
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
        var engine = memoryEngine();
        mount(controller, domain, engine);

        double last = controller.migrationProgress();
        List<Double> progress = new ArrayList<>();
        try (var providers = mockStatic(IStorageProvider.class);
             var chunks = mockStatic(ECOStorageChunkPersistence.class,
                     ignored -> java.util.concurrent.CompletableFuture.completedFuture(null))) {
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
        // The first tick persists seals; the next one shares its type budget between A and B.
        assertEquals(0D, progress.getFirst(), 1e-9);
        assertEquals((1 + 1.0D / 5) / 3, progress.get(1), 1e-9);
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

        try (var providers = mockStatic(IStorageProvider.class);
             var chunks = mockStatic(ECOStorageChunkPersistence.class,
                     ignored -> java.util.concurrent.CompletableFuture.completedFuture(null))) {
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

            mount(controller, domain, memoryEngine());
            controller.update();
            tick.incrementAndGet();
            controller.update();
            tick.incrementAndGet();
            assertNull(host.getInfiniteMigrationBlock());
            assertTrue(host.storageFaults().snapshot().stream()
                .noneMatch(fault -> fault.component().equals(ECOInfiniteStorageModeController.MIGRATION_FAULT)));
            assertEquals(List.of("x0"), completions);
        }
    }
}
