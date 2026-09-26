package cn.dancingsnow.neoecoae.multiblock.network;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NELogicalNetworkManagerTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @AfterEach
    void clearNetworks() {
        NELogicalNetworkManager.clearAll();
    }

    @ParameterizedTest
    @EnumSource(IGridNodeListener.State.class)
    void computationHostsRejoinWhenSameGridBecomesOnline(IGridNodeListener.State reason) {
        ServerLevel level = level();
        IManagedGridNode node = node();
        List<NEComputationCluster> clusters = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            var controller = controller(ECOComputationSystemBlockEntity.class, level, node, i);
            var cluster = new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO);
            when(controller.hasNetworkSwitch()).thenReturn(true);
            when(controller.hasNetworkFrequency()).thenReturn(true);
            when(controller.getNetworkFrequency()).thenReturn(7);
            doCallRealMethod().when(controller).updateCluster(cluster);
            controller.updateCluster(cluster);
            cluster.addBlockEntity(controller);
            NELogicalNetworkManager.attach(cluster);
            clusters.add(cluster);
        }
        NELogicalNetworkManager.tick(level);
        clusters.forEach(cluster -> assertNull(cluster.getNetworkCluster()));

        // Boot may finish arbitrarily later; grid identity and frequency never change.
        when(node.isOnline()).thenReturn(true);
        clusters.forEach(cluster -> cluster.getController().onMainNodeStateChanged(reason));
        clusters.forEach(cluster -> assertNull(cluster.getNetworkCluster()));
        NELogicalNetworkManager.tick(level);
        var network = clusters.getFirst().getNetworkCluster();
        assertNotNull(network);
        assertEquals(8, network.getMembers().size());
        clusters.forEach(cluster -> assertSame(network, cluster.getNetworkCluster()));

        when(node.isOnline()).thenReturn(false);
        clusters.forEach(cluster -> cluster.getController().onMainNodeStateChanged(reason));
        NELogicalNetworkManager.tick(level);
        clusters.forEach(cluster -> assertNull(cluster.getNetworkCluster()));
        when(node.isOnline()).thenReturn(true);
        clusters.forEach(cluster -> cluster.getController().onMainNodeStateChanged(reason));
        NELogicalNetworkManager.tick(level);
        assertEquals(8, clusters.getFirst().getNetworkCluster().getMembers().size());
    }

    @org.junit.jupiter.api.Test
    void computationSelectionModeIsSharedAcrossNetworkMembers() {
        ServerLevel level = level();
        IManagedGridNode node = node();
        when(node.isOnline()).thenReturn(true);
        List<NEComputationCluster> clusters = new ArrayList<>();
        List<ECOComputationSystemBlockEntity> controllers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var controller = controller(ECOComputationSystemBlockEntity.class, level, node, i);
            when(controller.hasNetworkSwitch()).thenReturn(true);
            when(controller.hasNetworkFrequency()).thenReturn(true);
            when(controller.getNetworkFrequency()).thenReturn(4);
            when(controller.getCpuSelectionMode()).thenReturn(
                i == 0 ? CpuSelectionMode.PLAYER_ONLY : CpuSelectionMode.MACHINE_ONLY);
            var cluster = new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO);
            doCallRealMethod().when(controller).updateCluster(cluster);
            controller.updateCluster(cluster);
            cluster.addBlockEntity(controller);
            cluster.updateFormed(true);
            NELogicalNetworkManager.attach(cluster);
            clusters.add(cluster);
            controllers.add(controller);
        }

        var network = clusters.getFirst().getNetworkCluster();
        assertNotNull(network);
        clusters.forEach(cluster -> assertEquals(CpuSelectionMode.PLAYER_ONLY, cluster.getSelectionMode()));

        clusters.getLast().setSelectionMode(CpuSelectionMode.ANY);

        clusters.forEach(cluster -> assertEquals(CpuSelectionMode.ANY, cluster.getSelectionMode()));
        controllers.forEach(controller -> verify(controller).setCpuSelectionMode(CpuSelectionMode.ANY));
    }

    @ParameterizedTest
    @EnumSource(IGridNodeListener.State.class)
    void craftingHostsRejoinWhenSameGridBecomesOnline(IGridNodeListener.State reason) {
        ServerLevel level = level();
        IManagedGridNode node = node();
        List<NECraftingCluster> clusters = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            var controller = controller(ECOCraftingSystemBlockEntity.class, level, node, i);
            var cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
            when(controller.hasNetworkSwitch()).thenReturn(true);
            when(controller.hasNetworkFrequency()).thenReturn(true);
            when(controller.getNetworkFrequency()).thenReturn(7);
            doCallRealMethod().when(controller).updateCluster(cluster);
            controller.updateCluster(cluster);
            cluster.addBlockEntity(controller);
            NELogicalNetworkManager.attach(cluster);
            clusters.add(cluster);
        }
        NELogicalNetworkManager.tick(level);
        clusters.forEach(cluster -> assertNull(cluster.getNetworkCluster()));
        when(node.isOnline()).thenReturn(true);
        clusters.forEach(cluster -> cluster.getController().onMainNodeStateChanged(reason));
        NELogicalNetworkManager.tick(level);
        var network = clusters.getFirst().getNetworkCluster();
        assertNotNull(network);
        assertEquals(8, network.getMembers().size());
        clusters.forEach(cluster -> assertSame(network, cluster.getNetworkCluster()));

        // Unloading the level must discard queued callbacks and associations.
        clusters.forEach(cluster -> cluster.getController().onMainNodeStateChanged(reason));
        NELogicalNetworkManager.clear(level);
        NELogicalNetworkManager.tick(level);
        clusters.forEach(cluster -> assertNull(cluster.getNetworkCluster()));
    }

    private static ServerLevel level() {
        var level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(mock(MinecraftServer.class));
        return level;
    }

    private static IManagedGridNode node() {
        var node = mock(IManagedGridNode.class);
        when(node.getGrid()).thenReturn(mock(IGrid.class));
        return node;
    }

    private static <T extends NEBlockEntity<?, ?>> T controller(
        Class<T> type, ServerLevel level, IManagedGridNode node, int index
    ) {
        // Exercise the production callbacks, keeping rendering and the game launcher out of this test.
        T controller = mock(type, invocation -> switch (invocation.getMethod().getName()) {
            case "onMainNodeStateChanged", "onMainNodeGridChanged", "isServerStopping", "getCluster" ->
                invocation.callRealMethod();
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
        when(controller.getLevel()).thenReturn(level);
        when(controller.getMainNode()).thenReturn(node);
        when(controller.getBlockPos()).thenReturn(new BlockPos(index, 0, 0));
        return controller;
    }
}
