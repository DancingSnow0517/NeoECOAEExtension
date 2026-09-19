package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellStorageManager;
import cn.dancingsnow.neoecoae.impl.storage.ECOSavedDataPersistence;
import cn.dancingsnow.neoecoae.impl.storage.StorageTransferJournal;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import java.io.IOException;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {}

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            StorageTransferJournal.recoverAll(event.getServer().getWorldPath(LevelResource.ROOT));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot finish pending storage transfer; preserve the world and repair disk access before restarting",
                    e);
        }
    }

    /** Minecraft loads ordinary and infinite SavedData before cells can be mounted. */
    public static void onServerStarted(ServerStartedEvent event) {
        NELogicalNetworkManager.onServerStarted();
        ECOCellStorageManager.onServerStarted(event.getServer());
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
                try {
                    ECOStorageCells.clearRuntimeState();
                } finally {
                    ECOSavedDataPersistence.clear();
                }
            }
        }
    }
}
