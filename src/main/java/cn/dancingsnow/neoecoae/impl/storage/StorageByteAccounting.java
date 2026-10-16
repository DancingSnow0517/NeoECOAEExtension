package cn.dancingsnow.neoecoae.impl.storage;

import java.math.BigInteger;

/** @deprecated Use the version-independent core storage arithmetic. */
@Deprecated
public final class StorageByteAccounting {
    private StorageByteAccounting() {}
    public static long remainingInsertAmount(long totalBytes, long usedBytes, long bucket, long amountPerByte) {
        return cn.dancingsnow.neoecoae.core.storage.StorageByteAccounting.remainingInsertAmount(
            totalBytes, usedBytes, bucket, amountPerByte);
    }
    public static long remainingInsertAmount(long totalBytes, BigInteger usedBytes, BigInteger bucket,
            long amountPerByte, long bytesPerType, boolean newType) {
        return cn.dancingsnow.neoecoae.core.storage.StorageByteAccounting.remainingInsertAmount(
            totalBytes, usedBytes, bucket, amountPerByte, bytesPerType, newType);
    }
    public static BigInteger usedBytes(long storedTypes, BigInteger amount, long amountPerByte, long bytesPerType) {
        return cn.dancingsnow.neoecoae.core.storage.StorageByteAccounting.usedBytes(
            storedTypes, amount, amountPerByte, bytesPerType);
    }
}
