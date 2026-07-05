package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOCellStorageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOCellStorageManager.class);
    private static final long LOAD_BUDGET_NANOS = 750_000L;
    private static final long FLUSH_BUDGET_NANOS = 1_000_000L;
    private static final int FLUSH_INTERVAL_TICKS = 20;

    private static final Map<UUID, FileBackedECOStorageBackend> CELLS = new HashMap<>();
    private static final Map<UUID, ISaveProvider> OWNERS = new HashMap<>();
    private static final Map<ISaveProvider, UUID> OWNER_IDS = new IdentityHashMap<>();
    private static int tickCounter;

    private ECOCellStorageManager() {}

    @Nullable public static synchronized ECOStorageBackend getOrCreate(
            ItemStack stack, IBasicECOCellItem cellItem, @Nullable ISaveProvider owner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        boolean hasLegacyContents = ECOCellHandle.hasLegacyContents(stack);
        boolean hadId = ECOCellHandle.getId(stack).isPresent();

        if (server == null) {
            if (hasLegacyContents) {
                ECOCellHandle.updateSummaryFromLegacy(stack, 0L);
            }
            return null;
        }
        if (!hadId && owner == null && !hasLegacyContents) {
            return null;
        }

        if (hasLegacyContents) {
            ECOCellHandle.updateSummaryFromLegacy(stack, 0L);
        }

        UUID id = ECOCellHandle.getOrCreateId(stack);
        if (owner != null) {
            id = forkIfClaimedByAnotherOwner(server, stack, id, owner);
        }

        Path path = cellPath(server, id);
        if (hadId
                && !CELLS.containsKey(id)
                && !Files.exists(path)
                && ECOCellHandle.getVersion(stack) >= ECOCellHandle.VERSION
                && ECOCellHandle.hasNonEmptySummary(stack)
                && !hasLegacyContents) {
            ECOCellHandle.markMissing(stack);
            return null;
        }

        if (owner != null) {
            claim(id, owner);
        }

        ECOCellHandle.clearProblemState(stack);
        UUID backendId = id;
        Path backendPath = path;
        FileBackedECOStorageBackend backend = CELLS.computeIfAbsent(
                backendId,
                ignored -> new FileBackedECOStorageBackend(
                        backendId,
                        backendPath,
                        ECOCellHandle.getStoredTypesSummary(stack),
                        ECOCellHandle.getStoredAmountSummary(stack)));

        if (hasLegacyContents && !backend.hasPersistentData()) {
            List<GenericStack> legacyStacks = ECOCellHandle.readLegacyStacks(stack);
            backend.importLegacyNow(legacyStacks);
            ECOCellHandle.updateSummary(stack, backend, estimateUsedBytes(cellItem, backend));
            ECOCellHandle.clearLegacyContents(stack);
        }

        return backend;
    }

    public static synchronized void forkIfAlreadyPresent(ItemStack stack, Iterable<ItemStack> mountedStacks) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || stack == null || stack.isEmpty()) {
            return;
        }

        UUID id = ECOCellHandle.getId(stack).orElse(null);
        if (id == null) {
            return;
        }

        for (ItemStack mountedStack : mountedStacks) {
            if (mountedStack == null || mountedStack.isEmpty() || mountedStack == stack) {
                continue;
            }
            if (ECOCellHandle.getId(mountedStack).filter(id::equals).isPresent()) {
                forkStorageId(server, stack, id, "duplicate mounted in the same ECO storage host");
                return;
            }
        }
    }

    public static synchronized boolean loadBudgeted(ECOStorageBackend backend, long maxNanos) {
        return backend != null && backend.loadBudgeted(maxNanos);
    }

    public static synchronized void tick() {
        tickCounter++;
        for (FileBackedECOStorageBackend backend : CELLS.values()) {
            backend.loadBudgeted(LOAD_BUDGET_NANOS);
        }
        if (tickCounter % FLUSH_INTERVAL_TICKS == 0) {
            flushBudgeted(FLUSH_BUDGET_NANOS);
        }
    }

    public static synchronized void flushBudgeted(long maxNanos) {
        long deadline = maxNanos <= 0L ? Long.MAX_VALUE : System.nanoTime() + maxNanos;
        for (FileBackedECOStorageBackend backend : CELLS.values()) {
            backend.flushBudgeted(Math.max(0L, deadline - System.nanoTime()));
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
    }

    public static synchronized void closeAll() {
        for (FileBackedECOStorageBackend backend : CELLS.values()) {
            backend.closeAndFlush();
        }
        CELLS.clear();
        OWNERS.clear();
        OWNER_IDS.clear();
    }

    public static synchronized void close(UUID id) {
        FileBackedECOStorageBackend backend = CELLS.remove(id);
        if (backend != null) {
            backend.closeAndFlush();
        }
        ISaveProvider owner = OWNERS.remove(id);
        if (owner != null) {
            OWNER_IDS.remove(owner);
        }
    }

    public static synchronized void release(@Nullable ItemStack stack, @Nullable ISaveProvider owner) {
        if (owner == null) {
            return;
        }
        UUID id = OWNER_IDS.get(owner);
        if (id == null) {
            id = ECOCellHandle.getId(stack).orElse(null);
        }
        if (id != null && OWNERS.get(id) == owner) {
            OWNERS.remove(id);
            OWNER_IDS.remove(owner);
        }
    }

    private static UUID forkIfClaimedByAnotherOwner(
            MinecraftServer server, ItemStack stack, UUID id, ISaveProvider owner) {
        ISaveProvider currentOwner = OWNERS.get(id);
        if (currentOwner == null || currentOwner == owner) {
            return id;
        }
        return forkStorageId(server, stack, id, "storage id is already claimed by another host");
    }

    private static void claim(UUID id, ISaveProvider owner) {
        UUID previousId = OWNER_IDS.get(owner);
        if (previousId != null && !previousId.equals(id) && OWNERS.get(previousId) == owner) {
            OWNERS.remove(previousId);
        }
        OWNERS.put(id, owner);
        OWNER_IDS.put(owner, id);
    }

    private static UUID forkStorageId(MinecraftServer server, ItemStack stack, UUID oldId, String reason) {
        FileBackedECOStorageBackend sourceBackend = CELLS.get(oldId);
        if (sourceBackend != null) {
            sourceBackend.closeAndFlush();
        }

        UUID newId;
        Path newPath;
        do {
            newId = UUID.randomUUID();
            newPath = cellPath(server, newId);
        } while (CELLS.containsKey(newId) || Files.exists(newPath));

        Path oldPath = cellPath(server, oldId);
        if (Files.exists(oldPath)) {
            copyStorageDirectory(oldPath, newPath);
        }
        writeForkManifest(newPath, newId, stack);

        ECOCellHandle.setId(stack, newId);
        ECOCellHandle.clearProblemState(stack);
        LOGGER.warn("Forked duplicated ECO storage matrix UUID {} -> {} ({})", oldId, newId, reason);
        return newId;
    }

    private static void copyStorageDirectory(Path source, Path target) {
        try (var paths = Files.walk(source)) {
            for (Path sourcePath : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else if (Files.isRegularFile(sourcePath) && !sourcePath.getFileName().toString().endsWith(".tmp")) {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(
                            sourcePath,
                            targetPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to fork ECO cell storage " + source + " -> " + target, e);
        }
    }

    private static void writeForkManifest(Path storagePath, UUID id, ItemStack stack) {
        Path manifestPath = storagePath.resolve("manifest.dat");
        CompoundTag tag = null;
        if (Files.isRegularFile(manifestPath)) {
            try (var input = Files.newInputStream(manifestPath)) {
                tag = NbtIo.readCompressed(input);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Unable to read copied ECO cell storage manifest {}", manifestPath, e);
            }
        }
        if (tag == null) {
            tag = new CompoundTag();
            tag.putInt("version", 2);
            tag.putString("kind", "cell");
            tag.putLong("revision", 0L);
            tag.putInt("storedTypes", ECOCellHandle.getStoredTypesSummary(stack));
            tag.putString("storedAmount", Long.toString(ECOCellHandle.getStoredAmountSummary(stack)));
        }
        tag.putUUID("id", id);
        try {
            Files.createDirectories(storagePath);
            try (var output = Files.newOutputStream(manifestPath)) {
                NbtIo.writeCompressed(tag, output);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write forked ECO cell storage manifest " + manifestPath, e);
        }
    }

    private static long estimateUsedBytes(IBasicECOCellItem cellItem, ECOStorageBackend backend) {
        long typeBytes = (long) backend.getStoredTypes() * cellItem.getBytesPerType();
        long amountBytes = backend.getStoredAmount().toLongSaturated() / Math.max(1, cellItem.getKeyType().getAmountPerByte());
        return Math.max(0L, typeBytes + amountBytes);
    }

    private static Path cellPath(MinecraftServer server, UUID id) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("neoecoae")
                .resolve("storage_v2")
                .resolve("cells")
                .resolve(id.toString());
    }
}
