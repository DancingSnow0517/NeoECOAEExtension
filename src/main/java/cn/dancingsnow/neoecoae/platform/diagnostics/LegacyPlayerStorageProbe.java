package cn.dancingsnow.neoecoae.platform.diagnostics;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.persistence.ECOStorageRepository;
import cn.dancingsnow.neoecoae.impl.storage.persistence.Legacy2042DomainSnapshot;
import cn.dancingsnow.neoecoae.impl.storage.persistence.Legacy2042Storage;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;

/** Opt-in acceptance of exported player storage files in a separate development world. */
final class LegacyPlayerStorageProbe {
    private LegacyPlayerStorageProbe() {}

    static void run(MinecraftServer server, Path world, String phase) throws Exception {
        check(phase.equals("player-upgrade") || phase.equals("player-read"), "Unknown player snapshot phase");
        Path export = Path.of(System.getProperty("neoecoae.legacyPlayerSnapshotDirectory")).toAbsolutePath().normalize();
        check(!export.equals(world.toAbsolutePath().normalize()), "Source and acceptance world must differ");
        JsonObject manifest = JsonParser.parseString(Files.readString(export.resolve("manifest.json"))).getAsJsonObject();
        Path prepared = export.resolve("prepared/data"), originals = export.resolve("originals/data");
        Path data = world.resolve("data");
        Path marker = world.resolve("player-storage-fixture-ready");
        if (phase.equals("player-upgrade")) {
            check(!Files.exists(marker), "Choose a fresh acceptance world");
            try (var files = Files.walk(prepared)) {
                for (Path source : files.filter(Files::isRegularFile).toList()) {
                    Path target = data.resolve(prepared.relativize(source));
                    check(!Files.exists(target), "Acceptance data already exists: " + target);
                    Files.createDirectories(target.getParent()); Files.copy(source, target);
                }
            }
            Files.writeString(marker, "Exported storage files only; not a full player-world acceptance\n");
        } else check(Files.exists(marker), "Upgrade must complete before cold reload");

        JsonObject report = new JsonObject();
        report.addProperty("phase", phase);
        report.addProperty("fixture_kind", "actual player storage file copies in independent development world; not full player-world or client acceptance");
        report.addProperty("original_world_modified", false);
        JsonArray cells = new JsonArray(), domains = new JsonArray();
        int index = 0, repaired = 0;
        for (var raw : manifest.getAsJsonArray("cells")) {
            JsonObject entry = raw.getAsJsonObject();
            String name = entry.get("file").getAsString();
            Path source = inside(prepared, name);
            UUID id = UUID.fromString(source.getFileName().toString().replace(".dat", ""));
            CompoundTag expected = NbtIo.readCompressed(source.toFile()).getCompound("data");
            check(entry.get("status").getAsString().matches("legacy|current|header_repair_available"), "Unsupported cell case");
            if (entry.get("status").getAsString().equals("header_repair_available")) {
                Path rejectedData = world.resolve("original-header-rejection/" + id);
                Path rejected = rejectedData.resolve(name);
                if (!Files.exists(rejected)) {
                    Files.createDirectories(rejected.getParent()); Files.copy(inside(originals, name), rejected);
                }
                var nativeStorage = new DimensionDataStorage(rejectedData.toFile(), DataFixers.getDataFixer());
                var session = new ECOStorageRepository.Session(nativeStorage, rejectedData);
                ItemStack damaged = disk(id);
                var access = session.open(damaged, null);
                check(!access.writable() && !access.data().flush(), "Missing original header was accepted as empty writable storage");
                nativeStorage.save();
                check(Files.mismatch(rejected, inside(originals, name)) == -1, "Rejected original was overwritten");
                repaired++;
            }
            BlockPos pos = new BlockPos(index++ * 3, 90, 0);
            server.overworld().getChunkAt(pos);
            if (phase.equals("player-upgrade")) {
                server.overworld().setBlockAndUpdate(pos, NEBlocks.ECO_DRIVE.getDefaultState());
                var drive = (ECODriveBlockEntity) server.overworld().getBlockEntity(pos);
                drive.setCellStack(disk(id));
            }
            var drive = (ECODriveBlockEntity) server.overworld().getBlockEntity(pos);
            check(drive != null && drive.getCellStack() != null, "Player disk did not survive native block-entity loading");
            var cell = (ECOStorageCell) drive.getCellInventory();
            check(cell != null && cell.storageWritable() && drive.canExtractCell(), "Player disk remains locked after header recovery");
            check(cell.storageData().amounts().size() == expected.getList("entries", Tag.TAG_COMPOUND).size(), "Player disk resource types changed");
            for (Tag value : expected.getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag resource = (CompoundTag) value;
                AEKey key = AEKey.fromTagGeneric(resource.getCompound("key"));
                check(key != null && cell.storageData().amounts().get(key).toBigInteger().equals(BigInteger.valueOf(resource.getLong("amount"))), "Player resource key/NBT/amount changed");
            }
            if (cell.storageData().amounts().isEmpty()) {
                AEKey key = AEItemKey.of(Items.DIAMOND);
                check(cell.insert(key, 7, Actionable.MODULATE, IActionSource.empty()) == 7, "Recovered empty disk cannot store items");
                check(cell.extract(key, 7, Actionable.MODULATE, IActionSource.empty()) == 7, "Recovered disk cannot return stored items");
            }
            ItemStack moved = drive.HANDLER.extractItem(0, 1, false);
            check(!moved.isEmpty() && drive.getCellStack() == null, "Recovered disk cannot leave its drive");
            check(drive.HANDLER.insertItem(0, moved, false).isEmpty(), "Recovered disk cannot be reinserted");
            check(((ECOStorageCell) drive.getCellInventory()).storageData().flush(), "Recovered disk cannot checkpoint");
            drive.setChanged();
            JsonObject result = new JsonObject(); result.addProperty("cell", id.toString());
            result.addProperty("writable", true); result.addProperty("extraction_reinsertion", true);
            result.addProperty("entries", cell.storageData().amounts().size()); cells.add(result);
        }

        int blocked = 0;
        for (var raw : manifest.getAsJsonArray("domains")) {
            JsonObject entry = raw.getAsJsonObject(); String name = entry.get("file").getAsString();
            Path source = inside(prepared, name), copied = inside(data, name);
            String filename = source.getFileName().toString();
            boolean current = filename.startsWith("neoecoae_infinite_");
            UUID id = UUID.fromString(current ? filename.substring("neoecoae_infinite_".length(), filename.length() - "_v3.dat".length())
                : filename.substring("domain_".length(), filename.length() - ".dat".length()));
            boolean unavailable = entry.get("status").getAsString().equals("blocked");
            var engine = ECOInfiniteStorageDomains.acquireExisting(server.overworld(), id);
            check(engine.isHealthy() != unavailable, "Unexpected player domain state: " + id);
            if (unavailable) {
                blocked++;
                check(!engine.flush(), "Unreadable player domain was saved as empty");
                check(Files.mismatch(source, copied) == -1, "Unreadable player domain was overwritten");
            } else if (!current) {
                CompoundTag decoded = Legacy2042Storage.domain(Legacy2042DomainSnapshot.read(source), id);
                ECOInfiniteStorageData direct = ECOInfiniteStorageData.load(decoded);
                check(direct.canMigrate(), "Legacy player record snapshot cannot be decoded");
                check(Files.mismatch(source, copied) == -1, "Original legacy domain bytes changed");
            }
            ECOInfiniteStorageDomains.release(server, id);
            JsonObject result = new JsonObject(); result.addProperty("domain", id.toString());
            result.addProperty("source", name); result.addProperty("healthy", !unavailable);
            domains.add(result);
        }
        report.add("cells", cells); report.add("domains", domains);
        report.addProperty("repaired_headers", repaired); report.addProperty("unreadable_domains_retained", blocked);
        report.addProperty("result", "PASS");
        Files.writeString(world.resolve("legacy-player-storage-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static ItemStack disk(UUID id) {
        ItemStack disk = NEItems.ECO_ITEM_CELL_256M.asStack();
        disk.getOrCreateTag().putUUID("eco_cell_id", id); disk.getOrCreateTag().putInt("eco_cell_version", 2); return disk;
    }

    private static Path inside(Path directory, String name) {
        Path target = directory.resolve(name).normalize();
        check(target.startsWith(directory.normalize()), "Snapshot path escapes its export directory"); return target;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
