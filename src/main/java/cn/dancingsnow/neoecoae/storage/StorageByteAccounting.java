package cn.dancingsnow.neoecoae.storage;

import static cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingAdd;
import static cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingMultiply;

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
