package cn.dancingsnow.neoecoae.forge.event;

import cn.dancingsnow.neoecoae.impl.storage.ECOCellStorageManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

public final class ECOStorageLifecycleEvents {
    private ECOStorageLifecycleEvents() {}

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ECOCellStorageManager.tick();
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ECOCellStorageManager.closeAll();
    }

    public static void onLevelSave(LevelEvent.Save event) {
        ECOCellStorageManager.flushBudgeted(0L);
    }
}
