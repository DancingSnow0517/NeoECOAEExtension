package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;

/** Local performance setting: the wire protocol and equality do not depend on this value. */
@Mod.EventBusSubscriber(modid = NeoECOAE.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KeyReuseConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    static { BUILDER.translation("neoecoae.configuration.keyReuse").push("keyReuse"); }
    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Reuse default resource keys in ECO and supported official AE2 factories. Tags, capabilities, content equality and existing saves remain compatible.",
            "在 ECO 与受支持的官方 AE2 工厂中复用默认资源 key；保留 NBT、能力数据、内容相等语义与旧存档格式。")
        .translation("neoecoae.configuration.keyReuse.enabled")
        .define("enabled", true);
    static { BUILDER.pop(); }
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private KeyReuseConfig() {}

    @SubscribeEvent
    public static void loading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) KeyReuse.configure(ENABLED.get());
    }

    @SubscribeEvent
    public static void reloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) KeyReuse.configure(ENABLED.get());
    }

    @SubscribeEvent
    public static void unloading(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == SPEC) KeyReuse.configure(false);
    }
}
