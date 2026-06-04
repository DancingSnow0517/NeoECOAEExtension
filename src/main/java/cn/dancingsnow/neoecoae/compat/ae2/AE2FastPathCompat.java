package cn.dancingsnow.neoecoae.compat.ae2;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingFastPathCache;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class AE2FastPathCompat {
    private AE2FastPathCompat() {}

    @Mod.EventBusSubscriber(modid = NeoECOAE.MOD_ID)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            clearFastPathState();
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            clearFastPathState();
        }

        private static void clearFastPathState() {
            // TODO: Also hook datapack recipe reload directly if this port adds a
            // dedicated reload listener. The generation bump prevents stale hits
            // across server lifecycle changes.
            AE2PatternIntrospection.onRecipeReloadOrServerReload();
            ECOCraftingFastPathCache.clearAllCaches();
        }
    }
}
