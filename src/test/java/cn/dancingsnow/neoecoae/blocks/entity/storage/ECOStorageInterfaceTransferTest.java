package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.impl.storage.StorageInterfaceTransferPolicy;
import org.junit.jupiter.api.Test;

class ECOStorageInterfaceTransferTest {
    @Test
    void infiniteSentinelsRequireExplicitOptIn() {
        assertFalse(StorageInterfaceTransferPolicy.shouldImportNetworkAmount(Long.MAX_VALUE, false));
        assertFalse(StorageInterfaceTransferPolicy.shouldImportNetworkAmount(Integer.MAX_VALUE, false));
        assertTrue(StorageInterfaceTransferPolicy.shouldImportNetworkAmount(Long.MAX_VALUE, true));
        assertTrue(StorageInterfaceTransferPolicy.shouldImportNetworkAmount(Integer.MAX_VALUE, true));
    }

    @Test
    void regularPositiveAmountsRemainImportable() {
        assertFalse(StorageInterfaceTransferPolicy.shouldImportNetworkAmount(0L, true));
        assertFalse(StorageInterfaceTransferPolicy.shouldImportNetworkAmount(-1L, true));
        assertTrue(StorageInterfaceTransferPolicy.shouldImportNetworkAmount(64L, false));
    }
}
