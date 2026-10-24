package cn.dancingsnow.neoecoae.platform.diagnostics;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.util.SettingsFrom;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.persistence.ECOCellReference;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.platform.nbt.NEAtomicNbtFile;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.items.ItemStackHandler;
import org.slf4j.LoggerFactory;

/** Opt-in, source-backed 20.4.2 format fixtures through actual Forge/LDLib block-entity lifecycles. */
public final class Legacy2042ServerProbe {
    private static final BlockPos DRIVE = new BlockPos(0, 90, 0);
    private static final BlockPos BUS = new BlockPos(3, 90, 0);
    private static final BlockPos CONTROLLER = new BlockPos(6, 90, 0);
    private static final BlockPos BLOCKED_DRIVE = new BlockPos(9, 90, 0);
    private static final int HIGH_SLOT = 449;
    private Legacy2042ServerProbe() {}

    public static void started(ServerStartedEvent event) {
        String phase = System.getProperty("neoecoae.legacy2042Smoke", "");
        if (net.minecraftforge.fml.loading.FMLEnvironment.production || phase.isEmpty()) return;
        var server = event.getServer();
        try {
            Path root = server.getWorldPath(LevelResource.ROOT);
            check(root.toAbsolutePath().normalize().toString().contains("legacy-2042-acceptance-"), "Use a dedicated legacy-2042-acceptance-* world");
            run(server, phase, root);
            LoggerFactory.getLogger(Legacy2042ServerProbe.class).info("LEGACY_2042_ACCEPTANCE_PASS phase={}", phase);
        } catch (Throwable failure) {
            LoggerFactory.getLogger(Legacy2042ServerProbe.class).error("LEGACY_2042_ACCEPTANCE_FAIL phase=" + phase, failure);
            throw new IllegalStateException("20.4.2 compatibility acceptance failed", failure);
        } finally { server.halt(false); }
    }

