package cn.dancingsnow.neoecoae.impl.storage;

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

        BigInteger freeBytes = BigInteger.valueOf(totalBytes).subtract(usedBytes);
        if (freeBytes.signum() < 0) {
            return 0L;
        }

        amountPerByte = Math.max(1L, amountPerByte);
        BigInteger amountPerByteValue = BigInteger.valueOf(amountPerByte);
        long unitsToCompleteBucket = targetBucketAmount.signum() <= 0
            ? 0L
            : amountPerByteValue.subtract(targetBucketAmount.mod(amountPerByteValue))
                .mod(amountPerByteValue).longValue();

        if (newType) {
            freeBytes = freeBytes.subtract(BigInteger.valueOf(Math.max(0L, bytesPerType)));
            if (freeBytes.signum() < 0 || freeBytes.signum() == 0 && unitsToCompleteBucket == 0L) {
                return 0L;
            }
        }

        BigInteger acceptable = freeBytes.multiply(amountPerByteValue)
            .add(BigInteger.valueOf(unitsToCompleteBucket));
        return acceptable.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    public static BigInteger usedBytes(
        long storedTypes,
        BigInteger storedAmount,
        long amountPerByte,
        long bytesPerType
    ) {
        amountPerByte = Math.max(1L, amountPerByte);
        BigInteger normalizedAmount = storedAmount == null || storedAmount.signum() <= 0
            ? BigInteger.ZERO : storedAmount;
        BigInteger divisor = BigInteger.valueOf(amountPerByte);
        BigInteger[] division = normalizedAmount.divideAndRemainder(divisor);
        BigInteger contentBytes = division[0].add(division[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE);
        return contentBytes.add(
            BigInteger.valueOf(Math.max(0L, storedTypes)).multiply(BigInteger.valueOf(Math.max(0L, bytesPerType)))
        );
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
