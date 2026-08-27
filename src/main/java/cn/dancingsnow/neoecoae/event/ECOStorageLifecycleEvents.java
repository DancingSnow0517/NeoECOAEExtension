package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {
    }

    /**
     * Infinite storage domains are saved with the world, so there is nothing to write here. Only the engine cache of
     * the stopped server is dropped, which matters for the integrated server where the process outlives the world.
     */
    public static void onServerStopped(ServerStoppedEvent event) {
        ECOInfiniteStorageDomains.onServerStopped(event.getServer());
    }
}