    private static AEKey key() {
        ItemStack diamond = new ItemStack(Items.DIAMOND); diamond.getOrCreateTag().putInt("legacy_grade", 7);
        return AEItemKey.of(diamond);
    }
    private static BigInteger huge() { return BigInteger.TEN.pow(180).add(BigInteger.valueOf(42)); }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    private static void run(MinecraftServer server, String phase, Path root) throws Exception {
        if (phase.startsWith("omni-")) {
            cn.dancingsnow.neoecoae.integration.ae2omnicells.OmniCellCompatibilityProbe.run(server, root, phase);
            return;
        }
        if (phase.startsWith("player-")) {
            LegacyPlayerStorageProbe.run(server, root, phase);
            return;
        }
        check(phase.equals("upgrade") || phase.equals("read"), "Unknown phase");
        Path fixtureFile = root.resolve("legacy-2042-fixture.dat");
        ServerLevel world = server.overworld(); CompoundTag fixture;
        if (phase.equals("upgrade")) {
            check(!Files.exists(fixtureFile), "Fixture already exists; choose a new acceptance world");
            fixture = prepare(world, root);
            NEAtomicNbtFile.write(fixture, fixtureFile);
        } else fixture = NbtIo.readCompressed(fixtureFile.toFile());
        world.getChunkAt(DRIVE);
        var drive = (ECODriveBlockEntity) world.getBlockEntity(DRIVE);
        check(drive != null && drive.getCellStack() != null, "Drive cell did not survive native loading");
        var cell = (ECOStorageCell) drive.getCellInventory();
        check(cell != null && cell.storageWritable(), "Converted ordinary cell is unavailable");
        check(cell.getMigrationAmount(key()) == 800, "Ordinary inventory or exact key NBT changed");
        check(ECOCellReference.id(drive.getCellStack()).equals(fixture.getUUID("cell")), "Cell UUID changed");
        var domainId = fixture.getUUID("domain");
        var domain = ECOInfiniteStorageDomains.acquireExisting(world, domainId);
        check(domain.isHealthy() && domain.getAmount(key()).toBigInteger().equals(huge().subtract(BigInteger.valueOf(17))), "Infinite amount changed across upgrade/restart");
        check(domain.hasMigrationReceipt(fixture.getUUID("receipt")), "Release receipt was lost");
        var bus = (ECOCraftingPatternBusBlockEntity) world.getBlockEntity(BUS);
        check(bus != null && bus.getPageCount() == 8, "Legacy bus page count changed");
        check(ItemStack.matches(ItemStack.of(fixture.getCompound("firstPattern")), bus.getPatternSlotInventory().getStackInSlot(0)), "First pattern NBT changed");
        check(ItemStack.matches(ItemStack.of(fixture.getCompound("highPattern")), bus.getPatternSlotInventory().getStackInSlot(HIGH_SLOT)), "High-slot pattern NBT changed");
        bus.flushScheduledPatternDetails();
        check(bus.getAvailablePatterns().size() == 2, "Imported patterns are not advertised by the real provider");
        var controller = (ECOStorageSystemBlockEntity) world.getBlockEntity(CONTROLLER);
        check(controller != null && controller.getStoragePriority() == 17, "Controller priority was not migrated");
        CompoundTag savedController = controller.saveWithFullMetadata();
        check(domainId.equals(savedController.getUUID("infiniteDomainId")), "Controller domain changed");
        var component = savedController.getCompound("infiniteComponentInventory").getList("inventory", Tag.TAG_COMPOUND);
        check(component.size() == 1 && ItemStack.of(component.getCompound(0)).getCount() == 64, "Controller components were lost");
        Path source = root.resolve("data/neoecoae_infinite/domain_" + domainId + ".dat");
        check(Arrays.equals(fixture.getByteArray("domainSource"), Files.readAllBytes(source)), "Old domain bytes were modified");
        Path archive = root.resolve("data/neoecoae_20_4_2_archive/cells/" + fixture.getUUID("cell") + ".dat");
        check(Arrays.equals(fixture.getByteArray("cellSource"), Files.readAllBytes(archive)), "Old cell archive changed");
        ItemStack stale = ItemStack.of(fixture.getCompound("staleDisk"));
        var staleCell = ECOStorageCellItem.getCellInventory(stale, null);
        check(staleCell != null && staleCell.getMigrationAmount(key()) == 800, "Stale release item resurrected old inventory");
        if (fixture.hasUUID("blockedDomain")) {
            var blockedDrive = (ECODriveBlockEntity) world.getBlockEntity(BLOCKED_DRIVE);
            check(blockedDrive != null && blockedDrive.hasLegacyRestoreReceipts(), "Original restore escrow was lost");
            var blockedCell = (ECOStorageCell) blockedDrive.getCellInventory();
            check(blockedCell != null && !blockedCell.storageWritable() && blockedCell.getMigrationAmount(key()) == 90,
                "Unverifiable old restore lost its inventory or became writable");
            check(blockedDrive.HANDLER.extractItem(0, 1, false).isEmpty(), "Quarantined restore cell escaped its host");
            var blockedDomain = ECOInfiniteStorageDomains.acquireExisting(world, fixture.getUUID("blockedDomain"));
            check(!blockedDomain.isHealthy() && blockedDomain.extract(key(), 1, Actionable.MODULATE) == 0,
                "Old restore domain barrier was lost after restart");
            ECOInfiniteStorageDomains.release(server, fixture.getUUID("blockedDomain"));
        }
        ECOInfiniteStorageDomains.release(server, domainId);
        var report = new JsonObject(); report.addProperty("phase", phase);
        report.addProperty("sourceTag", "20.4.2"); report.addProperty("sourceCommit", "f1739d1f1d23b889649aa2804a3d7e493c919c4e");
        report.addProperty("fixtureKind", "source-backed old-format NBT in actual Forge world; not a player world");
        report.addProperty("ordinaryUuidNbtAndAmounts", "PASS"); report.addProperty("infiniteExactAmountsAndReceipts", "PASS");
        report.addProperty("patternNbtHighSlotsPagesAndProvider", "PASS"); report.addProperty("controllerDomainComponentsPriority", "PASS");
        report.addProperty("sourceBytesAndStaleItems", "PASS");
        report.addProperty("dismantledBusAndQuarantine", phase.equals("upgrade") ? "PASS" : "verified in upgrade phase");
        report.addProperty("interruptedRestoreBarrier", fixture.hasUUID("blockedDomain") ? "PASS" : "not in this fixture revision");
        Files.writeString(root.resolve("legacy-2042-acceptance-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        drive.setChanged(); bus.setChanged(); controller.setChanged();
    }

    private static CompoundTag prepare(ServerLevel world, Path root) throws Exception {
        CompoundTag fixture = new CompoundTag(); UUID cell = UUID.randomUUID(), domain = UUID.randomUUID(), receipt = UUID.randomUUID();
        fixture.putUUID("cell", cell); fixture.putUUID("domain", domain); fixture.putUUID("receipt", receipt);
        ItemStack disk = NEItems.ECO_ITEM_CELL_16M.asStack();
        disk.getOrCreateTag().putUUID("eco_cell_id", cell); disk.getOrCreateTag().putInt("eco_cell_version", 2);
        fixture.put("staleDisk", disk.save(new CompoundTag()));
        CompoundTag ordinary = new CompoundTag(), entry = new CompoundTag(); ListTag entries = new ListTag();
        ordinary.putInt("format", 1); ordinary.putUUID("cell", cell); ordinary.putLong("revision", 11);
        entry.put("key", key().toTagGeneric()); entry.putLong("amount", 900); entries.add(entry); ordinary.put("entries", entries);
        Path ordinaryFile = root.resolve("data/neoecoae_cells/" + cell + ".dat"); writeOld(ordinaryFile, ordinary);
        fixture.putByteArray("cellSource", Files.readAllBytes(ordinaryFile));
        CompoundTag infinite = new CompoundTag(), hugeEntry = new CompoundTag(), proof = new CompoundTag();
        infinite.putInt("format", 2); infinite.putUUID("domain", domain); infinite.putLong("revision", 17);
        hugeEntry.put("key", key().toTagGeneric()); hugeEntry.putByteArray("amount_wide", huge().toByteArray());
        ListTag hugeEntries = new ListTag(); hugeEntries.add(hugeEntry); infinite.put("entries", hugeEntries);
        proof.putUUID("id", receipt); proof.putString("contents_sha256", "1".repeat(64)); ListTag proofs = new ListTag(); proofs.add(proof);
        infinite.put("transfer_receipts", proofs); Path infiniteFile = root.resolve("data/neoecoae_infinite/domain_" + domain + ".dat");
        writeOld(infiniteFile, infinite); fixture.putByteArray("domainSource", Files.readAllBytes(infiniteFile));
        world.setBlockAndUpdate(DRIVE, NEBlocks.ECO_DRIVE.getDefaultState());
        var drive = (ECODriveBlockEntity) world.getBlockEntity(DRIVE); CompoundTag legacyDrive = new CompoundTag();
        legacyDrive.put("cellStack", disk.save(new CompoundTag())); drive.load(legacyDrive);
        var inventory = (ECOStorageCell) drive.getCellInventory();
        check(inventory != null && inventory.extract(key(), 100, Actionable.MODULATE, IActionSource.empty()) == 100, "Real drive import/extraction failed");
        inventory.persistMigrationContents(world);
        ItemStack moved = drive.HANDLER.extractItem(0, 1, false);
        check(!moved.isEmpty() && drive.getCellStack() == null, "Disk extraction did not transfer ownership");
        check(drive.HANDLER.insertItem(0, moved, false).isEmpty(), "Disk reinsertion failed");
        var engine = ECOInfiniteStorageDomains.acquireExisting(world, domain);
        check(engine.extract(key(), BigInteger.valueOf(17), Actionable.MODULATE).equals(BigInteger.valueOf(17)) && engine.flush(), "Infinite migration/extraction failed");
        ECOInfiniteStorageDomains.release(world.getServer(), domain);
        ItemStack first = pattern(world, "oak_planks", new ItemStack[]{new ItemStack(Items.OAK_LOG)}, new ItemStack(Items.OAK_PLANKS, 4));
        ItemStack high = pattern(world, "stick", new ItemStack[]{new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, ItemStack.EMPTY,
            new ItemStack(Items.OAK_PLANKS)}, new ItemStack(Items.STICK, 4));
        first.getOrCreateTag().putString("legacy_marker", "first"); high.getOrCreateTag().putString("legacy_marker", "high");
        fixture.put("firstPattern", first.save(new CompoundTag())); fixture.put("highPattern", high.save(new CompoundTag()));
        CompoundTag legacyPatterns = new CompoundTag(); ListTag patterns = new ListTag();
        CompoundTag firstTag = first.save(new CompoundTag()), highTag = high.save(new CompoundTag());
        firstTag.putInt("Slot", 0); highTag.putInt("Slot", HIGH_SLOT); patterns.add(firstTag); patterns.add(highTag);
        legacyPatterns.put("patternInventory", patterns); legacyPatterns.putInt("patternInventoryPages", 8);
        world.setBlockAndUpdate(BUS, NEBlocks.CRAFTING_PATTERN_BUS.getDefaultState());
        ((ECOCraftingPatternBusBlockEntity) world.getBlockEntity(BUS)).load(legacyPatterns.copy());
        verifyDismantledBus(world, legacyPatterns, high);
        world.setBlockAndUpdate(CONTROLLER, NEBlocks.STORAGE_SYSTEM_L9.getDefaultState());
        var controller = (ECOStorageSystemBlockEntity) world.getBlockEntity(CONTROLLER);
        CompoundTag legacyController = new CompoundTag(); legacyController.putString("infiniteHostMode", "formed_infinite");
        legacyController.putUUID("infiniteDomainId", domain); legacyController.putInt("priority", 17);
        ItemStackHandler components = new ItemStackHandler(1); components.setStackInSlot(0, NEItems.ECO_INFINITE_CELL_COMPONENT.asStack(64));
        legacyController.put("infiniteComponentSlot", components.serializeNBT()); controller.load(legacyController);
        prepareBlockedRestore(world, root, fixture, ordinary, infinite);
        return fixture;
    }

    private static void prepareBlockedRestore(ServerLevel world, Path root, CompoundTag fixture,
            CompoundTag ordinaryTemplate, CompoundTag domainTemplate) throws Exception {
        UUID cellId = UUID.randomUUID(), domainId = UUID.randomUUID();
        CompoundTag ordinary = ordinaryTemplate.copy(); ordinary.putUUID("cell", cellId);
        ordinary.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putLong("amount", 90);
        writeOld(root.resolve("data/neoecoae_cells/" + cellId + ".dat"), ordinary);
        var disk = NEItems.ECO_ITEM_CELL_16M.asStack(); disk.getOrCreateTag().putUUID("eco_cell_id", cellId);
        CompoundTag legacy = new CompoundTag(); legacy.put("cellStack", disk.save(new CompoundTag()));
        CompoundTag receipt = new CompoundTag(); receipt.putUUID("id", UUID.randomUUID()); receipt.putInt("version", 1);
        ListTag receipts = new ListTag(); receipts.add(receipt); legacy.put("ecoRestoreReceipts", receipts);
        world.setBlockAndUpdate(BLOCKED_DRIVE, NEBlocks.ECO_DRIVE.getDefaultState());
        ((ECODriveBlockEntity) world.getBlockEntity(BLOCKED_DRIVE)).load(legacy);
        CompoundTag domain = domainTemplate.copy(); domain.putUUID("domain", domainId);
        writeOld(root.resolve("data/neoecoae_infinite/domain_" + domainId + ".dat"), domain);
        var engine = (cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine)
            ECOInfiniteStorageDomains.acquireExisting(world, domainId);
        engine.blockLegacyRestore(); ECOInfiniteStorageDomains.release(world.getServer(), domainId);
        fixture.putUUID("blockedDomain", domainId);
    }

    private static ItemStack pattern(ServerLevel world, String id, ItemStack[] partial, ItemStack output) {
        var recipe = world.getRecipeManager().byKey(new ResourceLocation("minecraft", id)).orElseThrow();
        ItemStack[] inputs = new ItemStack[9]; Arrays.fill(inputs, ItemStack.EMPTY); System.arraycopy(partial, 0, inputs, 0, partial.length);
        ItemStack pattern = PatternDetailsHelper.encodeCraftingPattern((CraftingRecipe) recipe, inputs, output, false, false);
        check(PatternDetailsHelper.decodePattern(pattern, world) != null, "Fixture recipe did not encode"); return pattern;
    }

    private static void verifyDismantledBus(ServerLevel world, CompoundTag source, ItemStack high) {
        BlockPos position = BUS.offset(0, 0, 3); world.setBlockAndUpdate(position, NEBlocks.CRAFTING_PATTERN_BUS.getDefaultState());
        var bus = (ECOCraftingPatternBusBlockEntity) world.getBlockEntity(position);
        bus.importSettings(SettingsFrom.DISMANTLE_ITEM, source.copy(), null);
        check(bus.getPageCount() == 8 && ItemStack.matches(high, bus.getPatternSlotInventory().getStackInSlot(HIGH_SLOT)), "Dismantled release bus lost patterns");
        var drops = new ArrayList<ItemStack>(); bus.addAdditionalDrops(world, position, drops); bus.clearContent();
        check(drops.size() == 2 && bus.getPatternSlotInventory().isEmpty(), "Dismantle duplicated or lost patterns");
        CompoundTag broken = source.copy(); broken.getList("patternInventory", Tag.TAG_COMPOUND).getCompound(0).putString("id", "absent:encoded_pattern");
        bus.load(broken); check(bus.getPatternSlotCount() == 0, "Undecodable inventory did not lock");
        CompoundTag carry = new CompoundTag(); bus.exportSettings(SettingsFrom.DISMANTLE_ITEM, carry, null);
        check(carry.getCompound("legacyPatternData").getList("patternInventory", Tag.TAG_COMPOUND).size() == 2, "Raw pattern escrow was lost on dismantle");
        drops.clear(); bus.addAdditionalDrops(world, position, drops); check(drops.isEmpty(), "Quarantined bus duplicated physical patterns");
        bus.clearContent(); bus.importSettings(SettingsFrom.DISMANTLE_ITEM, carry, null);
        check(bus.getPatternSlotCount() == 0 && !bus.saveWithFullMetadata().getCompound("legacyPatternData").isEmpty(), "Quarantine did not survive item placement");
        world.removeBlock(position, false);
    }

    private static void writeOld(Path file, CompoundTag data) throws Exception {
        Files.createDirectories(file.getParent()); CompoundTag root = new CompoundTag(); root.put("data", data); NbtIo.writeCompressed(root, file.toFile());
    }
}
