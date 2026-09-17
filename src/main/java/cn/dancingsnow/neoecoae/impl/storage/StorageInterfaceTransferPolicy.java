package cn.dancingsnow.neoecoae.impl.storage;

public final class StorageInterfaceTransferPolicy {
    public static boolean shouldImportNetworkAmount(long amount, boolean allowInfiniteStorageImport) {
        return amount > 0L && (allowInfiniteStorageImport || !isInfiniteNetworkAmount(amount));
    }

    private static boolean isInfiniteNetworkAmount(long amount) {
        return amount == Long.MAX_VALUE || amount == Integer.MAX_VALUE;
    }

    private StorageInterfaceTransferPolicy() {}
}
