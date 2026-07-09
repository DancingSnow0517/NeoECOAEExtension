package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

public final class ECOInfiniteStorageDomains {
    private static final Map<String, FileBackedInfiniteStorageEngine> ENGINES = new HashMap<>();

    private ECOInfiniteStorageDomains() {}

    public static synchronized FileBackedInfiniteStorageEngine get(ServerLevel level, UUID domainId) {
        String key = keyFor(level, domainId);
        return ENGINES.computeIfAbsent(
                key, ignored -> new FileBackedInfiniteStorageEngine(level.registryAccess(), domainId, domainPath(level, domainId)));
    }

    public static synchronized void close(ServerLevel level, UUID domainId) {
        String key = keyFor(level, domainId);
        FileBackedInfiniteStorageEngine engine = ENGINES.get(key);
        if (engine != null) {
            engine.closeAndFlush();
            ENGINES.remove(key);
        }
    }

    public static synchronized void flushAll() {
        for (FileBackedInfiniteStorageEngine engine : ENGINES.values()) {
            engine.flushBudgeted(0L);
        }
    }

    public static synchronized void closeAll() {
        for (FileBackedInfiniteStorageEngine engine : new ArrayList<>(ENGINES.values())) {
            engine.closeAndFlush();
        }
        ENGINES.clear();
    }

    private static String keyFor(ServerLevel level, UUID domainId) {
        String root = sanitize(level.getServer()
                .getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize()
                .toString());
        String dimension = sanitize(level.dimension().location().toString());
        return root + ":" + dimension + ":" + domainId;
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
