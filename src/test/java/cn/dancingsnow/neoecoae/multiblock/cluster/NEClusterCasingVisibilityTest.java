package cn.dancingsnow.neoecoae.multiblock.cluster;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NEClusterCasingVisibilityTest {
    private static final BlockPos MIN = BlockPos.ZERO;
    private static final BlockPos MAX = new BlockPos(1, 2, 1);

    @Test
    void computationClusterHidesEveryCasingWhenFormed() {
        assertTrue(new NEComputationCluster(MIN, MAX).hideAllCasingsWhenFormed());
    }

    @Test
    void storageAndCraftingKeepLocalizedCasingHiding() {
        assertFalse(new NEStorageCluster(MIN, MAX).hideAllCasingsWhenFormed());
        assertFalse(new NECraftingCluster(MIN, MAX).hideAllCasingsWhenFormed());
    }
}
