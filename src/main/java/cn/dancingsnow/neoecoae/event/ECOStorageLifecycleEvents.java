package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ECOInfiniteStorageDomains.closeAll();
    }

    public static void onLevelSave(LevelEvent.Save event) {
        ECOInfiniteStorageDomains.flushAll();
    }
}
