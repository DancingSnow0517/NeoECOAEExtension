package cn.dancingsnow.neoecoae.storage.transfer;

import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StorageExtractionExclusionsTest {
    @Test
    void bulkMigrationExcludesEveryBulkCellAndRestoresNestedScopes() {
        var first = mock(ECOBulkStorageCell.class);
        var second = mock(ECOBulkStorageCell.class);
        var ordinary = mock(MEStorage.class);
        try (var scope = StorageExtractionExclusions.openBulkMigration()) {
            assertTrue(StorageExtractionExclusions.contains(first));
            assertTrue(StorageExtractionExclusions.contains(second));
            assertFalse(StorageExtractionExclusions.contains(ordinary));
            try (var nested = StorageExtractionExclusions.open(List.of(ordinary))) {
                assertTrue(StorageExtractionExclusions.contains(first));
                assertTrue(StorageExtractionExclusions.contains(ordinary));
            }
            assertTrue(StorageExtractionExclusions.contains(second));
            assertFalse(StorageExtractionExclusions.contains(ordinary));
        }
        assertFalse(StorageExtractionExclusions.contains(first));
        assertFalse(StorageExtractionExclusions.contains(second));
    }

    @Test
    void excludesDestinationsAndRestoresOuterScopeAfterFailure() {
        var first = mock(MEStorage.class);
        var second = mock(MEStorage.class);
        var source = mock(MEStorage.class);
        try (var outer = StorageExtractionExclusions.open(List.of(first))) {
            assertTrue(StorageExtractionExclusions.contains(first));
            assertFalse(StorageExtractionExclusions.contains(source));
            assertThrows(IllegalStateException.class, () -> {
                try (var inner = StorageExtractionExclusions.open(List.of(second))) {
                    assertTrue(StorageExtractionExclusions.contains(first));
                    assertTrue(StorageExtractionExclusions.contains(second));
                    assertFalse(StorageExtractionExclusions.contains(source));
                    throw new IllegalStateException("test");
                }
            });
            assertTrue(StorageExtractionExclusions.contains(first));
            assertFalse(StorageExtractionExclusions.contains(second));
        }
        assertFalse(StorageExtractionExclusions.contains(first));
    }
}
