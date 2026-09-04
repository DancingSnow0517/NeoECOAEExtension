package cn.dancingsnow.neoecoae.impl.storage.transfer;

import java.util.concurrent.atomic.AtomicLong;

public final class ECOSophisticatedMetrics {
    static final AtomicLong INDEXED_EXTRACTS = new AtomicLong();
    static final AtomicLong FALLBACK_EXTRACTS = new AtomicLong();
    static final AtomicLong MATCHING_SLOTS = new AtomicLong();
    static final AtomicLong AVOIDED_SLOT_SCANS = new AtomicLong();
    static final AtomicLong DIRTY_SLOT_EVENTS = new AtomicLong();
    static final AtomicLong DEFERRED_SAVES = new AtomicLong();
    static final AtomicLong BATCH_FLUSHES = new AtomicLong();
    static final AtomicLong FLUSH_FAILURES = new AtomicLong();

    private ECOSophisticatedMetrics() {
    }

    public static Snapshot snapshot() {
        return new Snapshot(INDEXED_EXTRACTS.get(), FALLBACK_EXTRACTS.get(), MATCHING_SLOTS.get(),
            AVOIDED_SLOT_SCANS.get(), DIRTY_SLOT_EVENTS.get(), DEFERRED_SAVES.get(),
            BATCH_FLUSHES.get(), FLUSH_FAILURES.get());
    }

    public record Snapshot(long indexedExtracts, long fallbackExtracts, long matchingSlots,
                           long avoidedSlotScans, long dirtySlotEvents, long deferredSaves,
                           long batchFlushes, long domainFlushFailures) {
    }
}
