package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        NELogicalNetworkManager.clearAll();
        ECOInfiniteStorageDomains.closeAll();
    }
}
