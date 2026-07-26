package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ECOInfiniteStorageDomains.closeAll();
    }

    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level && Level.OVERWORLD.equals(level.dimension())) {
            ECOInfiniteStorageDomains.flushWalForWorldSave();
        }
    }

    public static void onServerTickPre(ServerTickEvent.Pre event) {
        ECOInfiniteStorageDomains.awaitPreviousTick();
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        ECOInfiniteStorageDomains.flushTick();
    }
}
