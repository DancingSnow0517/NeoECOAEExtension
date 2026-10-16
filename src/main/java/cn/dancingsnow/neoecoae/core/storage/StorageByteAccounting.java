package cn.dancingsnow.neoecoae.core.storage;

import cn.dancingsnow.neoecoae.core.math.NEMath;

import java.math.BigInteger;

/**
 * Shared byte-capacity arithmetic for storage cells and externally persisted storage domains.
 */
public final class StorageByteAccounting {
    private StorageByteAccounting() {}

    public static long usedBytes(long storedTypes, long storedAmount, long amountPerByte, long bytesPerType) {
        amountPerByte = Math.max(1L, amountPerByte);
        storedAmount = Math.max(0L, storedAmount);
        long content = storedAmount / amountPerByte + (storedAmount % amountPerByte == 0 ? 0 : 1);
        return NEMath.saturatingAdd(content,
            NEMath.saturatingMultiply(Math.max(0L, storedTypes), Math.max(0L, bytesPerType)));
    }

    /** Partial bytes remain fillable even when a legacy cell is already over capacity. */
    public static long remainingCellAmount(long totalBytes, long usedBytes, long amount, long amountPerByte) {
        amountPerByte = Math.max(1L, amountPerByte);
        long partial = amount <= 0 ? 0 : (amountPerByte - amount % amountPerByte) % amountPerByte;
        return NEMath.saturatingAdd(partial,
            NEMath.saturatingMultiply(Math.max(0L, totalBytes - usedBytes), amountPerByte));
    }

    public static long migrationCapacity(long totalBytes, long totalTypes, long bytesPerType, long amountPerByte,
                                         long currentAmount, long storedTypes, long storedAmount) {
        amountPerByte = Math.max(1L, amountPerByte);
        long used = usedBytes(storedTypes, storedAmount, amountPerByte, bytesPerType);
        long free = Math.max(0L, totalBytes - used);
        long remaining = remainingCellAmount(totalBytes, used, storedAmount, amountPerByte);
        if (currentAmount <= 0) {
            long partial = storedAmount <= 0 ? 0 : (amountPerByte - storedAmount % amountPerByte) % amountPerByte;
            boolean typeAvailable = storedTypes < totalTypes && bytesPerType > 0 && free / bytesPerType > 0;
            if (!typeAvailable || free < bytesPerType || free == bytesPerType && partial == 0) return 0;
            remaining = Math.max(0L, remaining - NEMath.saturatingMultiply(bytesPerType, amountPerByte));
        }
        return Math.max(0L, Math.min(Long.MAX_VALUE - Math.max(0L, currentAmount), remaining));
    }

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
        return NEMath.saturatingAdd(unitsToCompleteBucket, NEMath.saturatingMultiply(freeBytes, amountPerByte));
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
}
