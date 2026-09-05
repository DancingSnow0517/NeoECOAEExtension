package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {
    }

    /**
     * Drain pending journals and checkpoints before releasing integrated-server state.
     */
    public static void onServerStopped(ServerStoppedEvent event) {
        ECOInfiniteStorageDomains.onServerStopped(event.getServer());
        cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageTickBudget.clear(event.getServer());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        ECOInfiniteStorageDomains.tick(event.getServer(), event.getServer().getTickCount());
        cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch.retry();
    }
}
