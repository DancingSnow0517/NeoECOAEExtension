package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.all.NEItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("neoecoae")
@PrefixGameTestTemplate(false)
public final class StorageReliabilityGameTest {
    @GameTest(template = "empty", timeoutTicks = 200)
    @SuppressWarnings("unchecked")
    public static void failedRecoveryPreservesUnsavedLiveQuantities(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        UUID id = UUID.randomUUID();
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("data/neoecoae_cells/" + id + ".dat");
        var cell = SavedDataECOStorageBackend.createNew(id, server.overworld().getDataStorage(), path);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        cell.insert(iron, 10, Actionable.MODULATE);
        cell.flushAndAwait();
        cell.insert(iron, 5, Actionable.MODULATE);
        cell.persistenceFailed(new IOException("temporary disk failure"));
        var cellsField = ECOCellStorageManager.class.getDeclaredField("CELLS");
        cellsField.setAccessible(true);
        var cells = (java.util.Map<UUID, SavedDataECOStorageBackend>) cellsField.get(null);
        cells.put(id, cell);
        Path blockedHistory = StorageFileHistory.previous(path);
        Files.createDirectory(blockedHistory);
        Path blocker = blockedHistory.resolve("blocker");
        Files.writeString(blocker, "cannot replace nonempty directory");
        try {
            helper.assertTrue(!ECOCellStorageManager.recover(server, id), "failed write reported recovery");
            helper.assertTrue(cell.isDegraded(), "failed write exposed stale inventory");
        } finally {
            Files.delete(blocker);
            Files.delete(blockedHistory);
        }
        helper.assertTrue(ECOCellStorageManager.recover(server, id), "retry after disk repair failed");
        helper.assertTrue(cell.getAmount(iron) == 15, "recovery replaced live quantities with stale file");
        helper.assertTrue(
                AtomicSavedDataFile.read(path)
                                .getList("entries", 10)
                                .getCompound(0)
                                .getLong("amount")
                        == 15,
                "latest quantities were not committed");
        ECOCellStorageManager.close(id);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ordinaryCellReloadAndMissingModIsolation(GameTestHelper helper) throws Exception {
        UUID id = UUID.randomUUID();
        var storage = helper.getLevel().getServer().overworld().getDataStorage();
        String name = "neoecoae_cells/" + id;
        Path path = helper.getLevel()
                .getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(name + ".dat");
        var cell = SavedDataECOStorageBackend.createNew(id, storage, path);
        storage.set(name, cell);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        cell.insert(iron, 123, Actionable.MODULATE);
        storage.save();
        var loaded = SavedDataECOStorageBackend.load(AtomicSavedDataFile.read(path), id, storage, path);
        helper.assertTrue(loaded.getAmount(iron) == 123, "real AE key failed reload");
        ECOSavedDataPersistence.unregister(loaded);
        loaded.extract(iron, 23, Actionable.MODULATE);
        loaded.flushAndAwait();
        helper.assertTrue(
                SavedDataECOStorageBackend.load(AtomicSavedDataFile.read(path), id, storage, path)
                                .getAmount(iron)
                        == 100,
                "unregistered cell failed durable flush");
        CompoundTag snapshot = loaded.save(new CompoundTag());
        CompoundTag missingKey = iron.toTagGeneric().copy();
        missingKey.putString("id", "removed_mod:missing_item");
        CompoundTag missing = new CompoundTag();
        missing.put("key", missingKey);
        missing.putLong("amount", 42);
        snapshot.getList("entries", 10).add(missing);
        var isolated = SavedDataECOStorageBackend.load(snapshot, id, storage, path);
        helper.assertTrue(!isolated.isDegraded(), "missing mod quarantined whole cell");
        helper.assertTrue(isolated.extract(iron, 10, Actionable.MODULATE) == 10, "healthy iron unavailable");
        helper.assertTrue(!isolated.canTransfer(), "unresolved cell allowed migration");
        isolated.flushAndAwait();
        helper.assertTrue(AtomicSavedDataFile.read(path).getList("entries", 10).size() == 2, "missing entry discarded");
        helper.assertTrue(Files.isRegularFile(StorageFileHistory.previous(path)), "previous snapshot absent");
        storage.set(name, isolated);
        var stack = NEItems.ECO_ITEM_CELL_256M.asStack();
        ECOCellHandle.setId(stack, id);
        var facade = new ECOStorageCell(stack, null);
        helper.assertTrue(
                facade.insert(iron, 1, Actionable.SIMULATE, IActionSource.empty()) == 0,
                "partially damaged cell advertised insertion capacity");
        helper.assertTrue(
                facade.insert(iron, 1, Actionable.MODULATE, IActionSource.empty()) == 0,
                "cell reported acceptance after backend rejection");
        helper.assertTrue(
                facade.extract(iron, -1, Actionable.MODULATE, IActionSource.empty()) == 0,
                "negative extraction changed inventory");
        helper.assertTrue(
                facade.extract(iron, 1, Actionable.MODULATE, IActionSource.empty()) == 1,
                "healthy contents became inaccessible through facade");
        helper.succeed();
    }
}
