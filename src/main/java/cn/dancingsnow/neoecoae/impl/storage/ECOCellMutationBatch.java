package cn.dancingsnow.neoecoae.impl.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.LoggerFactory;

/** Coalesces host save callbacks while leaving the existing WAL and SavedData mutations immediate. */
public final class ECOCellMutationBatch implements AutoCloseable {
    private static final ThreadLocal<ECOCellMutationBatch> ACTIVE = new ThreadLocal<>();
    private static final Set<ECOStorageCell> RETRY = new LinkedHashSet<>();
    private final ECOCellMutationBatch parent;
    private final Set<ECOStorageCell> changed = new LinkedHashSet<>();
    private boolean closed;

    private ECOCellMutationBatch() {
        parent = ACTIVE.get();
        ACTIVE.set(this);
    }

    public static ECOCellMutationBatch open() {
        return new ECOCellMutationBatch();
    }

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
            changed.forEach(ECOCellMutationBatch::flush);
        }
    }

    public static void retry() {
        int remaining = 16;
        for (ECOStorageCell cell : new ArrayList<>(RETRY)) {
            if (remaining-- <= 0) break;
            RETRY.remove(cell);
            flush(cell);
        }
    }

    public static void drainRetries() {
        for (ECOStorageCell cell : new ArrayList<>(RETRY)) flush(cell);
        if (!RETRY.isEmpty())
            LoggerFactory.getLogger(ECOCellMutationBatch.class)
                    .error("{} ECO cell save callback(s) still failed at server shutdown", RETRY.size());
    }

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
        pending.forEach(ECOCellMutationBatch::flush);
    }

    public static void assertClean() {
        if (ACTIVE.get() != null) throw new IllegalStateException("ECOCellMutationBatch scope leaked across a tick");
    }

    private static void flush(ECOStorageCell cell) {
        try {
            cell.flushBatchedChanges();
            RETRY.remove(cell);
        } catch (RuntimeException failure) {
            if (RETRY.add(cell))
                LoggerFactory.getLogger(ECOCellMutationBatch.class)
                        .error("ECO cell host save failed; contents retained for retry", failure);
        }
    }
}
