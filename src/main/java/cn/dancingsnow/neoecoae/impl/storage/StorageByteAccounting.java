package cn.dancingsnow.neoecoae.impl.storage;

import java.math.BigInteger;

/** Shared, overflow-safe capacity arithmetic for finite cells and infinite domains. */
public final class StorageByteAccounting {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private StorageByteAccounting() {}

    public static BigInteger usedBytes(long types, BigInteger amount, long amountPerByte, long bytesPerType) {
        BigInteger safeAmount = amount == null ? BigInteger.ZERO : amount.max(BigInteger.ZERO);
        BigInteger perByte = BigInteger.valueOf(Math.max(1L, amountPerByte));
        BigInteger[] parts = safeAmount.divideAndRemainder(perByte);
        BigInteger contentBytes = parts[0].add(parts[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE);
        return contentBytes.add(BigInteger.valueOf(Math.max(0L, types))
                .multiply(BigInteger.valueOf(Math.max(0L, bytesPerType))));
    }

    public static long remainingInsertAmount(long totalBytes, BigInteger usedBytes,
                                             BigInteger currentAmount, long amountPerByte,
                                             long bytesPerType, boolean newType) {
        if (totalBytes <= 0 || usedBytes == null || currentAmount == null
                || usedBytes.signum() < 0 || currentAmount.signum() < 0) return 0L;
        BigInteger free = BigInteger.valueOf(totalBytes).subtract(usedBytes);
        if (free.signum() < 0) return 0L;
        BigInteger perByte = BigInteger.valueOf(Math.max(1L, amountPerByte));
        BigInteger bucketRemainder = currentAmount.signum() == 0 ? BigInteger.ZERO
                : perByte.subtract(currentAmount.mod(perByte)).mod(perByte);
        if (newType) free = free.subtract(BigInteger.valueOf(Math.max(0L, bytesPerType)));
        if (free.signum() < 0) return 0L;
        return free.multiply(perByte).add(bucketRemainder).min(MAX_LONG).longValue();
    }

    public static long remainingInsertAmount(long totalBytes, long usedBytes,
                                             long currentAmount, long amountPerByte) {
        return remainingInsertAmount(totalBytes, BigInteger.valueOf(usedBytes),
                BigInteger.valueOf(currentAmount), amountPerByte, 0L, false);
    }

    /** Capacity remaining for a key in a finite cell, including an unfinished byte bucket. */
    public static long remainingForCell(long totalBytes, long storedTypes, BigInteger storedAmount,
                                        long currentAmount, long amountPerByte, long bytesPerType) {
        if (currentAmount < 0L) return 0L;
        BigInteger used = usedBytes(storedTypes, storedAmount, amountPerByte, bytesPerType);
        return remainingInsertAmount(totalBytes, used, BigInteger.valueOf(currentAmount),
                amountPerByte, bytesPerType, currentAmount == 0L);
    }
}
