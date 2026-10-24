package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlanContext;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockRotation;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Explicit development-only acceptance of the actual twelve player disks in a separate formed structure. */
public final class OmniCellCompatibilityProbe {
    private static final BlockPos HOST = new BlockPos(0, 90, 0);
    private OmniCellCompatibilityProbe() {}

    public static void run(MinecraftServer server, Path world, String phase) throws Exception {
        check(phase.equals("omni-upgrade") || phase.equals("omni-read"), "Unknown Omni probe phase");
        Path source = Path.of(System.getProperty("neoecoae.legacyPlayerSnapshotDirectory")).toAbsolutePath().normalize();
        check(!source.equals(world.toAbsolutePath().normalize()), "Use an independent acceptance world");
        var manifest = JsonParser.parseString(Files.readString(source.resolve("omni-recovery-manifest.json"))).getAsJsonObject();
        var disks = manifest.getAsJsonArray("restored_drives");
        check(disks.size() == 12, "Expected the player's four columns of twelve disks");
        var level = server.overworld();
        var context = new MultiBlockPlanContext(4);
        NEMultiBlocks.STORAGE_SYSTEM_L9.createLevel(context);
        List<BlockPos> positions = new ArrayList<>();
        boolean upgrade = phase.equals("omni-upgrade");
        Path marker = world.resolve("omni-player-fixture-ready");
        check(upgrade != Files.exists(marker), "Choose a new world for upgrade, then reuse it for cold read");
        for (var planned : context.getPlannedBlocks()) {
            BlockPos pos = MultiBlockRotation.localToWorld(planned.relativePos(), HOST, Direction.NORTH, false);
            var state = MultiBlockRotation.rotateState(planned.targetState(), Direction.NORTH, false);
            level.getChunkAt(pos);
            if (upgrade) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            if (state.is(NEBlocks.ECO_DRIVE.get())) positions.add(pos);
        }
        positions.sort(Comparator.<BlockPos>comparingInt(pos -> pos.getX()).thenComparingInt(pos -> pos.getY()));
        check(positions.size() == 12, "Expected a real 3x4 drive array");
        Path dataDirectory = world.resolve("data/ae_universal_cell_data");
        Files.createDirectories(dataDirectory);
        if (upgrade) {
            for (int index = 0; index < disks.size(); index++) {
                var descriptor = disks.get(index).getAsJsonObject();
                String id = descriptor.get("uuid").getAsString();
                Files.copy(source.resolve("data/ae_universal_cell_data/" + id + ".dat"), dataDirectory.resolve(id + ".dat"));
                CompoundTag serialized = NbtIo.readCompressed(source.resolve(descriptor.get("fixture").getAsString()).toFile());
                ((ECODriveBlockEntity) level.getBlockEntity(positions.get(index))).load(serialized);
            }
            Files.writeString(marker, "Player disk items and inventories copied into an independent 3x4 structure.\n");
        }
        var host = (ECOStorageSystemBlockEntity) level.getBlockEntity(HOST);
        check(host != null, "Storage host was not placed");
        host.rebuildMultiblock();
        check(host.isFormed() && host.getCluster().getDrives().size() == 12, "Player-shaped structure did not form");
        long total = 0;
        int types = 0;
        for (int index = 0; index < disks.size(); index++) {
            var descriptor = disks.get(index).getAsJsonObject();
            String id = descriptor.get("uuid").getAsString();
            var drive = (ECODriveBlockEntity) level.getBlockEntity(positions.get(index));
            check(drive != null && drive.isFormed() && drive.getCellStack() != null, "Formed drive lost its disk");
            check(drive.getCellStack().getItem() == NEOmniItems.ECO_QUANTUM_OMNI_CELL_256M.get(), "Legacy quantum disk did not resolve");
            check(id.equals(drive.getCellStack().getTag().getString(AEUniversalCellData.UUID_TAG)), "Player disk identity changed");
            var cell = (ECOUniversalStorageCell) drive.getCellInventory();
            check(cell != null && cell.getTotalBytes() == 1L << 32, "Omni handler or long capacity bridge is missing");
            KeyCounter expected = new KeyCounter();
            var sourceData = NbtIo.readCompressed(source.resolve("data/ae_universal_cell_data/" + id + ".dat").toFile());
            for (Tag raw : sourceData.getCompound("data").getCompound("inventory").getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                var key = AEKey.fromTagGeneric(entry.getCompound("key"));
                check(key != null, "Actual player key could not be decoded");
                expected.add(key, entry.getLong("amount"));
                types++; total += entry.getLong("amount");
            }
            assertContents(cell, expected);
            var key = AEItemKey.of(Items.STONE);
            long existing = cell.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            check(cell.insert(key, 13, Actionable.MODULATE, IActionSource.empty()) == 13, "Restored disk rejected an insert");
            check(cell.extract(key, 13, Actionable.MODULATE, IActionSource.empty()) == 13, "Restored disk rejected extraction");
            check(cell.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()) == existing, "I/O changed existing stock");
            assertContents(cell, expected);
            cell.persistMigrationContents(level);
            ItemStack removed = drive.HANDLER.extractItem(0, 1, false);
            check(!removed.isEmpty() && drive.getCellStack() == null, "Restored disk could not be removed");
            check(drive.HANDLER.insertItem(0, removed, false).isEmpty(), "Restored disk could not be reinserted");
            final int[] mounted = {0};
            drive.mountInventories((inventory, priority) -> mounted[0]++);
            check(mounted[0] == 1 && drive.isMounted(), "A formed restored drive did not mount storage");
            assertContents((ECOUniversalStorageCell) drive.getCellInventory(), expected);
            check(id.equals(drive.getCellStack().getTag().getString(AEUniversalCellData.UUID_TAG)), "Reinsert changed player UUID");
            var clientDrive = cn.dancingsnow.neoecoae.all.NEBlockEntities.ECO_DRIVE.get().create(positions.get(index), drive.getBlockState());
            clientDrive.handleUpdateTag(drive.getUpdateTag());
            check(clientDrive.getCellStack() != null
                && clientDrive.getCellStack().getItem() == drive.getCellStack().getItem(), "Initial client sync omitted the restored disk");
            drive.setChanged();
        }
        verifyLongCapacity();
        verifyUnknownDisk(level);
        level.getDataStorage().save();
        JsonObject report = new JsonObject();
        report.addProperty("phase", phase); report.addProperty("result", "PASS");
        report.addProperty("formed_drives", positions.size()); report.addProperty("preserved_player_uuid", disks.size());
        report.addProperty("stored_types", types); report.addProperty("stored_total", total);
        report.addProperty("mounts_and_ejection_and_client_payload", "PASS");
        report.addProperty("long_capacity_and_unknown_item_retention", "PASS");
        Files.writeString(world.resolve("omni-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void assertContents(ECOUniversalStorageCell cell, KeyCounter expected) {
        KeyCounter actual = new KeyCounter(); cell.getAvailableStacks(actual);
        check(actual.size() == expected.size(), "Player resource type count changed");
        for (var entry : expected) check(actual.get(entry.getKey()) == entry.getLongValue(), "Player resource amount changed");
    }

    private static void verifyLongCapacity() {
        var cell = ECOUniversalCellHandler.INSTANCE.getCellInventory(NEOmniItems.ECO_QUANTUM_OMNI_CELL_256M.asStack(), null);
        long amount = (1L << 32) * AEItemKey.of(Items.STONE).getType().getAmountPerByte();
        var key = AEItemKey.of(Items.STONE);
        check(cell.insert(key, amount, Actionable.MODULATE, IActionSource.empty()) == amount, "Capacity truncated above Integer.MAX_VALUE");
        check(cell.getStatus() == CellState.FULL, "Long-capacity full state is wrong");
        check(cell.insert(key, 1, Actionable.SIMULATE, IActionSource.empty()) == 0, "Long-capacity disk exceeded its bound");
        check(cell.extract(key, amount, Actionable.MODULATE, IActionSource.empty()) == amount, "Long quantity could not be recovered");
    }

    private static void verifyUnknownDisk(net.minecraft.server.level.ServerLevel level) {
        BlockPos pos = HOST.offset(0, 0, 8); level.setBlockAndUpdate(pos, NEBlocks.ECO_DRIVE.getDefaultState());
        var drive = (ECODriveBlockEntity) level.getBlockEntity(pos);
        CompoundTag unknown = new ItemStack(Items.PAPER).save(new CompoundTag()); unknown.putString("id", "unavailable:optional_disk");
        CompoundTag tag = new CompoundTag(); tag.put("cellStack", unknown); drive.load(tag);
        check(drive.getCellStack() == null && !drive.canExtractCell(), "Unknown disk was accepted as an empty slot");
        CompoundTag saved = drive.saveWithFullMetadata();
        check(saved.getCompound("cellStack").equals(unknown), "Unknown disk original NBT was overwritten");
        drive.load(saved); check(drive.saveWithFullMetadata().getCompound("cellStack").equals(unknown), "Second save lost unknown disk");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
