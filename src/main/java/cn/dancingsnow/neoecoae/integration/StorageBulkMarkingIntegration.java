package cn.dancingsnow.neoecoae.integration;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.List;

/** Optional-integration bridge for server-side storage-cell marking. */
public final class StorageBulkMarkingIntegration {
    private static Handler handler;
    private static Predicate<ECOStorageSystemBlockEntity> bulkCellDetector = host -> false;
    private static UnaryOperator<ItemStack> markerNormalizer = stack -> ItemStack.EMPTY;
    private static BiPredicate<ItemStack, ItemStack> sameMarkerChain = (left, right) -> false;
    private static BiFunction<ECOStorageSystemBlockEntity, BlockPos, MigrationResult> cellMigrator =
        (host, position) -> new MigrationResult(Status.UNAVAILABLE, 0L);
    private static BiFunction<ECOStorageSystemBlockEntity, BlockPos, List<ItemStack>> migrationPreview =
        (host, position) -> List.of();

    private StorageBulkMarkingIntegration() {
    }

    public static void register(
        Handler handler,
        Predicate<ECOStorageSystemBlockEntity> bulkCellDetector,
        UnaryOperator<ItemStack> markerNormalizer,
        BiPredicate<ItemStack, ItemStack> sameMarkerChain,
        BiFunction<ECOStorageSystemBlockEntity, BlockPos, MigrationResult> cellMigrator
    ) {
        register(handler, bulkCellDetector, markerNormalizer, sameMarkerChain, cellMigrator,
            (host, position) -> List.of());
    }

    public static void register(
        Handler handler,
        Predicate<ECOStorageSystemBlockEntity> bulkCellDetector,
        UnaryOperator<ItemStack> markerNormalizer,
        BiPredicate<ItemStack, ItemStack> sameMarkerChain,
        BiFunction<ECOStorageSystemBlockEntity, BlockPos, MigrationResult> cellMigrator,
        BiFunction<ECOStorageSystemBlockEntity, BlockPos, List<ItemStack>> migrationPreview
    ) {
        StorageBulkMarkingIntegration.handler = handler;
        StorageBulkMarkingIntegration.bulkCellDetector = bulkCellDetector;
        StorageBulkMarkingIntegration.markerNormalizer = markerNormalizer;
        StorageBulkMarkingIntegration.sameMarkerChain = sameMarkerChain;
        StorageBulkMarkingIntegration.cellMigrator = cellMigrator;
        StorageBulkMarkingIntegration.migrationPreview = migrationPreview;
    }

    public static void register(
        Handler handler,
        Predicate<ECOStorageSystemBlockEntity> bulkCellDetector,
        UnaryOperator<ItemStack> markerNormalizer,
        BiPredicate<ItemStack, ItemStack> sameMarkerChain
    ) {
        register(handler, bulkCellDetector, markerNormalizer, sameMarkerChain,
            (host, position) -> new MigrationResult(Status.UNAVAILABLE, 0L));
    }

    public static boolean isAvailable() {
        return handler != null;
    }

    public static boolean hasBulkCell(ECOStorageSystemBlockEntity host) {
        return handler != null && bulkCellDetector.test(host);
    }

    public static ItemStack normalizeMarker(ItemStack stack) {
        return handler == null || stack == null || stack.isEmpty()
            ? ItemStack.EMPTY
            : markerNormalizer.apply(stack);
    }

    public static boolean isSameMarkerChain(ItemStack left, ItemStack right) {
        return handler != null && left != null && right != null
            && !left.isEmpty() && !right.isEmpty() && sameMarkerChain.test(left, right);
    }

    public static MarkResult autoMark(ECOStorageSystemBlockEntity host, long threshold, boolean migrate) {
        if (handler == null) {
            return new MarkResult(Status.UNAVAILABLE, 0, 0, 0, 0, 0L);
        }
        return handler.autoMark(host, threshold, migrate);
    }

    public static MigrationResult migrateMarkedCell(ECOStorageSystemBlockEntity host, BlockPos position) {
        return handler == null ? new MigrationResult(Status.UNAVAILABLE, 0L)
            : cellMigrator.apply(host, position);
    }

    public static List<ItemStack> previewMarkedCell(ECOStorageSystemBlockEntity host, BlockPos position) {
        return handler == null ? List.of() : migrationPreview.apply(host, position);
    }

    @FunctionalInterface
    public interface Handler {
        MarkResult autoMark(ECOStorageSystemBlockEntity host, long threshold, boolean migrate);
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

    public record MigrationResult(Status status, long transferred) {
    }

    public enum Status {
        SUCCESS,
        NO_BULK_CELL,
        BUSY,
        INVALID_THRESHOLD,
        UNAVAILABLE
    }
}
