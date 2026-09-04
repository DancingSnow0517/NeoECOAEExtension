package cn.dancingsnow.neoecoae.command;

import static net.minecraft.commands.Commands.literal;

import appeng.api.networking.IGrid;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class NECommands {
    private NECommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("neoecoae")
            .requires(source -> source.hasPermission(2))
            .then(literal("clear_fx_workers").executes(context -> clearFxWorkers(context.getSource()))));
    }

    private static int clearFxWorkers(net.minecraft.commands.CommandSourceStack source) {
        Set<IGrid> affectedGrids = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<UUID> affectedJobs = new HashSet<>();
        int clearedWorkers = 0;
        int clearedThreads = 0;

        for (ECOCraftingWorkerBlockEntity worker : ECOCraftingWorkerBlockEntity.getLoadedServerWorkers()) {
            var result = worker.discardAllCraftingContents();
            if (result.threadCount() <= 0) {
                continue;
            }
            clearedWorkers++;
            clearedThreads += result.threadCount();
            affectedJobs.addAll(result.jobIds());
            IGrid grid = worker.getMainNode().getGrid();
            if (grid != null) {
                affectedGrids.add(grid);
            }
        }

        int cancelledJobs = cancelAffectedJobs(affectedGrids, affectedJobs);
        int finalClearedWorkers = clearedWorkers;
        int finalClearedThreads = clearedThreads;
        int finalCancelledJobs = cancelledJobs;
        source.sendSuccess(() -> Component.literal(
            "Cleared " + finalClearedThreads + " FX worker thread(s) in " + finalClearedWorkers
                + " loaded core(s); cancelled " + finalCancelledJobs + " crafting job(s). No items were dropped."
        ), true);
        return clearedThreads;
    }

    private static int cancelAffectedJobs(Set<IGrid> grids, Set<UUID> affectedJobs) {
        if (affectedJobs.isEmpty()) {
            return 0;
        }
        Set<NEComputationCluster> clusters = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (IGrid grid : grids) {
            for (ECOComputationSystemBlockEntity host : grid.getMachines(ECOComputationSystemBlockEntity.class)) {
                if (host.getCluster() != null) {
                    clusters.add(host.getCluster());
                }
            }
        }

        int cancelled = 0;
        for (NEComputationCluster cluster : clusters) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                for (UUID jobId : affectedJobs) {
                    if (cpu.getLogic().hasCraftingJob(jobId)) {
                        cpu.getLogic().cancel();
                        cancelled++;
                        break;
                    }
                }
            }
        }
        return cancelled;
    }
}
