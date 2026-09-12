package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
    private static final long IDLE_EVICTION_TICKS = 20L * 60L;

    private static final Map<MinecraftServer, Map<UUID, DomainEntry>> ENGINES = new IdentityHashMap<>();

    private ECOInfiniteStorageDomains() {}

    /** Mounts one host on a domain. Every successful acquire must be paired with {@link #release}. */
    public static synchronized ECOInfiniteStorageEngine acquire(ServerLevel level, UUID domainId) {
        MinecraftServer server = level.getServer();
        DomainEntry entry = ENGINES.computeIfAbsent(server, ignored -> new HashMap<>())
                .computeIfAbsent(domainId, ignored -> new DomainEntry(create(server, domainId)));
        entry.mountCount++;
        entry.idleSinceTick = Long.MIN_VALUE;
        return entry.engine;
    }

    /**
     * Releases one host mount. The last release starts an idle grace period, after which the cached engine is flushed
     * and discarded. The level retains the SavedData inventory and transfer receipts.
     */
    public static synchronized void release(MinecraftServer server, UUID domainId) {
        Map<UUID, DomainEntry> engines = ENGINES.get(server);
        if (engines != null) {
            DomainEntry entry = engines.get(domainId);
            if (entry == null) return;
            if (entry.mountCount > 0) entry.mountCount--;
            if (entry.mountCount == 0 && entry.idleSinceTick == Long.MIN_VALUE) {
                entry.idleSinceTick = server.getTickCount();
            }
        }
    }

    public static synchronized void onServerStopped(MinecraftServer server) {
        Map<UUID, DomainEntry> engines = ENGINES.remove(server);
        if (engines != null) {
            for (var entry : engines.entrySet()) {
                try { close(entry.getValue().engine); }
                catch (RuntimeException e) { LOGGER.error("ECO domain {} shutdown flush failed", entry.getKey(), e); }
            }
        }
    }

    public static synchronized void tick(MinecraftServer server, long tick) {
        Map<UUID, DomainEntry> engines = ENGINES.get(server);
        if (engines != null) {
            var iterator = engines.entrySet().iterator();
            while (iterator.hasNext()) {
                var domain = iterator.next();
                DomainEntry entry = domain.getValue();
                if (entry.mountCount == 0 && entry.idleSinceTick != Long.MIN_VALUE
                        && tick - entry.idleSinceTick >= IDLE_EVICTION_TICKS) {
                    try { close(entry.engine); }
                    catch (RuntimeException e) {
                        LOGGER.error("ECO domain {} idle eviction flush failed", domain.getKey(), e);
                        continue;
                    }
                    iterator.remove();
                    continue;
                }
            }
            if (engines.isEmpty()) ENGINES.remove(server);
        }
    }

    public static synchronized com.google.gson.JsonObject diagnosticReport(MinecraftServer server) {
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

    private static void close(ECOInfiniteStorageEngine engine) {
        if (engine instanceof SavedDataInfiniteStorageEngine saved) saved.close();
    }

    private static final class DomainEntry {
        private final ECOInfiniteStorageEngine engine;
        private int mountCount;
        private long idleSinceTick = Long.MIN_VALUE;

        private DomainEntry(ECOInfiniteStorageEngine engine) {
            this.engine = engine;
        }
    }

    private static ECOInfiniteStorageEngine create(MinecraftServer server, UUID domainId) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Cannot open ECO infinite storage domain " + domainId + " without a level");
        }
        HolderLookup.Provider registries = overworld.registryAccess();
        String legacyName = DATA_NAME_PREFIX + domainId;
        // A separate name keeps every old .dat/.store byte intact. Once v3 exists it is authoritative;
        // never fall back to the stale legacy inventory when the newer file is damaged.
        String dataName = legacyName + "_v3";
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path dataFile = worldRoot.resolve("data").resolve(dataName + ".dat");
        var storage = overworld.getDataStorage();
        ECOInfiniteStorageData data;
        try {
            if (Files.isRegularFile(dataFile)) {
                CompoundTag root = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());
                if (!root.contains("data", Tag.TAG_COMPOUND)) {
                    throw new java.io.IOException("Missing data compound in authoritative snapshot");
                }
                data = ECOInfiniteStorageData.load(root.getCompound("data"), registries);
            } else {
                data = ECOInfiniteStorageData.createNew();
                Path legacyFile = worldRoot.resolve("data").resolve(legacyName + ".dat");
                if (LegacyInfiniteStorageReader.exists(legacyFile)) {
                    var recovered = LegacyInfiniteStorageReader.readAvailable(legacyFile);
                    data = ECOInfiniteStorageData.load(recovered.data(), registries);
                    if (!recovered.failures().isEmpty()) data.markIncompleteLegacy(recovered.failures());
                }
                if (data.canWrite()) {
                    data.setDirty();
                    data.save(dataFile.toFile(), registries);
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            data = ECOInfiniteStorageData.createNew();
            data.markUnreadable("Cannot read authoritative domain snapshot; original files retained: " + e);
            LOGGER.error("Cannot import ECO domain {}; original files retained", domainId, e);
        }
        storage.set(dataName, data);
        if (data.status() != ECOInfiniteStorageData.DomainStatus.HEALTHY) {
            LOGGER.warn("ECO domain {} opened with status {}: {}", domainId, data.status(), data.failures());
        }
        return new SavedDataInfiniteStorageEngine(data, registries, dataFile);
    }

}
