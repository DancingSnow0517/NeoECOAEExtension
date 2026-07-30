package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellStorageManager;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {}

    /** Minecraft loads ordinary and infinite SavedData before cells can be mounted. */
    public static void onServerStarted(ServerStartedEvent event) {
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
