package cn.dancingsnow.neoecoae.integration;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Optional-integration bridge for server-side storage-cell marking. */
public final class StorageBulkMarkingIntegration {
    private static Handler handler;
    private static Predicate<ECOStorageSystemBlockEntity> bulkCellDetector = host -> false;
    private static UnaryOperator<ItemStack> markerNormalizer = stack -> ItemStack.EMPTY;
    private static BiPredicate<ItemStack, ItemStack> sameMarkerChain = (left, right) -> false;

    private StorageBulkMarkingIntegration() {
    }

    public static void register(
        Handler handler,
        Predicate<ECOStorageSystemBlockEntity> bulkCellDetector,
        UnaryOperator<ItemStack> markerNormalizer,
        BiPredicate<ItemStack, ItemStack> sameMarkerChain
    ) {
        StorageBulkMarkingIntegration.handler = handler;
        StorageBulkMarkingIntegration.bulkCellDetector = bulkCellDetector;
        StorageBulkMarkingIntegration.markerNormalizer = markerNormalizer;
        StorageBulkMarkingIntegration.sameMarkerChain = sameMarkerChain;
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
