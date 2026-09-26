package cn.dancingsnow.neoecoae.impl.storage;

import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.LoggerFactory;

/** Coalesces component serialization inside one controller tick; live amounts remain authoritative. */
public final class ECOCellMutationBatch implements AutoCloseable {
    private static final ThreadLocal<ECOCellMutationBatch> ACTIVE = new ThreadLocal<>();
    // Persistence obligations must retain their owner until they succeed or the server-stop drain reports failure.
    private static final Set<ECOStorageCell> RETRY = new LinkedHashSet<>();
    private final ECOCellMutationBatch parent;
    private final Set<ECOStorageCell> changed = new LinkedHashSet<>();
    private boolean closed;

    private ECOCellMutationBatch() { parent = ACTIVE.get(); ACTIVE.set(this); }
    public static ECOCellMutationBatch open() { return new ECOCellMutationBatch(); }
    static boolean defer(ECOStorageCell cell) {
        ECOCellMutationBatch batch = ACTIVE.get();
        if (batch == null) return false;
        batch.changed.add(cell);
        return true;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (parent != null) {
            ACTIVE.set(parent);
            parent.changed.addAll(changed);
        } else {
            ACTIVE.remove();
            for (ECOStorageCell cell : changed) flush(cell);
        }
    }

    public static void retry() {
        int remaining = 16;
        for (ECOStorageCell cell : new java.util.ArrayList<>(RETRY)) {
            if (remaining-- <= 0) break;
            // Failed entries are re-added at the tail by flush(), so one permanently failing
            // cell cannot starve every persistence obligation queued behind it.
            RETRY.remove(cell);
            flush(cell);
        }
    }

    public static void drainRetries() {
        for (ECOStorageCell cell : new java.util.ArrayList<>(RETRY)) flush(cell);
        if (!RETRY.isEmpty()) {
            LoggerFactory.getLogger(ECOCellMutationBatch.class)
                .error("{} ECO cell persistence operation(s) still failed during server shutdown", RETRY.size());
        }
    }

    /** Drops an accidentally unclosed server-thread scope at the server lifecycle boundary. */
    public static void clearThreadState() {
        ECOCellMutationBatch batch = ACTIVE.get();
        ACTIVE.remove();
        Set<ECOStorageCell> pending = new LinkedHashSet<>();
        while (batch != null) {
            pending.addAll(batch.changed);
            batch.changed.clear();
            batch.closed = true;
            batch = batch.parent;
        }
        for (ECOStorageCell cell : pending) flush(cell);
    }

    public static void assertClean() {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("ECOCellMutationBatch scope leaked across a server tick");
        }
    }

    private static void flush(ECOStorageCell cell) {
        try {
            cell.flushBatchedChanges();
            RETRY.remove(cell);
        } catch (RuntimeException e) {
            if (RETRY.add(cell)) LoggerFactory.getLogger(ECOCellMutationBatch.class)
                .error("ECO cell component save failed; in-memory contents retained for retry", e);
        }
    }
}
