package cn.dancingsnow.neoecoae.storage;

import static org.mockito.Mockito.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Real cells/repository with only the Forge server-discovery boundary supplied by Mockito. */
public final class StorageTestContext implements AutoCloseable {
    private static ECOStorageCellItem item;
    private static ECOStorageCellItem benchmarkItem;
    public final MinecraftServer server;
    public final ECOStorageRepository.Session session;
    public final ItemStack stack;
    public final ECOStorageCell cell;
    public final ISaveProvider owner = () -> {};

    public static synchronized void bootstrap() throws Exception {
        if (item != null) return;
        InventoryTestBootstrap.initialize();
        if (appeng.core.AEConfig.instance() == null)
            appeng.core.AEConfig.load(Files.createTempDirectory("eco-storage-test-config"));
        var type = new ECOCellType(Component.literal("Performance test cell"), 10000);
        item = Registry.register(BuiltInRegistries.ITEM, "eco_storage_test:cell",
            new ECOStorageCellItem(new Item.Properties(), ECOTier.L9, AEKeyType.items(), () -> type,
                Long.MAX_VALUE, 8, 0));
        // L9 byte capacity/accounting; only the type limit is lifted for the requested 500/1000-key experiments.
        benchmarkItem = Registry.register(BuiltInRegistries.ITEM, "eco_storage_test:benchmark_cell",
            new ECOStorageCellItem(new Item.Properties(), ECOTier.L9, AEKeyType.items(), () -> type));
    }

    public StorageTestContext(Path directory) throws Exception {
        this(directory, false);
    }

    public StorageTestContext(Path directory, boolean benchmark) throws Exception {
        bootstrap();
        Files.createDirectories(directory.resolve("data"));
        server = mock(MinecraftServer.class);
        ServerLevel level = mock(ServerLevel.class);
        when(server.isSameThread()).thenReturn(true);
        when(server.overworld()).thenReturn(level);
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(directory);
        when(level.getDataStorage()).thenReturn(new DimensionDataStorage(directory.resolve("data").toFile(),
            net.minecraft.util.datafix.DataFixers.getDataFixer()));
        stack = new ItemStack(benchmark ? benchmarkItem : item);
        try (var hooks = mockStatic(ServerLifecycleHooks.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            cell = new ECOStorageCell(stack, owner);
            session = cell.storageSession();
        }
        if (!cell.storageWritable()) throw new AssertionError("Cell must be writable");
        // Mid-game ticks must be outside Long's boxed-value cache in both benchmark versions.
        session.tick(1000);
    }

    public ECOStorageCell open(ItemStack value, ISaveProvider saveProvider) {
        try (var hooks = mockStatic(ServerLifecycleHooks.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            return new ECOStorageCell(value, saveProvider);
        }
    }

    public static AEKey[] keys(int n, boolean tagged) {
        List<AEKey> result = new ArrayList<>();
        if (tagged) {
            for (int i = 0; i < n; i++) {
                ItemStack value = new ItemStack(Items.STONE);
                value.getOrCreateTag().putInt("benchmarkVariant", i);
                result.add(AEItemKey.of(value));
            }
        } else {
            for (Item value : BuiltInRegistries.ITEM) {
                if (value == Items.AIR || value instanceof ECOStorageCellItem) continue;
                result.add(AEItemKey.of(value));
                if (result.size() == n) break;
            }
        }
        if (result.size() != n) throw new AssertionError("Not enough registered keys: " + result.size());
        return result.toArray(AEKey[]::new);
    }

    @Override public void close() { ECOStorageRepository.stopped(server); }
}
