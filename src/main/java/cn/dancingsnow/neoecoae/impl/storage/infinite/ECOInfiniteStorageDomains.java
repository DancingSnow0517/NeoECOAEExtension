package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOInfiniteStorageDomains {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOInfiniteStorageDomains.class);
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
            // A controller or a mounted storage wrapper in another dimension may still reference this world-global
            // domain. Keep one engine instance until server shutdown and only force its current state to disk here.
            engine.flushBudgeted(0L);
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
        String root = level.getServer()
                .getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize()
                .toString();
        return root + ":" + domainId;
    }

    private static Path domainPath(ServerLevel level, UUID domainId) {
        Path storageRoot = level.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("neoecoae_storage");
        return resolveDomainPath(storageRoot, domainId);
    }

    static Path resolveDomainPath(Path storageRoot, UUID domainId) {
        Path globalPath = storageRoot.resolve("domain_" + domainId);
        if (Files.isDirectory(globalPath)) {
            return globalPath;
        }

        Path legacyPath = findLegacyDomainPath(storageRoot, domainId);
        if (legacyPath == null) {
            return globalPath;
        }
        try {
            Files.createDirectories(storageRoot);
            Files.move(legacyPath, globalPath);
            LOGGER.info("Migrated ECO infinite storage domain {} from {} to {}", domainId, legacyPath, globalPath);
            return globalPath;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to migrate ECO infinite storage domain " + domainId, e);
        }
    }

    private static Path findLegacyDomainPath(Path storageRoot, UUID domainId) {
        if (!Files.isDirectory(storageRoot)) {
            return null;
        }
        String domainDirectory = "domain_" + domainId;
        List<Path> matches = new ArrayList<>();
        try (var children = Files.list(storageRoot)) {
            children
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().startsWith("dim_"))
                .map(path -> path.resolve(domainDirectory))
                .filter(Files::isDirectory)
                .forEach(matches::add);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect legacy ECO infinite storage domains", e);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException(
                "Multiple legacy ECO infinite storage domains share UUID " + domainId + ": " + matches
            );
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

}
