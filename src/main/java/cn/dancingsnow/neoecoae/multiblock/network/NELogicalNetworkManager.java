package cn.dancingsnow.neoecoae.multiblock.network;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.util.ServerTaskUtil;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Server-thread authority for network-switch group membership. A logical group is scoped to one
 * dimension, one subsystem, one online AE2 grid and one frequency, and is capped at
 * {@link NEFrequencyAllocator#HOST_LIMIT} members - both crafting and computation groups partition
 * into chunks of that size. A switch multiplier only applies after at least two physical hosts join
 * the same logical group.
 */
public final class NELogicalNetworkManager {
    private static final Map<ServerLevel, LevelState> LEVELS = new IdentityHashMap<>();
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
            state.crafting.attach(craftingCluster);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            state.computation.attach(computationCluster);
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
            state.crafting.refresh(craftingCluster);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            state.computation.refresh(computationCluster);
        }
    }

    public static void refreshAfterGridChange(NECluster<?> cluster) {
        Level level = getLevel(cluster);
        if (!(level instanceof ServerLevel serverLevel) || cluster.isDestroyed() || !cluster.isNetworkMode()) {
            return;
        }
        LevelState state = LEVELS.computeIfAbsent(serverLevel, ignored -> new LevelState());
        state.pendingGridRefresh.add(cluster);
    }

    /** Drain after AE2's grid update, once boot, power and channel state have settled. */
    public static void tick(ServerLevel level) {
        LevelState state = LEVELS.get(level);
        if (state == null || ServerTaskUtil.isServerStopping(level) || state.pendingGridRefresh.isEmpty()) {
            return;
        }
        List<NECluster<?>> pending = new ArrayList<>(state.pendingGridRefresh);
        state.pendingGridRefresh.clear();
        for (NECluster<?> cluster : pending) {
            if (!cluster.isDestroyed() && getLevel(cluster) == level) {
                refresh(cluster);
            }
        }
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
        for (ServerLevel level : new ArrayList<>(LEVELS.keySet())) {
            clear(level);
        }
    }

    public static void clear(ServerLevel level) {
        LevelState state = LEVELS.remove(level);
        if (state == null) {
            return;
        }
        state.crafting.clear();
        state.computation.clear();
        state.pendingGridRefresh.clear();
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
            state.crafting.detach(craftingCluster);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            state.computation.detach(computationCluster);
        }
        if (state.crafting.isEmpty() && state.computation.isEmpty()) {
            LEVELS.remove(serverLevel);
        }
    }

    private static @Nullable Level getLevel(NECluster<?> cluster) {
        NEBlockEntity<?, ?> controller = controllerOf(cluster);
        return controller == null ? null : controller.getLevel();
    }

    private static @Nullable NEBlockEntity<?, ?> controllerOf(NECluster<?> cluster) {
        if (cluster instanceof NECraftingCluster craftingCluster) {
            return craftingCluster.getController();
        }
        if (cluster instanceof NEComputationCluster computationCluster) {
            return computationCluster.getController();
        }
        return null;
    }

    /**
     * Membership and shared groups of one network-switch domain (crafting or computation). The member
     * set is a {@link HashSet} on purpose: group construction iterates it, so its order decides which
     * hosts share a partition.
     */
    private static final class Domain<C extends NECluster<C>, H extends NEBlockEntity<C, ?> & NENetworkSwitchHost, N> {
        private final Set<C> members = new HashSet<>();
        private final Map<NetworkPartitionKey, N> networks = new HashMap<>();
        private final Supplier<N> newNetwork;
        private final BiConsumer<C, N> bindNetwork;
        private final BiConsumer<N, List<C>> configure;
        private final Function<C, H> controllerLookup;

        private Domain(
            Supplier<N> newNetwork,
            BiConsumer<C, N> bindNetwork,
            BiConsumer<N, List<C>> configure,
            Function<C, H> controllerLookup
        ) {
            this.newNetwork = newNetwork;
            this.bindNetwork = bindNetwork;
            this.configure = configure;
            this.controllerLookup = controllerLookup;
        }

        boolean isEmpty() {
            return members.isEmpty();
        }

        void attach(C cluster) {
            if (members.add(cluster)) {
                assignFrequencyIfNeeded(cluster);
                rebuild();
                refreshAfterGridChange(cluster);
            }
        }

        void refresh(C cluster) {
            members.add(cluster);
            assignFrequencyIfNeeded(cluster);
            rebuild();
        }

        void detach(C cluster) {
            if (members.remove(cluster)) {
                rebuild();
            }
            bindNetwork.accept(cluster, null);
        }

        void clear() {
            for (C cluster : members) {
                bindNetwork.accept(cluster, null);
            }
            networks.clear();
            members.clear();
        }

        private void rebuild() {
            members.removeIf(cluster -> {
                var controller = controllerLookup.apply(cluster);
                boolean stale = cluster.isDestroyed() || controller == null || controller.getCluster() != cluster;
                if (stale) {
                    bindNetwork.accept(cluster, null);
                }
                return stale;
            });
            if (members.isEmpty()) {
                networks.clear();
                return;
            }

            Map<NetworkGroupKey, List<C>> groups = new HashMap<>();
            for (C cluster : members) {
                bindNetwork.accept(cluster, null);
                groups.computeIfAbsent(networkKey(cluster), ignored -> new ArrayList<>()).add(cluster);
            }

            Set<NetworkPartitionKey> activeKeys = new HashSet<>();
            for (Map.Entry<NetworkGroupKey, List<C>> entry : groups.entrySet()) {
                List<C> clusters = entry.getValue();
                for (int start = 0; start < clusters.size(); start += NETWORK_HOST_LIMIT) {
                    int partition = start / NETWORK_HOST_LIMIT;
                    NetworkPartitionKey key = new NetworkPartitionKey(entry.getKey(), partition);
                    activeKeys.add(key);
                    List<C> partitionMembers = clusters.subList(
                        start,
                        Math.min(start + NETWORK_HOST_LIMIT, clusters.size())
                    );
                    if (partitionMembers.size() < 2) {
                        // Solo member: no multiplier applies, so no network cluster is needed.
                        continue;
                    }
                    N network = networks.computeIfAbsent(key, ignored -> newNetwork.get());
                    for (C cluster : partitionMembers) {
                        bindNetwork.accept(cluster, network);
                    }
                    configure.accept(network, partitionMembers);
                }
            }
            networks.keySet().retainAll(activeKeys);
        }

        private void assignFrequencyIfNeeded(C cluster) {
            H controller = controllerLookup.apply(cluster);
            if (controller == null || controller.hasNetworkFrequency()) {
                return;
            }
            Object network = networkObject(cluster);
            if (network == null) {
                return;
            }
            IntArrayList assignedFrequencies = new IntArrayList();
            for (C other : members) {
                if (other == cluster || networkObject(other) != network) {
                    continue;
                }
                H otherController = controllerLookup.apply(other);
                if (otherController != null && otherController.hasNetworkFrequency()) {
                    assignedFrequencies.add(otherController.getNetworkFrequency());
                }
            }
            controller.assignNetworkFrequency(NEFrequencyAllocator.allocate(assignedFrequencies));
        }

        private NetworkGroupKey networkKey(C cluster) {
            H controller = controllerLookup.apply(cluster);
            int frequency = controller == null ? 0 : controller.getNetworkFrequency();
            Object network = networkObject(cluster);
            return new NetworkGroupKey(network == null ? cluster : network, frequency);
        }

        private @Nullable Object networkObject(C cluster) {
            H controller = controllerLookup.apply(cluster);
            if (controller != null && controller.getMainNode().isOnline() && controller.getMainNode().getGrid() != null) {
                return controller.getMainNode().getGrid();
            }
            return null;
        }
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
        private final Domain<NECraftingCluster, ECOCraftingSystemBlockEntity, NECraftingNetworkCluster> crafting =
            new Domain<>(
                NECraftingNetworkCluster::new,
                NECraftingCluster::setNetworkCluster,
                NECraftingNetworkCluster::configure,
                NECraftingCluster::getController
            );
        private final Domain<NEComputationCluster, ECOComputationSystemBlockEntity, NEComputationNetworkCluster> computation =
            new Domain<>(
                NEComputationNetworkCluster::new,
                NEComputationCluster::setNetworkCluster,
                NEComputationNetworkCluster::configure,
                NEComputationCluster::getController
            );
        private final Set<NECluster<?>> pendingGridRefresh = Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
