package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOInfiniteStorageDomains {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOInfiniteStorageDomains.class);
    private static final long TICK_CHECKPOINT_BUDGET_NANOS = 1_000_000L;
    private static final Map<String, FileBackedInfiniteStorageEngine> ENGINES = new HashMap<>();
    private static final Set<UUID> LOGGED_FAILURES = new HashSet<>();

    private ECOInfiniteStorageDomains() {}

    public static synchronized FileBackedInfiniteStorageEngine get(ServerLevel level, UUID domainId) {
        String key = keyFor(level, domainId);
        return ENGINES.computeIfAbsent(key, ignored -> {
            FileBackedInfiniteStorageEngine engine = new FileBackedInfiniteStorageEngine(
                level.registryAccess(), domainId, domainPath(level, domainId));
            engine.beginAsyncLoad();
            return engine;
        });
    }

    public static boolean exists(ServerLevel level, UUID domainId) {
        Path storageRoot = storageRoot(level);
        if (Files.isDirectory(storageRoot.resolve("domain_" + domainId))) {
            return true;
        }
        return findLegacyDomainPath(storageRoot, domainId) != null;
    }

    public static synchronized void close(ServerLevel level, UUID domainId) {
        String key = keyFor(level, domainId);
        FileBackedInfiniteStorageEngine engine = ENGINES.get(key);
        if (engine != null) {
            // A controller or a mounted storage wrapper in another dimension may still reference this world-global
            // domain. Keep one engine instance until server shutdown and only force its current state to disk here.
            runIsolated(engine, "checkpoint while detaching controller", () -> engine.flushBudgeted(0L));
        }
    }

    public static synchronized void flushWalForWorldSave() {
        for (FileBackedInfiniteStorageEngine engine : ENGINES.values()) {
            runIsolated(engine, "WAL flush during world save", () -> {
                engine.submitPendingWal();
                engine.awaitPendingWal();
            });
        }
    }

    public static synchronized void awaitPreviousTick() {
        // The writer normally finishes while the server is between ticks. Waiting here bounds the durability window
        // to one tick without forcing the WAL from every storage-system block entity tick.
        for (FileBackedInfiniteStorageEngine engine : ENGINES.values()) {
            runIsolated(engine, "WAL submission", engine::submitPendingWal);
        }
        for (FileBackedInfiniteStorageEngine engine : ENGINES.values()) {
            runIsolated(engine, "WAL wait", engine::awaitPendingWal);
        }
    }

    public static synchronized void flushTick() {
        for (FileBackedInfiniteStorageEngine engine : ENGINES.values()) {
            runIsolated(engine, "WAL submission", engine::submitPendingWal);
        }
        long deadline = System.nanoTime() + TICK_CHECKPOINT_BUDGET_NANOS;
        for (FileBackedInfiniteStorageEngine engine : ENGINES.values()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                break;
            }
            runIsolated(engine, "budgeted checkpoint", () -> engine.checkpointBudgeted(remaining));
        }
    }

    public static synchronized void closeAll() {
        try {
            for (FileBackedInfiniteStorageEngine engine : new ArrayList<>(ENGINES.values())) {
                try {
                    engine.closeAndFlush();
                } catch (RuntimeException e) {
                    LOGGER.error("Unable to close an ECO infinite storage domain", e);
                }
            }
        } finally {
            ENGINES.clear();
            LOGGED_FAILURES.clear();
            ECOInfiniteStorageIoWorker.shutdown();
        }
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
        return resolveDomainPath(storageRoot(level), domainId);
    }

    private static Path storageRoot(ServerLevel level) {
        Path worldRoot = level.getServer()
                .getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize();
        Path storageRoot = worldRoot.resolve("neoecoae_storage");
        Path legacyStorageRoot = worldRoot.resolve("data").resolve("neoecoae_storage");
        if (!Files.exists(storageRoot) && Files.isDirectory(legacyStorageRoot)) {
            try {
                Files.move(legacyStorageRoot, storageRoot);
                LOGGER.info("Migrated ECO infinite storage root from {} to {}", legacyStorageRoot, storageRoot);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to migrate ECO infinite storage root", e);
            }
        }
        return storageRoot;
    }

    private static void runIsolated(FileBackedInfiniteStorageEngine engine, String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            if (LOGGED_FAILURES.add(engine.domainId())) {
                LOGGER.error("ECO infinite storage domain {} failed {}; the domain has been isolated",
                    engine.domainId(), operation, e);
            }
        }
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
