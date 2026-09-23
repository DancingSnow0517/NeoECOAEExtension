package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public class NEConfig {
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

    static {
        BUILDER
            .comment(
                "ECO 存储系统选项。",
                "ECO storage system options.")
            .push("storage");
    }

    private static final ModConfigSpec.LongValue MEGA_BULK_AUTO_MARK_THRESHOLD = BUILDER
        .comment(
            "ECO 存储主机自动标记可压缩物品时使用的数量阈值；只有数量严格大于此值的物品才会被标记。",
            "Amount threshold used when an ECO storage host automatically marks compressible items; only amounts strictly greater than this value are marked.")
        .defineInRange("megaBulkAutoMarkThreshold", 20_000L, 0L, Long.MAX_VALUE);

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
            "普通回退路径每 CPU 每 tick 最多 64 次；可配置更低额度，不受协处理器数和历史 tick 限制。",
            "当前上限为 393216；FastPath 批量发配不消耗此额度。",
            "Maximum number of regular crafting patterns each CPU attempts to push per tick.",
            "Verified FastPath batches do not consume this limit.",
            "Ordinary fallback is capped at 64 per CPU per tick, independent of co-processors and previous ticks.",
            "The hard ceiling is 393216 to keep ordinary dispatch bounded.")
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

    static {
        BUILDER.pop();
    }

    static {
        BUILDER
            .comment(
                "仅用于排查问题的调试选项。正常游玩时建议保持关闭。",
                "Debug options intended only for troubleshooting. Keep these disabled during normal play.")
            .push("debug");
    }

    static {
        BUILDER
            .comment(
                "ECO 合成计算与发配诊断。",
                "ECO crafting calculation and dispatch diagnostics.")
            .push("calculating");
    }

    private static final ModConfigSpec.BooleanValue ECO_PLANNING_STAGE_DEBUG = BUILDER
        .comment(
            "记录 ECO 快速规划的每个计算阶段，包括非循环规划与循环规划。",
            "同时记录每次规划请求实际选择 ECO 快速规划还是 AE2 原版规划及原因。",
            "日志包含阶段耗时、成功状态、失败原因、目标物品和请求数量。",
            "Log every ECO fast-planning calculation stage, including acyclic and cyclic planning.",
            "Also log whether each request selects ECO fast planning or the native AE2 planner, and why.",
            "Logs stage duration, success state, failure reason, target item and requested amount.")
        .define("ecoPlanningStageDebug", false);

    private static final ModConfigSpec.BooleanValue ECO_CRAFT_SUBMISSION_DEBUG = BUILDER
        .comment(
            "诊断合成确认界面的开始按钮无法启用或提交延迟/失败的问题。",
            "记录开始按钮状态、计划与 CPU 选择、提交路由和提交耗时。",
            "Diagnose why the crafting-confirm Start button is unavailable or why submission is delayed or fails.",
            "Logs the Start-button state, plan and CPU selection, submission route and submission duration.")
        .define("ecoCraftSubmissionDebug", false);

    private static final ModConfigSpec.BooleanValue ECO_DISPATCH_WATCHDOG_DEBUG = BUILDER
        .comment(
            "记录 ECO 发配候选原因及发配前后的任务记账状态。",
            "连续 200 tick 没有实际进展时记录汇总诊断，之后每 1200 tick 再次记录。",
            "日志可能较多；此选项只收集诊断信息，不会修改合成状态。",
            "Log ECO dispatch candidate reasons and task accounting before and after dispatch.",
            "Logs an aggregated diagnostic after 200 ticks without real progress, then every 1200 ticks.",
            "This may produce many logs and only collects diagnostics; it does not change crafting state.")
        .define("ecoDispatchWatchdogDebug", false);

    static {
        BUILDER.pop();
    }

    private static final ModConfigSpec.BooleanValue ECO_CRAFTING_OUTPUT_DELIVERY_DEBUG = BUILDER
        .comment(
            "启用 ECO 合成产物交付等待诊断日志。",
            "同一任务的 Worker 连续 200 tick 无法交付产物后记录首条汇总警告，之后每 1200 tick 再次记录。",
            "等待结束后会记录一条恢复信息；此选项只控制日志，不会改变产物所有权、重试或恢复逻辑。",
            "Enable ECO crafting output-delivery wait diagnostic logs.",
            "Logs one aggregated warning per job after its workers have been unable to deliver outputs for 200 ticks, then every 1200 ticks.",
            "Logs a recovery message when the wait ends; this option only controls logging and never changes output ownership, retry or recovery behavior.")
        .define("ecoCraftingOutputDeliveryDebug", false);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength;
    public static int computationSystemMaxLength;
    public static int storageSystemMaxLength;
    public static long megaBulkAutoMarkThreshold = 20_000L;
    public static boolean postCraftingEvent;
    public static int craftingPatternBusPages = 1;
    public static boolean ecoAe2FastPathEnabled = true;
    public static int ecoCpuPushTickLimit = MAX_ECO_CPU_PUSH_TICK_LIMIT;
    public static int ecoFastPathCacheSize = 512;
    public static boolean ecoPlanningStageDebug = false;
    public static boolean ecoCraftSubmissionDebug = false;
    public static boolean ecoDispatchWatchdogDebug = false;
    public static boolean ecoCraftingOutputDeliveryDebug = false;

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
        megaBulkAutoMarkThreshold = MEGA_BULK_AUTO_MARK_THRESHOLD.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
        craftingPatternBusPages = CRAFTING_PATTERN_BUS_PAGES.get();
        ecoAe2FastPathEnabled = ECO_AE2_FAST_PATH_ENABLED.get();
        ecoCpuPushTickLimit = Math.clamp(ECO_CPU_PUSH_TICK_LIMIT.get(), 1, MAX_ECO_CPU_PUSH_TICK_LIMIT);
        ecoFastPathCacheSize = ECO_FAST_PATH_CACHE_SIZE.get();
        ecoPlanningStageDebug = ECO_PLANNING_STAGE_DEBUG.get();
        ecoCraftSubmissionDebug = ECO_CRAFT_SUBMISSION_DEBUG.get();
        ecoDispatchWatchdogDebug = ECO_DISPATCH_WATCHDOG_DEBUG.get();
        ecoCraftingOutputDeliveryDebug = ECO_CRAFTING_OUTPUT_DELIVERY_DEBUG.get();
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
