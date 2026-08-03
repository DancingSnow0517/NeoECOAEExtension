package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellStorageManager;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {}

    /** Minecraft loads ordinary and infinite SavedData before cells can be mounted. */
    public static void onServerStarted(ServerStartedEvent event) {
        NELogicalNetworkManager.onServerStarted();
        ECOCellStorageManager.onServerStarted(event.getServer());
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ECOCellStorageManager.awaitPreviousTick();
            ECOInfiniteStorageDomains.awaitPreviousTick();
        } else if (event.phase == TickEvent.Phase.END) {
            ECOCellStorageManager.tick();
            ECOInfiniteStorageDomains.flushTick();
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        try {
            NELogicalNetworkManager.onServerStopping();
            NELogicalNetworkManager.clearAll();
            ECOCellStorageManager.closeAll();
        } finally {
            try {
                ECOInfiniteStorageDomains.closeAll();
            } finally {
                ECOStorageCells.clearRuntimeState();
            }
        }
    }

    public static void onLevelSave(LevelEvent.Save event) {
        ECOCellStorageManager.flushBudgeted(0L);
        ECOInfiniteStorageDomains.flushAll();
    }
}
