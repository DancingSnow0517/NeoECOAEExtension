package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public class NEConfig {
    public static final long MAX_STORAGE_TRANSFER_RATE = Long.MAX_VALUE;
    public static final int PATTERN_BUS_SLOTS_PER_PAGE = 63;
    public static final int PATTERN_BUS_MIN_PAGES = 1;
    public static final int PATTERN_BUS_MAX_PAGES = 8;
    public static final int CRAFTING_WORKER_BASE_CRAFTS = 32;
    /** Temporary ordinary-path parallel dispatch ceiling until adaptive scheduling is wired in. */
    public static final int MAX_ECO_CPU_PUSH_TICK_LIMIT = 393_216;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER
            .comment(
                "多方块结构尺寸限制。",
                "Multiblock structure size limits.")
            .push("structure");
    }

    private static final ModConfigSpec.IntValue CRAFTING_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "合成系统多方块结构允许的最大长度（以方块计）。",
            "更高的值允许更长的扩展，但可能增加结构检查开销。",
            "Maximum allowed length of the crafting system multiblock, measured in blocks.",
            "Higher values allow longer extensions but may increase structure validation overhead.")
        .defineInRange("craftingSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue COMPUTATION_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "运算系统多方块结构允许的最大长度（以方块计）。",
            "更高的值允许更长的扩展，但可能增加结构检查开销。",
            "Maximum allowed length of the computation system multiblock, measured in blocks.",
            "Higher values allow longer extensions but may increase structure validation overhead.")
        .defineInRange("computationSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue STORAGE_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "存储系统多方块结构允许的最大长度（以方块计）。",
            "更高的值允许更长的扩展，但可能增加结构检查开销。",
            "Maximum allowed length of the storage system multiblock, measured in blocks.",
            "Higher values allow longer extensions but may increase structure validation overhead.")
        .defineInRange("storageSystemMaxLength", 15, 4, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    private static final ModConfigSpec.BooleanValue POST_CRAFTING_EVENT = BUILDER
        .comment(
            "合成系统完成配方时发送原版合成事件（ItemCraftedEvent）。",
            "可能引入额外的事件/监听器开销；安装 Balm 等模组时可能会有较明显影响。",
            "Post the vanilla ItemCraftedEvent when the crafting system completes a recipe.",
            "This may add event/listener overhead, especially when mods such as Balm are installed.")
        .define("postCraftingEvent", false);

    private static final ModConfigSpec.IntValue CRAFTING_PATTERN_BUS_PAGES = BUILDER
        .comment(
            "一个 ECO 智能样板总线提供的样板页数。",
            "每页可存储 63 个编码样板。",
            "Number of pattern pages exposed by one ECO smart pattern bus.",
            "Each page stores 63 encoded patterns.")
        .defineInRange("craftingPatternBusPages", 1, PATTERN_BUS_MIN_PAGES, PATTERN_BUS_MAX_PAGES);

    static {
        BUILDER
            .comment(
                "ECO AE2 快速路径缓存与批量合成选项。",
                "如果整合包遇到配方兼容问题，可以关闭或调低这些值。",
                "ECO AE2 fast-path cache and batch crafting options.",
                "Disable these options or lower their values if a modpack encounters recipe compatibility issues.")
            .push("fastPath");
    }

    private static final ModConfigSpec.BooleanValue ECO_AE2_FAST_PATH_ENABLED = BUILDER
        .comment(
            "启用 ECO AE2 快速路径批量合成缓存。",
            "可大幅减少重复 pattern 执行开销；如遇到特定整合包配方兼容问题，可关闭此选项回退到慢速路径。",
            "启用原版合成事件 postCraftingEvent 时，FastPath 会自动禁用以保留事件语义。",
            "Enable the ECO AE2 fast-path batch crafting cache.",
            "This greatly reduces repeated pattern execution overhead; disable it to fall back to the slow path if needed.",
            "FastPath is automatically disabled when postCraftingEvent is enabled to preserve event semantics.")
        .define("ecoAe2FastPathEnabled", true);

    private static final ModConfigSpec.IntValue ECO_CPU_PUSH_TICK_LIMIT = BUILDER
        .comment(
            "每个 CPU 每 tick 最多尝试推送的普通合成 pattern 数量。",
            "实际值仍会受可用协处理器数量限制。",
            "当前上限为 393216；后续动态发配策略接入后再调整。",
            "Maximum number of regular crafting patterns each CPU attempts to push per tick.",
            "The effective value is still limited by the number of available co-processors.",
            "The temporary hard ceiling is 393216 until adaptive dispatch is integrated.")
        .defineInRange("ecoCpuPushTickLimit", 200_000, 1, MAX_ECO_CPU_PUSH_TICK_LIMIT);

    private static final ModConfigSpec.IntValue ECO_FAST_PATH_CACHE_SIZE = BUILDER
        .comment(
            "每个 ECO 快速路径缓存最多保留的配方条目数量。",
            "Maximum number of recipe entries retained by each ECO fast-path cache.")
        .worldRestart()
        .defineInRange(
            "ecoFastPathCacheSize",
            512,
            ECOCraftingFastPathCache.MIN_CACHE_SIZE,
            ECOCraftingFastPathCache.MAX_CACHE_SIZE
        );

    private static final ModConfigSpec.BooleanValue ENABLE_SOPHISTICATED_TRANSFER_OPTIMIZATION = BUILDER
        .comment(
            "启用 Sophisticated Storage 的可选 ECO 持续传输优化。",
            "遇到整合包兼容冲突时可关闭；关闭后使用 AE2 通用路径和 200 tick 对账。",
            "Enable the optional Sophisticated Storage ECO transfer optimization.",
            "Disable this if a modpack encounters a compatibility conflict; AE2's generic path remains available.")
        .define("enableSophisticatedTransferOptimization", true);

    private static final ModConfigSpec.LongValue STORAGE_TRANSFER_RATE = BUILDER
        .comment(
            "有限存储域每 tick 的最大转移数量。",
            "默认值为 Long.MAX_VALUE；可设置为 1 到 Long.MAX_VALUE。",
            "Maximum amount transferred by a finite storage domain per tick.",
            "Defaults to Long.MAX_VALUE; valid range is 1 to Long.MAX_VALUE.")
        .defineInRange("storageTransferRate", Integer.MAX_VALUE, 1L, MAX_STORAGE_TRANSFER_RATE);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength;
    public static int computationSystemMaxLength;
    public static int storageSystemMaxLength;
    public static boolean postCraftingEvent;
    public static int craftingPatternBusPages = 1;
    public static boolean ecoAe2FastPathEnabled = true;
    public static int ecoCpuPushTickLimit = MAX_ECO_CPU_PUSH_TICK_LIMIT;
    public static int ecoFastPathCacheSize = 512;
    public static boolean enableSophisticatedTransferOptimization = true;
    public static long storageTransferRate = Integer.MAX_VALUE;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        applyConfig();
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        applyConfig();
    }

    private static void applyConfig() {
        craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.get();
        computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.get();
        storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
        craftingPatternBusPages = CRAFTING_PATTERN_BUS_PAGES.get();
        ecoAe2FastPathEnabled = ECO_AE2_FAST_PATH_ENABLED.get();
        ecoCpuPushTickLimit = Math.clamp(ECO_CPU_PUSH_TICK_LIMIT.get(), 1, MAX_ECO_CPU_PUSH_TICK_LIMIT);
        ecoFastPathCacheSize = ECO_FAST_PATH_CACHE_SIZE.get();
        enableSophisticatedTransferOptimization = ENABLE_SOPHISTICATED_TRANSFER_OPTIMIZATION.get();
        storageTransferRate = Math.clamp(STORAGE_TRANSFER_RATE.get(), 1L, MAX_STORAGE_TRANSFER_RATE);
    }

    public static int getCraftingPatternBusPages() {
        return Math.clamp(craftingPatternBusPages, PATTERN_BUS_MIN_PAGES, PATTERN_BUS_MAX_PAGES);
    }

    public static int getCraftingPatternBusSlotCount() {
        return PATTERN_BUS_SLOTS_PER_PAGE * getCraftingPatternBusPages();
    }

    public static int getMaxCraftingPatternBusSlotCount() {
        return PATTERN_BUS_SLOTS_PER_PAGE * PATTERN_BUS_MAX_PAGES;
    }

}
