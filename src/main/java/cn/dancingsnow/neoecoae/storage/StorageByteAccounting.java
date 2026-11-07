package cn.dancingsnow.neoecoae.storage;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.math.BigInteger;

/**
 * Shared byte-capacity arithmetic for storage cells and externally persisted storage domains.
 */
public final class StorageByteAccounting {
    private StorageByteAccounting() {}

    public static long remainingInsertAmount(
        long totalBytes,
        long usedBytes,
        long targetBucketAmount,
        long amountPerByte
    ) {
        if (totalBytes <= 0L || usedBytes < 0L || usedBytes > totalBytes || targetBucketAmount < 0L) {
            return 0L;
        }

        amountPerByte = Math.max(1L, amountPerByte);
        long unitsToCompleteBucket = targetBucketAmount <= 0L
            ? 0L
            : (amountPerByte - targetBucketAmount % amountPerByte) % amountPerByte;
        long freeBytes = totalBytes - usedBytes;
        return saturatingAdd(unitsToCompleteBucket, saturatingMultiply(freeBytes, amountPerByte));
    }

    public static long remainingInsertAmount(
        long totalBytes,
        BigInteger usedBytes,
        BigInteger targetBucketAmount,
        long amountPerByte,
        long bytesPerType,
        boolean newType
    ) {
        if (totalBytes <= 0L || usedBytes == null || targetBucketAmount == null
            || usedBytes.signum() < 0 || targetBucketAmount.signum() < 0) {
            return 0L;
        }

        PlannerAmount freeBytes = PlannerAmount.of(totalBytes).subtract(PlannerAmount.of(usedBytes));
        if (freeBytes.signum() < 0) {
            return 0L;
        }

        amountPerByte = Math.max(1L, amountPerByte);
        PlannerAmount amountPerByteValue = PlannerAmount.of(amountPerByte);
        PlannerAmount bucket = PlannerAmount.of(targetBucketAmount);
        long unitsToCompleteBucket = targetBucketAmount.signum() <= 0
            ? 0L
            : amountPerByteValue.subtract(bucket.remainder(amountPerByteValue))
                .remainder(amountPerByteValue).longValueExact();

        if (newType) {
            freeBytes = freeBytes.subtract(Math.max(0L, bytesPerType));
            if (freeBytes.signum() < 0 || freeBytes.signum() == 0 && unitsToCompleteBucket == 0L) {
                return 0L;
            }
        }

        PlannerAmount acceptable = freeBytes.multiply(amountPerByteValue).add(unitsToCompleteBucket);
        return acceptable.longValueSaturated();
    }

    public static BigInteger usedBytes(
        long storedTypes,
        BigInteger storedAmount,
        long amountPerByte,
        long bytesPerType
    ) {
        return usedBytesQuantity(storedTypes, storedAmount == null ? PlannerAmount.ZERO : PlannerAmount.of(storedAmount),
            amountPerByte, bytesPerType).toBigInteger();
    }

    public static PlannerAmount usedBytesQuantity(long storedTypes, PlannerAmount storedAmount,
            long amountPerByte, long bytesPerType) {
        PlannerAmount normalizedAmount = storedAmount == null || storedAmount.signum() <= 0
            ? PlannerAmount.ZERO : storedAmount;
        return normalizedAmount.ceilDiv(PlannerAmount.of(Math.max(1L, amountPerByte)))
            .add(PlannerAmount.of(Math.max(0L, storedTypes)).multiply(Math.max(0L, bytesPerType)));
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
