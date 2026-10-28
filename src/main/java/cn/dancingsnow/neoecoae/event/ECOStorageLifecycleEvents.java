package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.PatternBusUpdateScheduler;
import cn.dancingsnow.neoecoae.crafting.planner.growth.NetGrowthPatternValidationRegistry;
import cn.dancingsnow.neoecoae.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {
    }

    /**
     * Release integrated-server state. World data, including infinite domains, was already saved by vanilla shutdown.
     */
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        ECOCellMutationBatch.clearThreadState();
        ECOCellMutationBatch.drainRetries();
        try { cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository.flush(event.getServer()); }
        catch (RuntimeException failure) { org.slf4j.LoggerFactory.getLogger(ECOStorageLifecycleEvents.class)
            .error("ECO shutdown checkpoint failed; vanilla world save will retry dirty records", failure); }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        MinecraftServer server = event.getServer();
        PatternBusUpdateScheduler.clear(server);
        NELogicalNetworkManager.clearAll();
        ECOPlanningResultRegistry.clear();
        NetGrowthPatternValidationRegistry.clear();
        ECOCellMutationBatch.clearThreadState();
        ECOCellMutationBatch.drainRetries();
        ECOStorageCells.clearRuntimeState();
        ECOInfiniteStorageDomains.onServerStopped(server);
        cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository.stopped(server);
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NELogicalNetworkManager.clear(level);
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            NELogicalNetworkManager.tick(level);
        }
        ECOCellMutationBatch.retry();
        cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository.tick(event.getServer(), event.getServer().getTickCount());
        PatternBusUpdateScheduler.tick(event.getServer());
        if (!FMLEnvironment.production) {
            ECOCellMutationBatch.assertClean();
        }
    }
}
