package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

public final class ECOCellStorageManager {
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
            ISaveProvider currentOwner = OWNERS.get(id);
            if (currentOwner != null && currentOwner != owner) {
                ECOCellHandle.markLocked(stack);
                return null;
            }
            OWNERS.put(id, owner);
            OWNER_IDS.put(owner, id);
        }

        ECOCellHandle.clearProblemState(stack);
        FileBackedECOStorageBackend backend = CELLS.computeIfAbsent(
                id,
                ignored -> new FileBackedECOStorageBackend(
                        id, path, ECOCellHandle.getStoredTypesSummary(stack), ECOCellHandle.getStoredAmountSummary(stack)));

        if (hasLegacyContents && !backend.hasPersistentData()) {
            List<GenericStack> legacyStacks = ECOCellHandle.readLegacyStacks(stack);
            backend.importLegacyNow(legacyStacks);
            ECOCellHandle.updateSummary(stack, backend, estimateUsedBytes(cellItem, backend));
            ECOCellHandle.clearLegacyContents(stack);
        }

        return backend;
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
