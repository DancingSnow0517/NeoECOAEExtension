package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

public final class ECOInfiniteStorageDomains {
    private static final Map<String, FileBackedInfiniteStorageEngine> ENGINES = new HashMap<>();

    private ECOInfiniteStorageDomains() {}

    public static synchronized FileBackedInfiniteStorageEngine get(ServerLevel level, UUID domainId) {
        String dimension = sanitize(level.dimension().location().toString());
        String key = dimension + ":" + domainId;
        return ENGINES.computeIfAbsent(
                key, ignored -> new FileBackedInfiniteStorageEngine(domainId, domainPath(level, domainId)));
    }

    public static synchronized void close(ServerLevel level, UUID domainId) {
        String dimension = sanitize(level.dimension().location().toString());
        FileBackedInfiniteStorageEngine engine = ENGINES.remove(dimension + ":" + domainId);
        if (engine != null) {
            engine.closeAndFlush();
        }
    }

    private static Path domainPath(ServerLevel level, UUID domainId) {
        return level.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("neoecoae_storage")
                .resolve("dim_" + sanitize(level.dimension().location().toString()))
                .resolve("domain_" + domainId);
    }

    private static String sanitize(String value) {
        return value.replace(':', '_').replace('/', '_').replace('\\', '_');
    }
}
