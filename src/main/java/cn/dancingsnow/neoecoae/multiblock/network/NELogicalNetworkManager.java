package cn.dancingsnow.neoecoae.multiblock.network;

import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server-thread authority for network-switch group membership. A logical group is scoped to one
 * dimension, one subsystem, one online AE2 grid and one frequency, and is capped at
 * {@link NEFrequencyAllocator#HOST_LIMIT} members - both crafting and computation groups partition
 * into chunks of that size. A switch multiplier only applies after at least two physical hosts join
 * the same logical group.
 */
public final class NELogicalNetworkManager {
    private static final Map<ServerLevel, LevelState> LEVELS = new WeakHashMap<>();
    private static final int NETWORK_HOST_LIMIT = NEFrequencyAllocator.HOST_LIMIT;

    private NELogicalNetworkManager() {
    }

    public static void attach(NECluster<?> cluster) {
        if (!(cluster instanceof NECraftingCluster || cluster instanceof NEComputationCluster)) {
            return;
        }
        Level level = getLevel(cluster);
        if (!(level instanceof ServerLevel serverLevel) || cluster.isDestroyed() || !cluster.isNetworkMode()) {
            detach(cluster);
            return;
        }
        LevelState state = LEVELS.computeIfAbsent(serverLevel, ignored -> new LevelState());
        if (cluster instanceof NECraftingCluster craftingCluster) {
            if (state.crafting.add(craftingCluster)) {
                assignCraftingFrequencyIfNeeded(craftingCluster, state.crafting);
                rebuildCrafting(state);
                refreshAfterGridChange(craftingCluster);
            }
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            if (state.computation.add(computationCluster)) {
                assignComputationFrequencyIfNeeded(computationCluster, state.computation);
                rebuildComputation(state);
                refreshAfterGridChange(computationCluster);
            }
        }
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
            rebuildCrafting(state);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            state.computation.add(computationCluster);
            assignComputationFrequencyIfNeeded(computationCluster, state.computation);
            rebuildComputation(state);
        }
    }

    public static void refreshAfterGridChange(NECluster<?> cluster) {
        Level level = getLevel(cluster);
        if (!(level instanceof ServerLevel serverLevel) || cluster.isDestroyed() || !cluster.isNetworkMode()) {
            return;
        }
        LevelState state = LEVELS.computeIfAbsent(serverLevel, ignored -> new LevelState());
        if (!state.pendingGridRefresh.add(cluster)) {
            return;
        }

        int refreshTick = serverLevel.getServer().getTickCount() + 1;
        serverLevel.getServer().tell(new TickTask(refreshTick, () -> {
            LevelState currentState = LEVELS.get(serverLevel);
            if (currentState != null) {
                currentState.pendingGridRefresh.remove(cluster);
            }
            if (!serverLevel.getServer().isStopped()) {
                refresh(cluster);
            }
        }));
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
        if (cluster instanceof NECraftingCluster craftingCluster) {
            if (state.crafting.remove(craftingCluster)) {
                rebuildCrafting(state);
            }
            craftingCluster.setNetworkCluster(null);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            if (state.computation.remove(computationCluster)) {
                rebuildComputation(state);
            }
            computationCluster.setNetworkCluster(null);
        }
        if (state.crafting.isEmpty() && state.computation.isEmpty()) {
            LEVELS.remove(serverLevel);
        }
    }

    private static void rebuildCrafting(LevelState state) {
        state.crafting.removeIf(cluster -> {
            var controller = cluster.getController();
            boolean stale = cluster.isDestroyed() || controller == null || controller.getCluster() != cluster;
            if (stale) {
                cluster.setNetworkCluster(null);
            }
            return stale;
        });
        if (state.crafting.isEmpty()) {
            state.craftingNetworks.clear();
            return;
        }

        Map<NetworkGroupKey, List<NECraftingCluster>> groups = new HashMap<>();
        for (NECraftingCluster cluster : state.crafting) {
            cluster.setNetworkCluster(null);
            groups.computeIfAbsent(craftingNetworkKey(cluster), ignored -> new ArrayList<>()).add(cluster);
        }

        Set<NetworkPartitionKey> activeKeys = new HashSet<>();
        for (Map.Entry<NetworkGroupKey, List<NECraftingCluster>> entry : groups.entrySet()) {
            List<NECraftingCluster> clusters = entry.getValue();
            for (int start = 0; start < clusters.size(); start += NETWORK_HOST_LIMIT) {
                int partition = start / NETWORK_HOST_LIMIT;
                NetworkPartitionKey key = new NetworkPartitionKey(entry.getKey(), partition);
                activeKeys.add(key);
                List<NECraftingCluster> members = clusters.subList(
                    start,
                    Math.min(start + NETWORK_HOST_LIMIT, clusters.size())
                );
                if (members.size() < 2) {
                    // Solo member: no multiplier applies, so no network cluster is needed.
                    continue;
                }
                NECraftingNetworkCluster network = state.craftingNetworks.computeIfAbsent(
                    key,
                    ignored -> new NECraftingNetworkCluster()
                );
                for (NECraftingCluster cluster : members) {
                    cluster.setNetworkCluster(network);
                }
                network.configure(members);
            }
        }
        state.craftingNetworks.keySet().retainAll(activeKeys);
    }

    private static void rebuildComputation(LevelState state) {
        state.computation.removeIf(cluster -> {
            var controller = cluster.getController();
            boolean stale = cluster.isDestroyed() || controller == null || controller.getCluster() != cluster;
            if (stale) {
                cluster.setNetworkCluster(null);
            }
            return stale;
        });
        if (state.computation.isEmpty()) {
            state.computationNetworks.clear();
            return;
        }

        Map<NetworkGroupKey, List<NEComputationCluster>> groups = new HashMap<>();
        for (NEComputationCluster cluster : state.computation) {
            cluster.setNetworkCluster(null);
            groups.computeIfAbsent(computationNetworkKey(cluster), ignored -> new ArrayList<>()).add(cluster);
        }

        Set<NetworkPartitionKey> activeKeys = new HashSet<>();
        for (Map.Entry<NetworkGroupKey, List<NEComputationCluster>> entry : groups.entrySet()) {
            List<NEComputationCluster> clusters = entry.getValue();
            for (int start = 0; start < clusters.size(); start += NETWORK_HOST_LIMIT) {
                int partition = start / NETWORK_HOST_LIMIT;
                NetworkPartitionKey key = new NetworkPartitionKey(entry.getKey(), partition);
                activeKeys.add(key);
                List<NEComputationCluster> members = clusters.subList(
                    start,
                    Math.min(start + NETWORK_HOST_LIMIT, clusters.size())
                );
                if (members.size() < 2) {
                    // Solo member: no multiplier applies, so no network cluster is needed.
                    continue;
                }
                NEComputationNetworkCluster network = state.computationNetworks.computeIfAbsent(
                    key,
                    ignored -> new NEComputationNetworkCluster()
                );
                for (NEComputationCluster cluster : members) {
                    cluster.setNetworkCluster(network);
                }
                network.configure(members);
            }
        }
        state.computationNetworks.keySet().retainAll(activeKeys);
    }

    private static void assignCraftingFrequencyIfNeeded(
        NECraftingCluster cluster,
        Set<NECraftingCluster> clusters
    ) {
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
        NEComputationCluster cluster,
        Set<NEComputationCluster> clusters
    ) {
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
        return networkKey(cluster, cluster.getController() == null ? 0 : cluster.getController().getNetworkFrequency());
    }

    private static NetworkGroupKey computationNetworkKey(NEComputationCluster cluster) {
        return networkKey(cluster, cluster.getController() == null ? 0 : cluster.getController().getNetworkFrequency());
    }

    private static NetworkGroupKey networkKey(NECluster<?> cluster, int frequency) {
        Object network = networkObject(cluster);
        return new NetworkGroupKey(network == null ? cluster : network, frequency);
    }

    private static Object networkObject(NECluster<?> cluster) {
        if (cluster instanceof NECraftingCluster craftingCluster) {
            var controller = craftingCluster.getController();
            if (controller != null && controller.getMainNode().isOnline() && controller.getMainNode().getGrid() != null) {
                return controller.getMainNode().getGrid();
            }
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            var controller = computationCluster.getController();
            if (controller != null && controller.getMainNode().isOnline() && controller.getMainNode().getGrid() != null) {
                return controller.getMainNode().getGrid();
            }
        }
        return null;
    }

    private static @Nullable Level getLevel(NECluster<?> cluster) {
        if (cluster instanceof NECraftingCluster craftingCluster && craftingCluster.getController() != null) {
            return craftingCluster.getController().getLevel();
        }
        if (cluster instanceof NEComputationCluster computationCluster && computationCluster.getController() != null) {
            return computationCluster.getController().getLevel();
        }
        return null;
    }

    private static final class NetworkPartitionKey {
        private final NetworkGroupKey group;
        private final int partition;

        private NetworkPartitionKey(NetworkGroupKey group, int partition) {
            this.group = group;
            this.partition = partition;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NetworkPartitionKey key
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
            return other instanceof NetworkGroupKey key
                && network == key.network
                && frequency == key.frequency;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(network) + frequency;
        }
    }

    private static final class LevelState {
        private final Set<NECraftingCluster> crafting = new HashSet<>();
        private final Set<NEComputationCluster> computation = new HashSet<>();
        private final Map<NetworkPartitionKey, NECraftingNetworkCluster> craftingNetworks = new HashMap<>();
        private final Map<NetworkPartitionKey, NEComputationNetworkCluster> computationNetworks = new HashMap<>();
        private final Set<NECluster<?>> pendingGridRefresh = Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
