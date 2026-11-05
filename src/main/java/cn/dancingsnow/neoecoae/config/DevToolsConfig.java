package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Registration remains stable; this local setting controls visibility and server-side execution. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public final class DevToolsConfig {
    private static final boolean DEFAULT = !FMLEnvironment.production && Boolean.getBoolean("neoecoae.devGenerator");
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    static { BUILDER.translation("neoecoae.configuration.devTools").push("devTools"); }
    private static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Show the development generator and enable its commands and resource/FE workloads.",
            "Default off in normal installations; runClient defaults on. Restart the client after changing visibility.",
            "The server must also enable this setting to execute workloads in multiplayer.",
            "显示开发发生器并启用命令及资源/FE压测。普通安装默认关闭，runClient默认开启。修改显示后重启客户端；联机运行还需要服务端开启。")
        .translation("neoecoae.configuration.devTools.enabled")
        .worldRestart()
        .define("enabled", DEFAULT);
    static { BUILDER.pop(); }
    public static final ModConfigSpec SPEC = BUILDER.build();
    private static volatile boolean enabled = DEFAULT;

    private DevToolsConfig() {}
    public static boolean enabled() { return enabled; }

    @SubscribeEvent public static void loading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) enabled = ENABLED.get();
    }
    @SubscribeEvent public static void reloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) enabled = ENABLED.get();
    }
    @SubscribeEvent public static void unloading(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == SPEC) enabled = false;
    }
}
