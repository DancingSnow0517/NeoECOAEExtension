package cn.dancingsnow.neoecoae.multiblock.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class NECraftingClusterTest {
    @Test
    void configuredMultiplierComesFromTheInstalledSwitch() {
        NECraftingCluster cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);

        assertEquals(1, cluster.getConfiguredNetworkMultiplier());

        cluster.setNetworkMode(true);
        assertEquals(2, cluster.getConfiguredNetworkMultiplier());

        cluster.setHighEnergyNetworkMode(true);
        assertEquals(8, cluster.getConfiguredNetworkMultiplier());
    }

    @Test
    void exchangeMultiplierRequiresAtLeastTwoHosts() {
        assertEquals(1, NECraftingCluster.resolveNetworkMultiplier(true, false, 1, true));
        assertEquals(1, NECraftingCluster.resolveNetworkMultiplier(true, true, 1, true));
    }

    @Test
    void exchangeMultiplierUsesTheInstalledSwitchType() {
        assertEquals(4, NECraftingCluster.resolveNetworkMultiplier(true, false, 2, true));
        assertEquals(16, NECraftingCluster.resolveNetworkMultiplier(true, true, 2, true));
    }

    @Test
    void exchangeMultiplierRequiresNetworkButNotCooling() {
        assertEquals(1, NECraftingCluster.resolveNetworkMultiplier(false, true, 2, true));
        assertEquals(4, NECraftingCluster.resolveNetworkMultiplier(true, false, 2, false));
        assertEquals(64, NECraftingCluster.resolveNetworkMultiplier(true, true, 8, false));
    }
}
