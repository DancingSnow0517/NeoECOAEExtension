package cn.dancingsnow.neoecoae.grid.storage.infinite;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.grid.storage.infinite.persist.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.grid.storage.infinite.persist.LegacyInfiniteStorageReader;
import cn.dancingsnow.neoecoae.grid.storage.infinite.persist.SavedDataInfiniteStorageEngine;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.IOUtilities;
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
 * <p>Server-thread owned: hosts mount and unmount from their server-side tick, and the stop event and commands
 * run on the server thread. No lock is needed.
 */
public final class ECOInfiniteStorageDomains {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOInfiniteStorageDomains.class);
    private static final String DATA_NAME_PREFIX = "neoecoae_infinite_";

    private static final Map<MinecraftServer, Map<UUID, DomainEntry>> ENGINES = new IdentityHashMap<>();

    private static final String HOSTS_NAME = "neoecoae_storage_hosts";

    public static HostClaims hostClaims(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(HOSTS_NAME + ".dat");
        var claims = server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                () -> new HostClaims(!Files.exists(file)), HostClaims::load), HOSTS_NAME);
        claims.file = file;
        claims.registries = server.overworld().registryAccess();
        return claims;
    }

    public static final class HostClaims extends SavedData {
        private final Map<UUID, GlobalPos> locations = new HashMap<>();
        private final Map<GlobalPos, UUID> domains = new HashMap<>();
        private final boolean readable;
        private Path file;
        private HolderLookup.Provider registries;
        private CompletableFuture<Void> checkpoint = CompletableFuture.completedFuture(null);
        private UUID pendingDomain;
        private UUID displacedDomain;

        private HostClaims(boolean readable) { this.readable = readable; }

        private static HostClaims load(CompoundTag tag, HolderLookup.Provider registries) {
            HostClaims claims = new HostClaims(true);
            try {
                if (tag.getInt("version") != 1 || !(tag.get("hosts") instanceof CompoundTag hosts)) {
                    return new HostClaims(false);
                }
                for (String key : hosts.getAllKeys()) {
                    UUID id = UUID.fromString(key);
                    GlobalPos location = GlobalPos.CODEC.parse(NbtOps.INSTANCE, hosts.get(key)).getOrThrow();
                    if (claims.domains.putIfAbsent(location, id) != null) return new HostClaims(false);
                    claims.locations.put(id, location);
                }
                return claims;
            } catch (RuntimeException malformed) {
                LOGGER.error("Cannot load infinite storage host associations", malformed);
                return new HostClaims(false);
            }
        }

        public UUID domainAt(GlobalPos location) { return domains.get(location); }

        public GlobalPos locationOf(UUID id) { return locations.get(id); }

        public boolean readable() { return readable; }

        public boolean ready() { return readable && checkpoint.isDone() && !checkpoint.isCompletedExceptionally(); }

        public boolean readyFor(UUID id) {
            return readable && (ready() || !id.equals(pendingDomain) && !id.equals(displacedDomain));
        }

        public boolean bind(UUID id, GlobalPos location, boolean rebind) {
            if (!ready()) return false;
            GlobalPos previous = locations.get(id);
            UUID displaced = domains.get(location);
            if ((!rebind && previous != null && !previous.equals(location))
                    || (!rebind && displaced != null && !displaced.equals(id))) return false;
            if (location.equals(previous) && ready()) return true;
            if (previous != null) domains.remove(previous);
            if (displaced != null) locations.remove(displaced);
            locations.put(id, location);
            domains.put(location, id);
            pendingDomain = id;
            displacedDomain = displaced;
            write();
            return true;
        }

        public boolean unbind(UUID id, GlobalPos expectedLocation) {
            if (!readable || id == null || expectedLocation == null) return false;
            if (!expectedLocation.equals(locations.get(id))) return false;
            locations.remove(id);
            domains.remove(expectedLocation);
            if (id.equals(pendingDomain)) pendingDomain = null;
            if (id.equals(displacedDomain)) displacedDomain = null;
            setDirty();
            write();
            return true;
        }

        public void retry() {
            if (readable && checkpoint.isCompletedExceptionally()) write();
        }

        private void write() {
            if (!readable || file == null || registries == null) return;
            CompoundTag root = new CompoundTag();
            root.put("data", save(new CompoundTag(), registries));
            NbtUtils.addCurrentDataVersion(root);
            var previousWrite = checkpoint;
            var result = new CompletableFuture<Void>();
            checkpoint = result;
            previousWrite.whenComplete((ignored, prevFailure) -> IOUtilities.withIOWorker(() -> {
                try {
                    IOUtilities.writeNbtCompressed(root, file);
                    result.complete(null);
                } catch (java.io.IOException | RuntimeException failure) {
                    LOGGER.error("Cannot checkpoint infinite storage host associations", failure);
                    result.completeExceptionally(failure);
                }
            }));
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag hosts = new CompoundTag();
            locations.forEach((id, location) -> hosts.put(id.toString(),
                    GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, location).getOrThrow()));
            tag.put("hosts", hosts);
            tag.putInt("version", 1);
            return tag;
        }
    }

    private ECOInfiniteStorageDomains() {
    }

    /**
     * Mounts one host on a domain. Every successful acquire must be paired with {@link #release}.
     */
    public static ECOInfiniteStorageEngine acquire(ServerLevel level, UUID domainId) {
        MinecraftServer server = level.getServer();
        DomainEntry entry = ENGINES.computeIfAbsent(server, ignored -> new Object2ObjectOpenHashMap<>())
                .computeIfAbsent(domainId, ignored -> new DomainEntry(create(server, domainId)));
        entry.mountCount++;
        return entry.engine;
    }

    /**
     * Releases one host mount. The engine stays cached for the rest of the server session: it is only a view over the
     * level's SavedData, which the vanilla save cycle persists whether or not any host is mounted.
     */
    public static void release(MinecraftServer server, UUID domainId) {
        Map<UUID, DomainEntry> engines = ENGINES.get(server);
        if (engines != null) {
            DomainEntry entry = engines.get(domainId);
            if (entry != null && entry.mountCount > 0) entry.mountCount--;
        }
    }

    /** Checks existing data without creating an empty domain for an unknown UUID. */
    public static boolean exists(MinecraftServer server, UUID domainId) {
        Map<UUID, DomainEntry> engines = ENGINES.get(server);
        if (engines != null && engines.containsKey(domainId)) return true;
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("data");
        String name = DATA_NAME_PREFIX + domainId;
        return java.nio.file.Files.exists(directory.resolve(name + "_v3.dat"))
                || LegacyInfiniteStorageReader.exists(directory.resolve(name + ".dat"));
    }

    public static int mountedHosts(MinecraftServer server, UUID domainId) {
        Map<UUID, DomainEntry> engines = ENGINES.get(server);
        DomainEntry entry = engines == null ? null : engines.get(domainId);
        return entry == null ? 0 : entry.mountCount;
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

    private static ECOInfiniteStorageEngine create(MinecraftServer server, UUID domainId) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Cannot open ECO infinite storage domain " + domainId + " without a level");
        }
        String legacyName = DATA_NAME_PREFIX + domainId;
        // A separate name keeps every old .dat/.store byte intact. Once v3 exists it is authoritative;
        // never fall back to the stale legacy inventory when the newer file is damaged.
        String dataName = legacyName + "_v3";
        Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Path dataFile = dataDir.resolve(dataName + ".dat");
        Path legacyFile = dataDir.resolve(legacyName + ".dat");
        ECOInfiniteStorageData data = overworld.getDataStorage().computeIfAbsent(
                ECOInfiniteStorageData.factory(dataFile, () -> importLegacy(overworld, domainId, legacyFile)), dataName);
        data.bindPersistence(dataFile, overworld.registryAccess());
        if (data.status() != ECOInfiniteStorageData.DomainStatus.HEALTHY) {
            if (NEConfig.ecoCraftingOutputDeliveryDebug) LOGGER.warn("ECO domain {} opened with status {}: {}", domainId, data.status(), data.failures());
        }
        return new SavedDataInfiniteStorageEngine(data);
    }

    private static ECOInfiniteStorageData importLegacy(ServerLevel overworld, UUID domainId, Path legacyFile) {
        ECOInfiniteStorageData data = ECOInfiniteStorageData.createNew();
        try {
            if (!LegacyInfiniteStorageReader.exists(legacyFile)) return data;
            var recovered = LegacyInfiniteStorageReader.readAvailable(legacyFile);
            data = ECOInfiniteStorageData.load(recovered.data(), overworld.registryAccess());
            if (!recovered.failures().isEmpty()) data.markIncompleteLegacy(recovered.failures());
            // Written as v3 on the next world save; the legacy files are never touched.
            if (data.canWrite()) data.setDirty();
        } catch (java.io.IOException | RuntimeException e) {
            data = ECOInfiniteStorageData.createNew();
            data.markUnreadable("Cannot import legacy domain snapshot; original files retained: " + e);
            if (NEConfig.ecoCraftingOutputDeliveryDebug) LOGGER.error("Cannot import ECO domain {}; original files retained", domainId, e);
        }
        return data;
    }

}
