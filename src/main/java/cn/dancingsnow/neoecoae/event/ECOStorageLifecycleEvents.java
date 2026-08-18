package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathWarmupService;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ECOFastPathWarmupService.onServerStopping(event.getServer());
        ECOPlanningFailureDiagnostics.close();
        NELogicalNetworkManager.clearAll();
        ECOInfiniteStorageDomains.closeAll();
        ECOStorageCells.clearRuntimeState();
    }
}
