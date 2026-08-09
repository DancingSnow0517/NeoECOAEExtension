package cn.dancingsnow.neoecoae.multiblock.cluster;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class NECraftingNetworkClusterTest {
    @Test
    void fastPathCacheBelongsToTheLogicalNetwork() {
        var network = new NECraftingNetworkCluster();

        assertSame(network.getFastPathCache(), network.getFastPathCache());
    }
}
