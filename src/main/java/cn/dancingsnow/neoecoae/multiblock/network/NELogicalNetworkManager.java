package cn.dancingsnow.neoecoae.multiblock.network;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Server-thread authority for logical computation/crafting host membership.
 * Groups are scoped by dimension, AE2 grid and persisted network frequency.
 */
public final class NELogicalNetworkManager {
    private static final Map<ServerLevel, LevelState> LEVELS = new WeakHashMap<>();
    private static final int CRAFTING_NETWORK_HOST_LIMIT = NEFrequencyAllocator.HOST_LIMIT;

    private NELogicalNetworkManager() {}

    public static void attach(NECluster<?> cluster) {
        refresh(cluster);
    }

    public static void refresh(NECluster<?> cluster) {
        if (cluster.isDestroyed() || !cluster.isNetworkMode()) {
            detach(cluster);
            return;
        }
        Level level = getLevel(cluster);
        if (!(level instanceof ServerLevel serverLevel)) {
            clearAssociation(cluster);
            return;
        }
        LevelState state = LEVELS.computeIfAbsent(serverLevel, ignored -> new LevelState());
        if (cluster instanceof NECraftingCluster craftingCluster) {
            state.crafting.add(craftingCluster);
            assignCraftingFrequencyIfNeeded(craftingCluster, state.crafting);
            rebuildCrafting(serverLevel, state);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            state.computation.add(computationCluster);
            assignComputationFrequencyIfNeeded(computationCluster, state.computation);
            rebuildComputation(serverLevel, state);
        }
    }

    /**
     * Schedules a topology refresh after AE2 has finished adding/removing the
     * node. This is required when a formed multiblock joins an already-built
     * grid after the physical cluster was created.
     */
    public static void refreshAfterGridChange(NECluster<?> cluster) {
        if (cluster.isDestroyed() || !cluster.isNetworkMode()) {
            return;
        }
        Level level = getLevel(cluster);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        LevelState state = LEVELS.computeIfAbsent(serverLevel, ignored -> new LevelState());
        if (!state.pendingGridRefresh.add(cluster)) {
            return;
        }
        serverLevel.getServer().executeIfPossible(() -> {
            LevelState currentState = LEVELS.get(serverLevel);
            if (currentState != null) {
                currentState.pendingGridRefresh.remove(cluster);
            }
            if (!cluster.isDestroyed() && cluster.isNetworkMode()) {
                refresh(cluster);
            }
        });
    }

    public static void detachBeforeDestroy(NECluster<?> cluster) {
        detach(cluster);
    }

    public static void clearAssociation(NECluster<?> cluster) {
        if (cluster instanceof NECraftingCluster craftingCluster) {
            craftingCluster.setNetworkCluster(null);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            computationCluster.setNetworkCluster(null);
        }
    }

    public static void clearAll() {
        for (LevelState state : LEVELS.values()) {
            state.craftingNetworks.values().forEach(NECraftingNetworkCluster::clear);
            state.computationNetworks.values().forEach(NEComputationNetworkCluster::clear);
            for (NECraftingCluster cluster : state.crafting) {
                cluster.setNetworkCluster(null);
            }
            for (NEComputationCluster cluster : state.computation) {
                cluster.setNetworkCluster(null);
            }
        }
        LEVELS.clear();
    }

    private static void detach(NECluster<?> cluster) {
        Level level = getLevel(cluster);
        if (!(level instanceof ServerLevel serverLevel)) {
            clearAssociation(cluster);
            return;
        }
        LevelState state = LEVELS.get(serverLevel);
        if (state == null) {
            clearAssociation(cluster);
            return;
        }
        state.pendingGridRefresh.remove(cluster);
        if (cluster instanceof NECraftingCluster craftingCluster) {
            if (state.crafting.remove(craftingCluster)) {
                rebuildCrafting(serverLevel, state);
            }
            craftingCluster.setNetworkCluster(null);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            if (state.computation.remove(computationCluster)) {
                rebuildComputation(serverLevel, state);
            }
            computationCluster.setNetworkCluster(null);
        }
        if (state.crafting.isEmpty() && state.computation.isEmpty()) {
            LEVELS.remove(serverLevel);
        }
    }

