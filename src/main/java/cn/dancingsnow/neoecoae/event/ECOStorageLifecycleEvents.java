package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.PatternBusUpdateScheduler;
import cn.dancingsnow.neoecoae.crafting.planner.growth.NetGrowthPatternValidationRegistry;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {
    }

    /**
     * Release integrated-server state. World data, including infinite domains, was already saved by vanilla shutdown.
     */
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
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NELogicalNetworkManager.clear(level);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            NELogicalNetworkManager.tick(level);
        }
        ECOCellMutationBatch.retry();
        PatternBusUpdateScheduler.tick(event.getServer());
        if (!FMLEnvironment.production) {
            ECOCellMutationBatch.assertClean();
        }
    }
}
