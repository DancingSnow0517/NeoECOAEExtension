package cn.dancingsnow.neoecoae.integration;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;

import java.util.function.Predicate;

/** Optional-integration bridge for server-side storage-cell marking. */
public final class StorageBulkMarkingIntegration {
    private static Handler handler;
    private static Predicate<ECOStorageSystemBlockEntity> bulkCellDetector = host -> false;

    private StorageBulkMarkingIntegration() {
    }

    public static void register(
        Handler handler,
        Predicate<ECOStorageSystemBlockEntity> bulkCellDetector
    ) {
        StorageBulkMarkingIntegration.handler = handler;
        StorageBulkMarkingIntegration.bulkCellDetector = bulkCellDetector;
    }

    public static boolean isAvailable() {
        return handler != null;
    }

    public static boolean hasBulkCell(ECOStorageSystemBlockEntity host) {
        return handler != null && bulkCellDetector.test(host);
    }

    public static MarkResult autoMark(ECOStorageSystemBlockEntity host, long threshold) {
        if (handler == null) {
            return new MarkResult(Status.UNAVAILABLE, 0, 0, 0, 0, 0L);
        }
        return handler.autoMark(host, threshold);
    }

    @FunctionalInterface
    public interface Handler {
        MarkResult autoMark(ECOStorageSystemBlockEntity host, long threshold);
    }

    public record MarkResult(
        Status status,
        int added,
        int alreadyMarked,
        int notCompressible,
        int noSpace,
        long transferred
    ) {
    }

    public enum Status {
        SUCCESS,
        NO_BULK_CELL,
        BUSY,
        INVALID_THRESHOLD,
        UNAVAILABLE
    }
}
