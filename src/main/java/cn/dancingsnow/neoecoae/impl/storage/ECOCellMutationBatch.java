package cn.dancingsnow.neoecoae.impl.storage;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;
import org.slf4j.LoggerFactory;

/** Coalesces component serialization inside one controller tick; live amounts remain authoritative. */
public final class ECOCellMutationBatch implements AutoCloseable {
    private static final ThreadLocal<ECOCellMutationBatch> ACTIVE = new ThreadLocal<>();
    private static final Set<ECOStorageCell> RETRY = java.util.Collections.newSetFromMap(new WeakHashMap<>());
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
            flush(cell);
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
