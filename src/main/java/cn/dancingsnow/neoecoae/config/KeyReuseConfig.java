package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Local performance setting: the wire protocol and equality do not depend on this value. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public final class KeyReuseConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    static { BUILDER.translation("neoecoae.configuration.keyReuse").push("keyReuse"); }
    private static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Reuse plain resource keys inside ECO only. AE2/addon public factories are unchanged; existing saves remain compatible.",
            "仅在 ECO 内部复用无额外数据的资源 key；不替换 AE2 或附属的公共工厂，保留值相等与旧存档格式。")
        .translation("neoecoae.configuration.keyReuse.enabled")
        .define("enabled", true);
    static { BUILDER.pop(); }
    public static final ModConfigSpec SPEC = BUILDER.build();

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
