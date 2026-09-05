package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Looks up the storage engine of an infinite domain. Domains are world-global, so their data lives in the overworld's
 * data storage no matter which dimension the storage host stands in; the map here is only a cache of engines over that
 * data, discarded when the server stops.
 */
public final class ECOInfiniteStorageDomains {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOInfiniteStorageDomains.class);
    private static final String DATA_NAME_PREFIX = "neoecoae_infinite_";

    private static final Map<MinecraftServer, Map<UUID, ECOInfiniteStorageEngine>> ENGINES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, cn.dancingsnow.neoecoae.impl.storage.StorageFaults> FAULTS = new IdentityHashMap<>();

    private ECOInfiniteStorageDomains() {}

    public static synchronized ECOInfiniteStorageEngine get(ServerLevel level, UUID domainId) {
        MinecraftServer server = level.getServer();
        return ENGINES.computeIfAbsent(server, ignored -> new HashMap<>())
                .computeIfAbsent(domainId, ignored -> create(server, domainId));
    }

    /**
     * Drops the cached engine of a domain that is no longer mounted. The world data itself stays where it is: it is
     * owned by the level. Its journals and ownership receipts remain available for recovery.
     */
    public static synchronized void release(MinecraftServer server, UUID domainId) {
        Map<UUID, ECOInfiniteStorageEngine> engines = ENGINES.get(server);
        if (engines != null) {
            ECOInfiniteStorageEngine engine = engines.remove(domainId);
            if (engine instanceof SavedDataInfiniteStorageEngine saved) saved.close();
        }
    }

    public static synchronized void onServerStopped(MinecraftServer server) {
        Map<UUID, ECOInfiniteStorageEngine> engines = ENGINES.remove(server);
        if (engines != null) {
            for (var entry : engines.entrySet()) {
                try { if (entry.getValue() instanceof SavedDataInfiniteStorageEngine saved) saved.close(); }
                catch (RuntimeException e) { LOGGER.error("ECO domain {} shutdown flush failed", entry.getKey(), e); }
            }
        }
        FAULTS.remove(server);
    }

    public static synchronized void tick(MinecraftServer server, long tick) {
        Map<UUID, ECOInfiniteStorageEngine> engines = ENGINES.get(server);
        if (engines != null) engines.forEach((id, engine) -> {
            var faults = FAULTS.computeIfAbsent(server, ignored -> new cn.dancingsnow.neoecoae.impl.storage.StorageFaults());
            try { engine.tick(tick); faults.recovered(id.toString()); }
            catch (RuntimeException e) { faults.report(id.toString(), "Domain tick failed: " + e, tick, e); }
        });
    }

    public static synchronized com.google.gson.JsonObject diagnosticReport(MinecraftServer server) {
        com.google.gson.JsonObject report = new com.google.gson.JsonObject();
        report.addProperty("generatedAt", java.time.Instant.now().toString());
        com.google.gson.JsonArray domains = new com.google.gson.JsonArray();
        Map<UUID, ECOInfiniteStorageEngine> engines = ENGINES.get(server);
        if (engines != null) engines.forEach((id, engine) -> {
            com.google.gson.JsonObject domain = new com.google.gson.JsonObject();
            domain.addProperty("domain", id.toString());
            domain.addProperty("status", engine.status().name());
            domain.addProperty("canRestore", engine.canExitOrRestore());
            if (engine instanceof SavedDataInfiniteStorageEngine saved) {
                domain.addProperty("persistence", saved.persistenceSummary());
                com.google.gson.JsonArray failures = new com.google.gson.JsonArray();
                saved.failures().forEach(failures::add);
                domain.add("failures", failures);
            }
            domains.add(domain);
        });
        report.add("domains", domains);
        var faults = FAULTS.get(server);
        if (faults != null) report.add("lifecycleFailures", new com.google.gson.Gson().toJsonTree(faults.snapshot()));
        return report;
    }

    private static ECOInfiniteStorageEngine create(MinecraftServer server, UUID domainId) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Cannot open ECO infinite storage domain " + domainId + " without a level");
        }
        HolderLookup.Provider registries = overworld.registryAccess();
        String dataName = DATA_NAME_PREFIX + domainId;
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path dataFile = worldRoot.resolve("data").resolve(dataName + ".dat");
        boolean fileExisted = Files.isRegularFile(dataFile);

        ECOInfiniteStorageData data = overworld.getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(ECOInfiniteStorageData::createNew, ECOInfiniteStorageData::load, null),
                        dataName);
        data.bindDomainId(domainId);

        // Only meaningful right after the instance was built; a cached instance may well have written the file since.
        if (data.claimFirstLookup()) {
            if (isSilentlySubstitutedEmpty(fileExisted, data.wasLoadedFromDisk())) {
                // DimensionDataStorage logs read failures and hands back an empty instance, which is indistinguishable
                // from an empty domain. Left alone that would let the storage host convert every member cell back and
                // throw the contents away, so lock the domain instead.
                LOGGER.error(
                        "ECO infinite storage domain {} could not be read from {}; it stays locked until the file is"
                                + " repaired or removed",
                        domainId,
                        dataFile);
                data.markUnreadable();
            }
        }
        try {
            data.openJournal(dataFile, registries);
        } catch (java.io.IOException | RuntimeException e) {
            if (Files.exists(dataFile.resolveSibling(dataFile.getFileName() + ".store"))) data.markUnreadable();
            data.markWriteFailed("Cannot open storage journal: " + e);
            LOGGER.error("Cannot open ECO domain {} journal", domainId, e);
        }
        if (data.status() != ECOInfiniteStorageData.DomainStatus.HEALTHY) {
            LOGGER.warn("ECO domain {} opened with status {}: {}", domainId, data.status(), data.failures());
        }
        return new SavedDataInfiniteStorageEngine(data, registries, dataFile);
    }

    /**
     * {@code true} when a domain file exists on disk but the {@code SavedData} instance we got back was not built
     * from it. {@code DimensionDataStorage} silently swallows load failures and hands back a fresh empty instance in
     * that case, which is otherwise indistinguishable from a domain that is genuinely empty. Extracted as a pure
     * function so this decision is unit-testable without a real {@code ServerLevel}.
     */
    static boolean isSilentlySubstitutedEmpty(boolean fileExisted, boolean wasLoadedFromDisk) {
        return fileExisted && !wasLoadedFromDisk;
    }
}
