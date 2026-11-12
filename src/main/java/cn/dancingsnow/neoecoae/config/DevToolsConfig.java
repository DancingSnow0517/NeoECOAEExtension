package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Registration remains stable; this local setting controls visibility and server-side execution. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public final class DevToolsConfig {
    private static final boolean DEFAULT = !FMLEnvironment.isProduction() && Boolean.getBoolean("neoecoae.devGenerator");
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    static { BUILDER.translation("neoecoae.configuration.devTools").push("devTools"); }
    private static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Show the development generator and enable its commands and resource/FE workloads.",
            "Default off in normal installations; runClient defaults on. Restart the client after changing visibility.",
            "Commands and workloads update immediately. Commands require cheats enabled or operator permission level 2.",
            "The server must also enable this setting to execute workloads in multiplayer.",
            "显示开发发生器并启用命令及资源/FE压测。普通安装默认关闭，runClient默认开启。修改显示后重启客户端；联机运行还需要服务端开启。",
            "命令及压测开关即时生效；命令需要允许作弊或2级OP权限。")
        .translation("neoecoae.configuration.devTools.enabled")
        .define("enabled", DEFAULT);
    static { BUILDER.pop(); }
    public static final ModConfigSpec SPEC = BUILDER.build();
    private static volatile boolean enabled = DEFAULT;

    private DevToolsConfig() {}
    public static boolean enabled() { return enabled; }

    @SubscribeEvent public static void loading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) updateEnabled();
    }
    @SubscribeEvent public static void reloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) updateEnabled();
    }
    @SubscribeEvent public static void unloading(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == SPEC) enabled = false;
    }

    private static void updateEnabled() {
        boolean next = ENABLED.get();
        if (enabled == next) return;
        enabled = next;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Reloads can originate from the config watcher, outside the server thread.
            // The client retains its filtered command tree until the server sends it again.
            server.execute(() -> {
                for (var player : server.getPlayerList().getPlayers()) {
                    server.getCommands().sendCommands(player);
                }
            });
        }
    }
}
