package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public class NEConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER
            .comment("多方块结构尺寸限制。")
            .push("structure");
    }

    private static final ModConfigSpec.IntValue CRAFTING_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "合成系统多方块结构允许的最大长度（以方块计）。",
            "更高的值允许更长的扩展，但可能增加结构检查开销。")
        .defineInRange("craftingSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue COMPUTATION_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "运算系统多方块结构允许的最大长度（以方块计）。",
            "更高的值允许更长的扩展，但可能增加结构检查开销。")
        .defineInRange("computationSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue STORAGE_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "存储系统多方块结构允许的最大长度（以方块计）。",
            "更高的值允许更长的扩展，但可能增加结构检查开销。")
        .defineInRange("storageSystemMaxLength", 15, 4, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    private static final ModConfigSpec.BooleanValue POST_CRAFTING_EVENT = BUILDER
        .comment(
            "合成系统完成配方时发送原版合成事件（ItemCraftedEvent）。",
            "可能引入额外的事件/监听器开销；安装 Balm 等模组时可能会有较明显影响。")
        .define("postCraftingEvent", false);

    static {
        BUILDER
            .comment(
                "ECO AE2 快速路径缓存与批量合成选项。",
                "如果整合包遇到配方兼容问题，可以关闭或调低这些值。")
            .push("fastPath");
    }

    private static final ModConfigSpec.BooleanValue ECO_AE2_FAST_PATH_ENABLED = BUILDER
        .comment(
            "启用 ECO AE2 快速路径批量合成缓存。",
            "可大幅减少重复 pattern 执行开销；如遇到特定整合包配方兼容问题，可关闭此选项回退到慢速路径。",
            "启用原版合成事件 postCraftingEvent 时，FastPath 会自动禁用以保留事件语义。")
        .define("ecoAe2FastPathEnabled", true);

    private static final ModConfigSpec.BooleanValue DEBUG_ECO_FAST_PATH = BUILDER
        .comment("定期向日志输出 ECO 快速路径缓存统计信息。")
        .define("debugEcoFastPath", false);

    private static final ModConfigSpec.IntValue ECO_CPU_PUSH_TICK_LIMIT = BUILDER
        .comment(
            "每个 CPU 每 tick 最多尝试推送的普通合成 pattern 数量。",
            "实际值仍会受可用协处理器数量限制。")
        .defineInRange("ecoCpuPushTickLimit", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ECO_BATCH_FAST_PATH_LIMIT = BUILDER
        .comment("单次快速路径批量推送最多合并的合成次数。")
        .defineInRange("ecoBatchFastPathLimit", 64, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ECO_BATCH_FAST_PATH_TICK_LIMIT = BUILDER
        .comment("每个 CPU 每 tick 最多通过快速路径批量推送的合成次数。")
        .defineInRange("ecoBatchFastPathTickLimit", 256, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ECO_FAST_PATH_CACHE_SIZE = BUILDER
        .comment("每个 ECO 快速路径缓存最多保留的配方条目数量。")
        .worldRestart()
        .defineInRange("ecoFastPathCacheSize", 512, 16, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength;
    public static int computationSystemMaxLength;
    public static int storageSystemMaxLength;
    public static boolean postCraftingEvent;
    public static boolean ecoAe2FastPathEnabled = true;
    public static boolean debugEcoFastPath;
    public static int ecoCpuPushTickLimit = Integer.MAX_VALUE;
    public static int ecoBatchFastPathLimit = 64;
    public static int ecoBatchFastPathTickLimit = 256;
    public static int ecoFastPathCacheSize = 512;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.get();
        computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.get();
        storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
        ecoAe2FastPathEnabled = ECO_AE2_FAST_PATH_ENABLED.get();
        debugEcoFastPath = DEBUG_ECO_FAST_PATH.get();
        ecoCpuPushTickLimit = ECO_CPU_PUSH_TICK_LIMIT.get();
        ecoBatchFastPathLimit = ECO_BATCH_FAST_PATH_LIMIT.get();
        ecoBatchFastPathTickLimit = ECO_BATCH_FAST_PATH_TICK_LIMIT.get();
        ecoFastPathCacheSize = ECO_FAST_PATH_CACHE_SIZE.get();
    }
}
