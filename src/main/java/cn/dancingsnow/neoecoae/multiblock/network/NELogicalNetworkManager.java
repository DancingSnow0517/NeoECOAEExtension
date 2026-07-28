package cn.dancingsnow.neoecoae.multiblock.network;

import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server-thread authority for exchange-mode membership. A logical cluster is
 * scoped to one dimension and one subsystem; AE2 grid membership is never
 * merged here.
 */
public final class NELogicalNetworkManager {
    private static final Map<ServerLevel, LevelState> LEVELS = new WeakHashMap<>();

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
        attach(cluster);
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
            if (state.craftingNetwork != null) {
                state.craftingNetwork.clear();
            }
            if (state.computationNetwork != null) {
                state.computationNetwork.clear();
            }
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
        NECraftingNetworkCluster network = state.craftingNetwork;
        if (state.crafting.isEmpty()) {
            if (network != null) {
                network.clear();
            }
            state.craftingNetwork = null;
            return;
        }
        if (network == null) {
            network = new NECraftingNetworkCluster(level);
            state.craftingNetwork = network;
        }
        for (NECraftingCluster cluster : state.crafting) {
            cluster.setNetworkCluster(network);
        }
        network.configure(state.crafting);
    }

    private static void rebuildComputation(ServerLevel level, LevelState state) {
        NEComputationNetworkCluster network = state.computationNetwork;
        if (state.computation.isEmpty()) {
            if (network != null) {
                network.clear();
            }
            state.computationNetwork = null;
            return;
        }
        if (network == null) {
            network = new NEComputationNetworkCluster(level);
            state.computationNetwork = network;
        }
        for (NEComputationCluster cluster : state.computation) {
            cluster.setNetworkCluster(network);
        }
        network.configure(state.computation);
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
        private NECraftingNetworkCluster craftingNetwork;
        private NEComputationNetworkCluster computationNetwork;
    }
}
