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

    private ECOInfiniteStorageDomains() {}

    public static synchronized ECOInfiniteStorageEngine get(ServerLevel level, UUID domainId) {
        MinecraftServer server = level.getServer();
        return ENGINES.computeIfAbsent(server, ignored -> new HashMap<>())
                .computeIfAbsent(domainId, ignored -> create(server, domainId));
    }

    /**
     * Drops the cached engine of a domain that is no longer mounted. The world data itself stays where it is: it is
     * owned by the level, and a retired domain is a few hundred bytes.
     */
    public static synchronized void release(MinecraftServer server, UUID domainId) {
        Map<UUID, ECOInfiniteStorageEngine> engines = ENGINES.get(server);
        if (engines != null) {
            engines.remove(domainId);
        }
    }

    public static synchronized void onServerStopped(MinecraftServer server) {
        ENGINES.remove(server);
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

        // Only meaningful right after the instance was built; a cached instance may well have written the file since.
        if (data.claimFirstLookup() && fileExisted && !data.wasLoadedFromDisk()) {
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
        return new SavedDataInfiniteStorageEngine(data, registries, dataFile);
    }
}
