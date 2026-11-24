package cn.dancingsnow.neoecoae.storage.infinite;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

public final class ECOInfiniteStorageMember {
    private static final List<String> KEYS = List.of("neoecoae_infinite_member", "neoecoae_infinite_domain",
            "neoecoae_member_identity", "neoecoae_migration_id", "neoecoae_migration_domain", "neoecoae_restore_receipts");
    private static final String DOMAIN = KEYS.get(1);
    private static final String IDENTITY = KEYS.get(2);
    private static final String MIGRATION = KEYS.get(3);
    private static final String MIGRATION_DOMAIN = KEYS.get(4);
    private static final String RECEIPTS = KEYS.get(5);

    private ECOInfiniteStorageMember() { }

    private record Evidence(CompoundTag tag) {
        UUID identity() { return uuid(IDENTITY); }
        UUID migration() { return uuid(MIGRATION); }
        UUID domain() { return tag.contains(MIGRATION_DOMAIN) ? uuid(MIGRATION_DOMAIN) : uuid(DOMAIN); }
        UUID uuid(String key) { return tag.hasUUID(key) ? tag.getUUID(key) : null; }
        boolean malformed() {
            return (tag.contains(MIGRATION) != tag.contains(MIGRATION_DOMAIN))
                    || List.of(DOMAIN, IDENTITY, MIGRATION, MIGRATION_DOMAIN).stream()
                        .anyMatch(key -> tag.contains(key) && uuid(key) == null)
                    || tag.contains(DOMAIN) && tag.contains(MIGRATION_DOMAIN) && !uuid(DOMAIN).equals(uuid(MIGRATION_DOMAIN))
                    || tag.contains(RECEIPTS) && !tag.contains(RECEIPTS, Tag.TAG_COMPOUND);
        }
        long receipt(UUID transaction) {
            CompoundTag receipts = tag.getCompound(RECEIPTS);
            String key = transaction.toString();
            if (!receipts.contains(key)) return 0L;
            if (!receipts.contains(key, Tag.TAG_LONG) || receipts.getLong(key) <= 0L) {
                throw new IllegalStateException("Malformed restore receipt " + transaction);
            }
            return receipts.getLong(key);
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static Evidence evidence(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom != null && KEYS.stream().anyMatch(custom::contains) ? new Evidence(custom.getUnsafe()) : null;
    }

    private static void clearSettledKeys(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        KEYS.forEach(tag::remove);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean hasTransactionEvidence(ItemStack stack) {
        Evidence cell = evidence(stack);
        return cell != null && (cell.tag().contains(MIGRATION) || cell.tag().contains(RECEIPTS) || cell.domain() != null);
    }

    public static boolean prepareCell(ServerLevel level, ECODriveBlockEntity drive, ItemStack stack, @Nullable UUID hostDomain) {
        Evidence cell = evidence(stack);
        if (cell == null) return false;
        UUID domain = cell.domain();
        if (domain == null && !cell.malformed() && !cell.tag().contains(RECEIPTS)) {
            clearSettledKeys(stack);
            drive.setChanged();
            return false;
        }
        UUID inspected = domain != null ? domain : hostDomain;
        if (inspected == null || !ECOInfiniteStorageDomains.exists(level.getServer(), inspected)) {
            LoggerFactory.getLogger(ECOInfiniteStorageMember.class).warn(
                    "Unresolved legacy storage disk at {} (domain {}): association or SavedData unavailable", drive.getBlockPos(), inspected);
            return true;
        }
        var engine = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquire(level, inspected);
        try {
            var data = engine.legacyData();
            if (cell.malformed() || domain == null) {
                data.noteLegacyEvidence(Set.of(), "Malformed or unbound legacy transaction evidence at " + drive.getBlockPos());
                engine.checkpoint();
                return true;
            }
            if (cell.migration() != null && !data.legacySourceSettled(cell.migration())) {
                data.noteLegacyEvidence(Set.of(cell.migration()), "");
                engine.checkpoint();
                return true;
            }
            if (!data.canRead() || data.hasPendingRestore() || data.hasUnresolvedLegacyEvidence()) return true;
            clearSettledKeys(stack);
            drive.setChanged();
            return false;
        } finally {
            ECOInfiniteStorageDomains.release(level.getServer(), inspected);
        }
    }

    public static void recordRoster(SavedDataInfiniteStorageEngine engine, Set<UUID> sources, boolean malformed) {
        engine.legacyData().noteLegacyEvidence(sources, malformed ? "Invalid legacy controller transaction roster" : "");
        if (!sources.isEmpty() || malformed) engine.checkpoint();
    }

    @Nullable
    public static String reconcile(ServerLevel level, UUID domain, SavedDataInfiniteStorageEngine engine,
            List<ECODriveBlockEntity> drives) {
        var data = engine.legacyData();
        Set<UUID> sources = data.legacySources();
        Set<AEKey> restores = data.legacyRestoreKeys();
        if (!data.legacyFailure().isEmpty()) return data.legacyFailure();
        if (data.hasUnresolvedLegacyEvidence()) return "Saved domain contains unreadable legacy transaction evidence";
        if (sources.isEmpty() && restores.isEmpty()) return null;
        if (!data.mayReconcileLegacy()) return "Legacy evidence or domain ledger is not readable and durable";
        try {
            Map<UUID, Disk> sourceDisks = new HashMap<>();
            Map<UUID, Disk> targets = new HashMap<>();
            for (ECODriveBlockEntity drive : drives) {
                Evidence evidence = evidence(drive.getCellStack());
                if (evidence == null || !domain.equals(evidence.domain())) continue;
                if (evidence.malformed()) throw new IllegalStateException("Malformed disk evidence at " + drive.getBlockPos());
                Disk disk = new Disk(drive, evidence);
                if (evidence.migration() != null && sources.contains(evidence.migration())
                        && sourceDisks.putIfAbsent(evidence.migration(), disk) != null) {
                    throw new IllegalStateException("Duplicate legacy migration evidence " + evidence.migration());
                }
                if (evidence.identity() != null && targets.putIfAbsent(evidence.identity(), disk) != null && !restores.isEmpty()) {
                    throw new IllegalStateException("Duplicate legacy restore evidence " + evidence.identity());
                }
            }
            if (!sourceDisks.keySet().containsAll(sources)) return "Original migration source evidence is missing: " + sources;
            Map<AEKey, BigInteger> overlaps = new HashMap<>();
            Map<AEKey, String> ledgerKeys = new HashMap<>();
            if (!sources.isEmpty()) data.amounts.forEach((key, amount) -> ledgerKeys.put(key, keyTag(level, key)));
            for (var entry : sourceDisks.entrySet()) {
                Disk disk = entry.getValue();
                var contents = disk.inventory(level).migrationEntries();
                var observedKeys = new java.util.HashSet<AEKey>();
                while (contents.hasNext()) {
                    var content = contents.next();
                    AEKey key = content.getKey();
                    long amount = content.getLongValue();
                    String keyTag = keyTag(level, key);
                    if (amount <= 0L) throw new IllegalStateException("Invalid legacy source amount");
                    observedKeys.add(key);
                    UUID sealed = id(entry.getKey() + ":" + keyTag);
                    UUID oldSlot = id(domain + ":to-domain:" + disk.drive().getBlockPos().asLong() + ":" + keyTag + ":" + amount);
                    boolean amountMatches = engine.hasMigrationReceipt(oldSlot);
                    if (!amountMatches && engine.hasMigrationReceipt(sealed)) {
                        return "Legacy credit has no matching source position and quantity: " + entry.getKey();
                    }
                    if (amountMatches) {
                        addOverlap(overlaps, key, amount);
                    }
                }
                for (var credited : ledgerKeys.entrySet()) {
                    if (!observedKeys.contains(credited.getKey())
                            && engine.hasMigrationReceipt(id(entry.getKey() + ":" + credited.getValue()))) {
                        return "Credited resource is missing from the legacy source: " + entry.getKey();
                    }
                }
            }
            for (AEKey key : restores) {
                UUID transaction = data.restoreTransaction(key);
                Set<UUID> originalTargets = data.restoreTargetIds(key);
                var plan = data.restorePlan(key);
                if (originalTargets.isEmpty() || !targets.keySet().containsAll(originalTargets)) {
                    return "Original restore target evidence is missing for " + transaction;
                }
                for (UUID identity : originalTargets) {
                    Disk disk = targets.get(identity);
                    long observed = disk.inventory(level).getMigrationAmount(key);
                    long receipt = disk.evidence().receipt(id(transaction + ":" + identity));
                    var goal = plan.get(identity);
                    if (goal == null) {
                        if (plan.isEmpty() && (receipt <= 0L || observed < receipt)) {
                            return "Insufficient evidence for an older restore without before/after quantities: " + transaction;
                        }
                        if (!plan.isEmpty() && receipt != 0L) return "Receipt for an unallocated restore target: " + transaction;
                        addOverlap(overlaps, key, receipt);
                        continue;
                    }
                    long prior = goal.transferred() - (goal.after() - goal.before());
                    if (observed < goal.before() || observed > goal.after()
                            || receipt != prior && (receipt != goal.transferred() || observed != goal.after())) {
                        return "Restore target quantity or receipt conflicts with saved plan: " + transaction;
                    }
                    addOverlap(overlaps, key, prior + (observed - goal.before()));
                }
            }
            if (!engine.settleLegacy(sources, restores, overlaps)) return "Proven overlapping quantities exceed the saved balance";
            engine.checkpoint();
            return null;
        } catch (RuntimeException failure) {
            return failure.toString();
        }
    }

    private record Disk(ECODriveBlockEntity drive, Evidence evidence) {
        IECOStorageMigrationCell inventory(ServerLevel level) {
            if (!(drive.loadCellInventory() instanceof IECOStorageMigrationCell cell)) {
                throw new IllegalStateException("Unreadable legacy disk at " + drive.getBlockPos());
            }
            if (!cell.canVerifyLegacyContents(level)) {
                throw new IllegalStateException("Incomplete legacy disk contents at " + drive.getBlockPos());
            }
            return cell;
        }
    }

    private static void addOverlap(Map<AEKey, BigInteger> overlaps, AEKey key, long amount) {
        if (amount > 0L) overlaps.merge(key, BigInteger.valueOf(amount), BigInteger::add);
    }

    private static UUID id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }

    private static String keyTag(ServerLevel level, AEKey key) {
        return String.valueOf(key.toTagGeneric(level.registryAccess()));
    }

    static UUID settlementId(UUID migration) { return id("neoecoae:legacy-settled:" + migration); }
}
