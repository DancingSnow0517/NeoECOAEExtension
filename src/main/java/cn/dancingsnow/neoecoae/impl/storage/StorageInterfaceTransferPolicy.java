package cn.dancingsnow.neoecoae.impl.storage;

public final class StorageInterfaceTransferPolicy {
    public static boolean shouldImportNetworkAmount(long amount, boolean allowInfiniteStorageImport) {
        // Amounts alone do not identify a creative source: finite cells may contain these exact values.
        // The ME mount filter checks IECOUnboundedSource instead.
        return amount > 0L;
    }

    private StorageInterfaceTransferPolicy() {}
}
