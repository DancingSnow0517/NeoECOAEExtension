package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ECOInfiniteMigrationProgressTest {
    @Test void progressCombinesCompletedSourcesWithTheShareOfTheCurrentOne() {
        assertEquals(0.0D, ECOInfiniteStorageModeController.combinedMigrationProgress(0, 0.0D, 12), 1e-12);
        // One of twelve sources half done is visible immediately instead of staying at 0%.
        assertEquals(0.5D / 12, ECOInfiniteStorageModeController.combinedMigrationProgress(0, 0.5D, 12), 1e-12);
        // Finishing a source moves its share from "partial" to "completed" without a step back.
        double before = ECOInfiniteStorageModeController.combinedMigrationProgress(0, 1.0D, 12);
        double after = ECOInfiniteStorageModeController.combinedMigrationProgress(1, 0.0D, 11);
        assertEquals(before, after, 1e-12);
        assertEquals(1.0D, ECOInfiniteStorageModeController.combinedMigrationProgress(12, 0.0D, 0), 1e-12);
        assertEquals(1.0D, ECOInfiniteStorageModeController.combinedMigrationProgress(0, 0.0D, 0), 1e-12);
        assertEquals(1.0D, ECOInfiniteStorageModeController.combinedMigrationProgress(1, 5.0D, 1), 1e-12,
            "a share above one per source is clamped");
    }
}
