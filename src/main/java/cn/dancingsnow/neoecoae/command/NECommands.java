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
            .then(literal("clear_fx_workers").executes(context -> clearFxWorkers(context.getSource())))
            .then(literal("storage_report").executes(context -> storageReport(context.getSource(), null))
                .then(net.minecraft.commands.Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                    .executes(context -> storageReport(context.getSource(),
                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(context, "pos"))))));
    }

    private static int storageReport(net.minecraft.commands.CommandSourceStack source, net.minecraft.core.BlockPos pos) {
        try {
            var report = cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains.diagnosticReport(source.getServer());
            if (pos != null && source.getLevel().getBlockEntity(pos)
                instanceof cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity controller) {
                report.addProperty("controller", pos.toShortString());
                report.addProperty("averageNanos", controller.getPerformanceAverageNanos());
                report.addProperty("p95Nanos", controller.getPerformanceP95Nanos());
                report.addProperty("maxNanos", controller.getPerformanceMaxNanos());
                report.addProperty("details", controller.storageDiagnosticText());
                report.add("controllerFailures", new com.google.gson.Gson().toJsonTree(controller.storageFailures()));
            }
            var directory = source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("eco-storage-reports");
            java.nio.file.Files.createDirectories(directory);
            var file = directory.resolve("storage-" + UUID.randomUUID() + ".json");
            java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report),
                java.nio.file.StandardOpenOption.CREATE_NEW);
            source.sendSuccess(() -> Component.literal("ECO storage report: " + file.toAbsolutePath()), false);
            return 1;
        } catch (java.io.IOException | RuntimeException e) {
            source.sendFailure(Component.literal("Could not write storage report: " + e));
            return 0;
        }
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
