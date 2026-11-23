package cn.dancingsnow.neoecoae.storage.infinite;

import cn.dancingsnow.neoecoae.storage.persistence.Legacy2042DomainSnapshot;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Looks up the storage engine of an infinite domain. Domains are world-global, so their data lives in the overworld's
 * data storage no matter which dimension the storage host stands in; the map here is only a cache of engines over that
 * data, discarded when the server stops. Loading and saving belong entirely to the vanilla {@code DimensionDataStorage}.
 *
 * <p>Server-thread owned: hosts mount and unmount from their server-side tick, and the stop event, commands and
 * storage recovery all run on the server thread. No lock is needed.
 */
public final class ECOInfiniteStorageDomains {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOInfiniteStorageDomains.class);
    private static final String DATA_NAME_PREFIX = "neoecoae_infinite_";
    private static final String LEGACY_2042_DIRECTORY = "neoecoae_infinite";
    private static final String LEGACY_2042_PREFIX = "domain_";

    private static final Map<MinecraftServer, Map<UUID, DomainEntry>> ENGINES = new IdentityHashMap<>();

    private ECOInfiniteStorageDomains() {
    }

    /**
     * Mounts one host on a domain. Every successful acquire must be paired with {@link #release}.
     */
    public static ECOInfiniteStorageEngine acquire(ServerLevel level, UUID domainId) {
        return acquire(level, domainId, false);
    }

    public static ECOInfiniteStorageEngine acquireExisting(ServerLevel level, UUID domainId) {
        return acquire(level, domainId, true);
    }

    private static ECOInfiniteStorageEngine acquire(ServerLevel level, UUID domainId, boolean requireExisting) {
        MinecraftServer server = level.getServer();
        DomainEntry entry = ENGINES.computeIfAbsent(server, ignored -> new Object2ObjectOpenHashMap<>())
                .computeIfAbsent(domainId, ignored -> new DomainEntry(create(server, domainId, requireExisting)));
        entry.mountCount++;
        return entry.engine;
    }

    /**
     * Releases one host mount. The engine stays cached for the rest of the server session: it is only a view over the
     * level's SavedData, which the vanilla save cycle persists whether or not any host is mounted.
     */
    public static void release(MinecraftServer server, UUID domainId) {
        DomainEntry entry = cachedDomain(server, domainId);
        if (entry != null && entry.mountCount > 0) entry.mountCount--;
    }

    public static int mountedHosts(MinecraftServer server, UUID domainId) {
        DomainEntry entry = cachedDomain(server, domainId);
        return entry == null ? 0 : entry.mountCount;
    }

    public static boolean exists(MinecraftServer server, UUID domainId) {
        return cachedDomain(server, domainId) != null || savedDomainExists(dataDirectory(server), domainId);
    }

    public static Set<UUID> discoverIds(MinecraftServer server) {
        Path directory = dataDirectory(server);
        Set<UUID> ids = new HashSet<>();
        collectSavedIds(directory, DATA_NAME_PREFIX, ids);
        collectSavedIds(directory.resolve(LEGACY_2042_DIRECTORY), LEGACY_2042_PREFIX, ids);
        ids.removeIf(id -> !savedDomainExists(directory, id));

        Map<UUID, DomainEntry> engines = ENGINES.get(server);
        if (engines != null) ids.addAll(engines.keySet());
        return ids;
    }

    private static void collectSavedIds(Path directory, String prefix, Set<UUID> ids) {
        int uuidEnd = prefix.length() + 36;
        try (var paths = Files.newDirectoryStream(directory, prefix + "*")) {
            for (Path path : paths) {
                String name = path.getFileName().toString();
                if (name.length() <= uuidEnd) continue;
                try {
                    ids.add(UUID.fromString(name.substring(prefix.length(), uuidEnd)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (NoSuchFileException ignored) {
        } catch (IOException | DirectoryIteratorException failure) {
            LOGGER.warn("Cannot list ECO infinite storage domains in {}", directory, failure);
        }
    }

    private static Path dataDirectory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data");
    }

    private static boolean savedDomainExists(Path directory, UUID domainId) {
        String name = DATA_NAME_PREFIX + domainId;
        return Files.exists(directory.resolve(name + "_v3.dat"))
                || Files.exists(directory.resolve(name + "_v3.dat.bak"))
                || LegacyInfiniteStorageReader.exists(directory.resolve(name + ".dat"))
                || Legacy2042DomainSnapshot.exists(legacy2042File(directory, domainId));
    }

    private static DomainEntry cachedDomain(MinecraftServer server, UUID domainId) {
        Map<UUID, DomainEntry> engines = ENGINES.get(server);
        return engines == null ? null : engines.get(domainId);
    }

    /** The world has already been saved by vanilla shutdown; only drop the cached views. */
    public static void onServerStopped(MinecraftServer server) {
        ENGINES.remove(server);
    }

    public static com.google.gson.JsonObject diagnosticReport(MinecraftServer server) {
        com.google.gson.JsonObject report = new com.google.gson.JsonObject();
        report.addProperty("generatedAt", java.time.Instant.now().toString());
        com.google.gson.JsonArray domains = new com.google.gson.JsonArray();
        Map<UUID, DomainEntry> engines = ENGINES.get(server);
        if (engines != null) engines.forEach((id, entry) -> {
            ECOInfiniteStorageEngine engine = entry.engine;
            com.google.gson.JsonObject domain = new com.google.gson.JsonObject();
            domain.addProperty("domain", id.toString());
            domain.addProperty("mountedHosts", entry.mountCount);
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
        return report;
    }

    private static final class DomainEntry {
        private final ECOInfiniteStorageEngine engine;
        private int mountCount;

        private DomainEntry(ECOInfiniteStorageEngine engine) {
            this.engine = engine;
        }
    }

    private static ECOInfiniteStorageEngine create(MinecraftServer server, UUID domainId, boolean requireExisting) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Cannot open ECO infinite storage domain " + domainId + " without a level");
        }
        String legacyName = DATA_NAME_PREFIX + domainId;
        // A separate name keeps every old .dat/.store byte intact. Once v3 exists it is authoritative;
        // never fall back to the stale legacy inventory when the newer file is damaged.
        String dataName = legacyName + "_v3";
        Path dataDir = dataDirectory(server);
        Path dataFile = dataDir.resolve(dataName + ".dat");
        Path legacyFile = dataDir.resolve(legacyName + ".dat");
        var catalog = cn.dancingsnow.neoecoae.storage.persistence.StorageCatalogSavedData
            .open(cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository.session(server));
        var factory = ECOInfiniteStorageData.factory(dataFile, () -> {
            if (!catalog.readable() || catalog.contains(domainId)) {
                var unavailable = ECOInfiniteStorageData.createNew();
                unavailable.markUnreadable("Published infinite domain snapshot is missing; identity retained: " + domainId);
                return unavailable;
            }
            return importLegacy(domainId, legacyFile, requireExisting);
        });
        ECOInfiniteStorageData data = overworld.getDataStorage().computeIfAbsent(
                factory.deserializer(), factory.constructor(), dataName);
        data.bind(dataFile);
        data.bindDomain(domainId);
        cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository.register(server, data);
        if (!catalog.contains(domainId) && data.canMigrate() && (!data.flush() || !catalog.publish(domainId))) {
            data.markUnreadable("Cannot publish the durable infinite domain identity");
        }
        if (data.status() != ECOInfiniteStorageData.DomainStatus.HEALTHY) {
            LOGGER.warn("ECO domain {} opened with status {}: {}", domainId, data.status(), data.failures());
        }
        return new SavedDataInfiniteStorageEngine(data);
    }

    static ECOInfiniteStorageData importLegacy(UUID domainId, Path legacyFile, boolean requireExisting) {
        ECOInfiniteStorageData data = ECOInfiniteStorageData.createNew();
        try {
            Path releaseFile = legacy2042File(legacyFile.getParent(), domainId);
            boolean releasePresent = Legacy2042DomainSnapshot.exists(releaseFile);
            if (releasePresent) {
                if (LegacyInfiniteStorageReader.exists(legacyFile))
                    throw new java.io.IOException("Conflicting 20.4.2 and main legacy domain sources");
                data = ECOInfiniteStorageData.load(cn.dancingsnow.neoecoae.storage.persistence.Legacy2042Storage
                    .domain(Legacy2042DomainSnapshot.read(releaseFile), domainId));
                if (data.canMigrate()) data.setDirty();
                else data.markIncompleteLegacy(java.util.List.of("Unresolved 20.4.2 inventory; original domain retained"));
                return data;
            }
            if (!LegacyInfiniteStorageReader.exists(legacyFile)) {
                if (requireExisting) data.markUnreadable("Existing infinite domain is missing; refusing to create an empty domain: " + domainId);
                return data;
            }
            var recovered = LegacyInfiniteStorageReader.readAvailable(legacyFile);
            data = ECOInfiniteStorageData.load(recovered.data());
            if (!recovered.failures().isEmpty()) data.markIncompleteLegacy(recovered.failures());
            // Written as v3 on the next world save; the legacy files are never touched.
            if (data.canWrite()) data.setDirty();
        } catch (java.io.IOException | RuntimeException e) {
            data = ECOInfiniteStorageData.createNew();
            data.markUnreadable("Cannot import legacy domain snapshot; original files retained: " + e);
            LOGGER.error("Cannot import ECO domain {}; original files retained", domainId, e);
        }
        return data;
    }

    private static Path legacy2042File(Path directory, UUID domainId) {
        return directory.resolve(LEGACY_2042_DIRECTORY).resolve(LEGACY_2042_PREFIX + domainId + ".dat");
    }
}
