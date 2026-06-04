package cn.dancingsnow.neoecoae.compat.ae2;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
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

        @SubscribeEvent
        public static void onAddReloadListener(AddReloadListenerEvent event) {
            event.addListener(new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                    return null;
                }

                @Override
                protected void apply(Void prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
                    clearFastPathState();
                }
            });
        }

        private static void clearFastPathState() {
            AE2PatternIntrospection.onRecipeReloadOrServerReload();
        }
    }
}
