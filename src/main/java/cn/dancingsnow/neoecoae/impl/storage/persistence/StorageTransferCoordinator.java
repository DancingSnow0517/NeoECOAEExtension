package cn.dancingsnow.neoecoae.impl.storage.persistence;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import java.nio.file.Files;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/** Durable source seal -> destination receipt -> source retirement. Recovery runs before mounting a source. */
public final class StorageTransferCoordinator {
    public enum Phase { PREPARED, SOURCE_SEALED, DESTINATION_SAVED, SOURCE_SAVED, COMPLETED }
    private StorageTransferCoordinator() {}

    public static StorageTransferData journal(ECOStorageRepository.Session session, UUID source, boolean create) {
        String key = "neoecoae_transfers/" + source;
        var path = session.directory().resolve(key + ".dat");
        var data = session.storage().get(tag -> StorageTransferData.load(tag, source), key);
        if (data == null && (create || Files.exists(path) || Files.exists(path.resolveSibling(path.getFileName() + ".bak")))) {
            data = new StorageTransferData(source);
            if (Files.exists(path) || Files.exists(path.resolveSibling(path.getFileName() + ".bak")))
                data.unavailable("Unreadable transfer record; source remains locked");
            session.storage().set(key, data);
        }
        if (data != null) { data.bind(path); session.track(data); }
        return data;
    }

    public static boolean transfer(ECOStorageRepository.Session session, ECOCellSavedData source,
                                   UUID domain, SavedDataInfiniteStorageEngine target, UUID transaction) {
        return transfer(session, source, domain, target, transaction, phase -> {});
    }

    public static boolean transfer(ECOStorageRepository.Session session, ECOCellSavedData source,
            UUID domain, SavedDataInfiniteStorageEngine target, UUID transaction,
            java.util.Map<appeng.api.stacks.AEKey, java.util.Set<UUID>> compatibility) {
        return transfer(session, source, domain, target, transaction, phase -> {}, compatibility);
    }

    static boolean transfer(ECOStorageRepository.Session session, ECOCellSavedData source,
                            UUID domain, SavedDataInfiniteStorageEngine target, UUID transaction, Consumer<Phase> observer) {
        return transfer(session, source, domain, target, transaction, observer, java.util.Map.of());
    }
    static boolean transfer(ECOStorageRepository.Session session, ECOCellSavedData source,
            UUID domain, SavedDataInfiniteStorageEngine target, UUID transaction, Consumer<Phase> observer,
            java.util.Map<appeng.api.stacks.AEKey, java.util.Set<UUID>> compatibility) {
        if (!source.canMutate() || !target.isHealthy()) return false;
        StorageTransferData journal = journal(session, source.id(), true);
        if (!journal.readable()) throw new IllegalStateException("Unreadable ECO transfer journal");
        if (transaction.equals(journal.transaction())) {
            if (!domain.equals(journal.domain())) throw new IllegalStateException("ECO transfer domain changed");
            if (journal.completed()) {
                boolean saved = journal.flush() && source.flush() && target.flush();
                if (saved) target.endTransfer(transaction);
                return saved;
            }
        } else {
            if (!source.flush()) return false;
            if (source.legacyMigration() && compatibility.isEmpty())
                throw new IllegalStateException("Legacy migration needs its original per-key receipts");
            journal.prepare(transaction, domain, source, compatibility);
        }
        // An earlier failed prepare must be flushed again before exposing any destination amounts.
        if (!journal.flush()) return false;
        observer.accept(Phase.PREPARED);
        ECOCellSavedData baseline = ECOCellSavedData.load(journal.sourceSnapshot(), source.id());
        if (!baseline.canMutate()) throw new IllegalStateException("Invalid sealed ECO transfer inventory");
        CompoundTag metadata = source.metadata();
        boolean retired = metadata.getBoolean("neoecoae_infinite_member")
            && metadata.hasUUID("neoecoae_infinite_domain") && domain.equals(metadata.getUUID("neoecoae_infinite_domain"))
            && source.amounts().isEmpty();
        if (!retired && !source.amounts().sameContents(baseline.amounts()))
            throw new IllegalStateException("Sealed ECO source differs from its transfer snapshot");
        if (retired && !target.hasMigrationReceipt(transaction))
            throw new IllegalStateException("Retired ECO source has no durable destination receipt");
        target.beginTransfer(transaction);
        if (!retired) {
            metadata.putUUID("neoecoae_migration_id", transaction);
            metadata.putUUID("neoecoae_migration_domain", domain);
            source.metadata(metadata);
            if (!source.flush()) return false;
            observer.accept(Phase.SOURCE_SEALED);
            var aliases = journal.compatibilityReceipts();
            if (baseline.legacyMigration() && aliases.size() != baseline.amounts().size())
                throw new IllegalStateException("Incomplete legacy migration receipts; source remains sealed");
            if (!target.importOnce(transaction, baseline.amounts(), aliases) || !target.flush()) return false;
            observer.accept(Phase.DESTINATION_SAVED);
            source.amounts().clear();
            source.legacyMigration(false);
            source.clearLegacy2042();
            metadata.putBoolean("neoecoae_infinite_member", true);
            metadata.putUUID("neoecoae_infinite_domain", domain);
            metadata.putUUID("neoecoae_member_identity", transaction);
            metadata.remove("neoecoae_migration_id"); metadata.remove("neoecoae_migration_domain");
            source.metadata(metadata);
            if (!source.flush()) return false;
        }
        observer.accept(Phase.SOURCE_SAVED);
        if (!target.flush() || !source.flush()) return false;
        journal.complete();
        if (!journal.flush()) return false;
        target.endTransfer(transaction);
        observer.accept(Phase.COMPLETED);
        return true;
    }

    public static void recover(ECOStorageRepository.Session session, ECOCellSavedData source) {
        StorageTransferData journal = journal(session, source.id(), false);
        if (journal == null || journal.completed()) return;
        if (!journal.readable()) {
            source.unavailable("Pending ECO transfer requires recovery before mounting"); return;
        }
        if (session.server() == null) return;
        var server = session.server();
        try {
            var target = ECOInfiniteStorageDomains.acquireExisting(server.overworld(), journal.domain());
            if (!(target instanceof SavedDataInfiniteStorageEngine saved)
                || !transfer(session, source, journal.domain(), saved, journal.transaction())) {
                source.unavailable("ECO transfer recovery did not complete; original records retained");
            }
        } catch (RuntimeException failure) {
            source.unavailable("ECO transfer recovery failed; original records retained: " + failure);
            org.slf4j.LoggerFactory.getLogger(StorageTransferCoordinator.class).error("Cannot recover ECO disk {}", source.id(), failure);
        } finally { ECOInfiniteStorageDomains.release(server, journal.domain()); }
    }
}