    private static void rebuildCrafting(ServerLevel level, LevelState state) {
        state.crafting.removeIf(cluster -> {
            var controller = cluster.getController();
            boolean stale = cluster.isDestroyed() || controller == null || controller.getCluster() != cluster;
            if (stale) {
                cluster.setNetworkCluster(null);
            }
            return stale;
        });
        if (state.crafting.isEmpty()) {
            state.craftingNetworks.values().forEach(NECraftingNetworkCluster::clear);
            state.craftingNetworks.clear();
            return;
        }

        Map<NetworkGroupKey, List<NECraftingCluster>> groups = new HashMap<>();
        for (NECraftingCluster cluster : state.crafting) {
            cluster.setNetworkCluster(null);
            groups.computeIfAbsent(craftingNetworkKey(cluster), ignored -> new ArrayList<>())
                    .add(cluster);
        }

        Set<CraftingNetworkPartitionKey> activeKeys = new HashSet<>();
        for (Map.Entry<NetworkGroupKey, List<NECraftingCluster>> entry : groups.entrySet()) {
            List<NECraftingCluster> clusters = entry.getValue();
            for (int start = 0; start < clusters.size(); start += CRAFTING_NETWORK_HOST_LIMIT) {
                int partition = start / CRAFTING_NETWORK_HOST_LIMIT;
                CraftingNetworkPartitionKey key = new CraftingNetworkPartitionKey(entry.getKey(), partition);
                activeKeys.add(key);
                NECraftingNetworkCluster network =
                        state.craftingNetworks.computeIfAbsent(key, ignored -> new NECraftingNetworkCluster());
                List<NECraftingCluster> members =
                        clusters.subList(start, Math.min(start + CRAFTING_NETWORK_HOST_LIMIT, clusters.size()));
                for (NECraftingCluster member : members) {
                    member.setNetworkCluster(network);
                }
                network.configure(members);
            }
        }
        state.craftingNetworks.entrySet().removeIf(entry -> {
            if (activeKeys.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().clear();
            return true;
        });
    }

    private static void rebuildComputation(ServerLevel level, LevelState state) {
        state.computation.removeIf(cluster -> {
            var controller = cluster.getController();
            boolean stale = cluster.isDestroyed() || controller == null || controller.getCluster() != cluster;
            if (stale) {
                cluster.setNetworkCluster(null);
            }
            return stale;
        });
        if (state.computation.isEmpty()) {
            state.computationNetworks.values().forEach(NEComputationNetworkCluster::clear);
            state.computationNetworks.clear();
            return;
        }

        Map<NetworkGroupKey, List<NEComputationCluster>> groups = new HashMap<>();
        for (NEComputationCluster cluster : state.computation) {
            cluster.setNetworkCluster(null);
            groups.computeIfAbsent(computationNetworkKey(cluster), ignored -> new ArrayList<>())
                    .add(cluster);
        }

        Set<NetworkGroupKey> activeKeys = new HashSet<>();
        for (Map.Entry<NetworkGroupKey, List<NEComputationCluster>> entry : groups.entrySet()) {
            activeKeys.add(entry.getKey());
            NEComputationNetworkCluster network = state.computationNetworks.computeIfAbsent(
                    entry.getKey(), ignored -> new NEComputationNetworkCluster());
            for (NEComputationCluster cluster : entry.getValue()) {
                cluster.setNetworkCluster(network);
            }
            network.configure(entry.getValue());
        }
        state.computationNetworks.entrySet().removeIf(entry -> {
            if (activeKeys.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().clear();
            return true;
        });
    }

    private static void assignCraftingFrequencyIfNeeded(NECraftingCluster cluster, Set<NECraftingCluster> clusters) {
        var controller = cluster.getController();
        if (controller == null || controller.hasNetworkFrequency()) {
            return;
        }
        Object network = networkObject(cluster);
        if (network == null) {
            return;
        }
        List<Integer> assignedFrequencies = new ArrayList<>();
        for (NECraftingCluster other : clusters) {
            if (other == cluster || networkObject(other) != network) {
                continue;
            }
            var otherController = other.getController();
            if (otherController != null && otherController.hasNetworkFrequency()) {
                assignedFrequencies.add(otherController.getNetworkFrequency());
            }
        }
        controller.assignNetworkFrequency(NEFrequencyAllocator.allocate(assignedFrequencies));
    }

    private static void assignComputationFrequencyIfNeeded(
            NEComputationCluster cluster, Set<NEComputationCluster> clusters) {
        var controller = cluster.getController();
        if (controller == null || controller.hasNetworkFrequency()) {
            return;
        }
        Object network = networkObject(cluster);
        if (network == null) {
            return;
        }
        List<Integer> assignedFrequencies = new ArrayList<>();
        for (NEComputationCluster other : clusters) {
            if (other == cluster || networkObject(other) != network) {
                continue;
            }
            var otherController = other.getController();
            if (otherController != null && otherController.hasNetworkFrequency()) {
                assignedFrequencies.add(otherController.getNetworkFrequency());
            }
        }
        controller.assignNetworkFrequency(NEFrequencyAllocator.allocate(assignedFrequencies));
    }

    private static NetworkGroupKey craftingNetworkKey(NECraftingCluster cluster) {
        var controller = cluster.getController();
        return networkKey(cluster, controller == null ? 0 : controller.getNetworkFrequency());
    }

    private static NetworkGroupKey computationNetworkKey(NEComputationCluster cluster) {
        var controller = cluster.getController();
        return networkKey(cluster, controller == null ? 0 : controller.getNetworkFrequency());
    }

    private static NetworkGroupKey networkKey(NECluster<?> cluster, int frequency) {
        Object network = networkObject(cluster);
        return new NetworkGroupKey(network == null ? cluster : network, frequency);
    }

    @Nullable private static Object networkObject(NECluster<?> cluster) {
        if (cluster instanceof NECraftingCluster craftingCluster) {
            var controller = craftingCluster.getController();
            return controller == null ? null : safeGrid(controller.getMainNode());
        }
        if (cluster instanceof NEComputationCluster computationCluster) {
            var controller = computationCluster.getController();
            return controller == null ? null : safeGrid(controller.getMainNode());
        }
        return null;
    }

    @Nullable private static IGrid safeGrid(@Nullable IManagedGridNode node) {
        if (node == null) {
            return null;
        }
        try {
            if (!node.isOnline()) {
                return null;
            }
            return node.getGrid();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable private static Level getLevel(NECluster<?> cluster) {
        if (cluster instanceof NECraftingCluster craftingCluster && craftingCluster.getController() != null) {
            return craftingCluster.getController().getLevel();
        }
        if (cluster instanceof NEComputationCluster computationCluster && computationCluster.getController() != null) {
            return computationCluster.getController().getLevel();
        }
        return null;
    }

    private static final class CraftingNetworkPartitionKey {
        private final NetworkGroupKey group;
        private final int partition;

        private CraftingNetworkPartitionKey(NetworkGroupKey group, int partition) {
            this.group = group;
            this.partition = partition;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CraftingNetworkPartitionKey key
                    && group.equals(key.group)
                    && partition == key.partition;
        }

        @Override
        public int hashCode() {
            return 31 * group.hashCode() + partition;
        }
    }

    private static final class NetworkGroupKey {
        private final Object network;
        private final int frequency;

        private NetworkGroupKey(Object network, int frequency) {
            this.network = network;
            this.frequency = frequency;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NetworkGroupKey key && network == key.network && frequency == key.frequency;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(network) + frequency;
        }
    }

    private static final class LevelState {
        private final Set<NECraftingCluster> crafting = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<NEComputationCluster> computation = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<CraftingNetworkPartitionKey, NECraftingNetworkCluster> craftingNetworks = new HashMap<>();
        private final Map<NetworkGroupKey, NEComputationNetworkCluster> computationNetworks = new HashMap<>();
        private final Set<NECluster<?>> pendingGridRefresh = Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
