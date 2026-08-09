package cn.dancingsnow.neoecoae.multiblock.network;

import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import java.util.ArrayList;
import java.util.Collections;
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
 * Server-thread authority for exchange-mode membership. A logical cluster is
 * scoped to one dimension, one subsystem and one AE2 grid. A switch multiplier
 * only applies after at least two physical hosts join the same logical group.
 */
public final class NELogicalNetworkManager {
    private static final Map<ServerLevel, LevelState> LEVELS = new WeakHashMap<>();

    private NELogicalNetworkManager() {}

    // Retained for the 1.20.1 lifecycle hooks. Logical groups are created lazily.
    public static void onServerStarted() {}

    public static void onServerStopping() {
        clearAll();
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
                rebuildCrafting(serverLevel, state);
            }
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            if (state.computation.add(computationCluster)) {
                rebuildComputation(serverLevel, state);
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
            rebuildCrafting(serverLevel, state);
        } else if (cluster instanceof NEComputationCluster computationCluster) {
            state.computation.add(computationCluster);
            rebuildComputation(serverLevel, state);
        }
    }

    public static void refreshAfterGridChange(NECluster<?> cluster) {
        refresh(cluster);
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
        for (Map.Entry<ServerLevel, LevelState> entry : LEVELS.entrySet()) {
            LevelState state = entry.getValue();
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

        Map<Object, List<NECraftingCluster>> groups = new IdentityHashMap<>();
        for (NECraftingCluster cluster : state.crafting) {
            cluster.setNetworkCluster(null);
            groups.computeIfAbsent(craftingNetworkKey(cluster), ignored -> new ArrayList<>())
                    .add(cluster);
        }

        Set<Object> activeKeys = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<Object, List<NECraftingCluster>> entry : groups.entrySet()) {
            activeKeys.add(entry.getKey());
            NECraftingNetworkCluster network =
                    state.craftingNetworks.computeIfAbsent(entry.getKey(), ignored -> new NECraftingNetworkCluster());
            for (NECraftingCluster cluster : entry.getValue()) {
                cluster.setNetworkCluster(network);
            }
            network.configure(entry.getValue());
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

        Map<Object, List<NEComputationCluster>> groups = new IdentityHashMap<>();
        for (NEComputationCluster cluster : state.computation) {
            cluster.setNetworkCluster(null);
            groups.computeIfAbsent(computationNetworkKey(cluster), ignored -> new ArrayList<>())
                    .add(cluster);
        }

        Set<Object> activeKeys = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<Object, List<NEComputationCluster>> entry : groups.entrySet()) {
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

    private static Object craftingNetworkKey(NECraftingCluster cluster) {
        var controller = cluster.getController();
        if (controller != null
                && controller.getMainNode().isOnline()
                && controller.getMainNode().getGrid() != null) {
            return controller.getMainNode().getGrid();
        }
        return cluster;
    }

    private static Object computationNetworkKey(NEComputationCluster cluster) {
        var controller = cluster.getController();
        if (controller != null
                && controller.getMainNode().isOnline()
                && controller.getMainNode().getGrid() != null) {
            return controller.getMainNode().getGrid();
        }
        return cluster;
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

    private static final class LevelState {
        private final Set<NECraftingCluster> crafting = new HashSet<>();
        private final Set<NEComputationCluster> computation = new HashSet<>();
        private final Map<Object, NECraftingNetworkCluster> craftingNetworks = new IdentityHashMap<>();
        private final Map<Object, NEComputationNetworkCluster> computationNetworks = new IdentityHashMap<>();
    }
}
