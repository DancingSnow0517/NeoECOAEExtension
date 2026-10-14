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

    /*
     * Spec defaults. Each one is used in its define call and as the initial value of the matching static mirror
     * field, so the two cannot drift apart before the config is loaded.
     */
    private static final int DEFAULT_CRAFTING_SYSTEM_MAX_LENGTH = 15;
    private static final int DEFAULT_COMPUTATION_SYSTEM_MAX_LENGTH = 15;
    private static final int DEFAULT_STORAGE_SYSTEM_MAX_LENGTH = 15;
    private static final long DEFAULT_MEGA_BULK_AUTO_MARK_THRESHOLD = 20_000L;
    private static final boolean DEFAULT_POST_CRAFTING_EVENT = false;
    private static final boolean DEFAULT_LARGE_WORKSTATION_COMPAT_RECIPES_ENABLED = false;
    private static final int DEFAULT_CRAFTING_PATTERN_BUS_PAGES = 1;
    private static final int DEFAULT_ECO_FAST_PATH_CACHE_SIZE = 512;
    private static final int DEFAULT_ECO_PLANNING_MAX_MILLIS = 10_000;
    private static final int DEFAULT_ECO_PLANNING_MAX_WORK = 5_000_000;
    private static final int DEFAULT_ECO_PLANNING_MAX_MEMORY_MIB = 64;
    private static final boolean DEFAULT_ECO_PLANNING_STAGE_DEBUG = false;
    private static final boolean DEFAULT_ECO_CRAFT_SUBMISSION_DEBUG = false;
    private static final boolean DEFAULT_ECO_DISPATCH_WATCHDOG_DEBUG = false;
    private static final boolean DEFAULT_ECO_CRAFTING_OUTPUT_DELIVERY_DEBUG = false;

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
            "合成子系统多方块结构允许的最大长度（单位：方块）。",
            "数值越大，可扩展的模块/组件越长，但结构检测开销也可能更高。",
            "Maximum length (in blocks) allowed for the Crafting System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost.")
        .defineInRange("craftingSystemMaxLength", DEFAULT_CRAFTING_SYSTEM_MAX_LENGTH, 5, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue COMPUTATION_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "计算子系统多方块结构允许的最大长度（单位：方块）。",
            "数值越大，可扩展的模块/组件越长，但结构检测开销也可能更高。",
            "Maximum length (in blocks) allowed for the Computation System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost.")
        .defineInRange("computationSystemMaxLength", DEFAULT_COMPUTATION_SYSTEM_MAX_LENGTH, 5, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue STORAGE_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "存储子系统多方块结构允许的最大长度（单位：方块）。",
            "数值越大，可扩展的模块/组件越长，但结构检测开销也可能更高。",
            "Maximum length (in blocks) allowed for the Storage System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost.")
        .defineInRange("storageSystemMaxLength", DEFAULT_STORAGE_SYSTEM_MAX_LENGTH, 4, Integer.MAX_VALUE);

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
            "ECO 存储主机自动标记可压缩物品时使用的数量阈值。",
            "只有数量严格大于该值的物品才会被标记。",
            "Amount threshold used when an ECO storage host automatically marks compressible items.",
            "Only amounts strictly greater than this value are marked.")
        .defineInRange("megaBulkAutoMarkThreshold", DEFAULT_MEGA_BULK_AUTO_MARK_THRESHOLD, 0L, Long.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    private static final ModConfigSpec.BooleanValue POST_CRAFTING_EVENT = BUILDER
        .comment(
            "当合成子系统完成一次配方时，是否触发原版的合成事件（ItemCraftedEvent）。",
            "开启后可能引入额外事件/监听开销；在安装 Balm 等模组时可能更明显。",
            "Post a vanilla crafting event (ItemCraftedEvent) when the Crafting System finishes a recipe.",
            "May introduce extra event/listener overhead; can be more noticeable with mods like Balm installed.")
        .define("postCraftingEvent", DEFAULT_POST_CRAFTING_EVENT);

    private static final ModConfigSpec.BooleanValue LARGE_WORKSTATION_COMPAT_RECIPES_ENABLED = BUILDER
        .comment(
            "允许大型集成工作站处理受支持模组的联动配方，默认关闭。",
            "关闭时仅处理本模组的集成工作站配方。",
            "修改后重新进入世界或重启服务器，以刷新配方浏览器。",
            "Allow the large integrated workstation to process supported mods' recipes. Disabled by default.",
            "When disabled, only native integrated workstation recipes are available.",
            "Reopen the world or restart the server after changing this option to refresh recipe viewers.")
        .worldRestart()
        .define("largeWorkstationCompatRecipesEnabled", DEFAULT_LARGE_WORKSTATION_COMPAT_RECIPES_ENABLED);

    private static final ModConfigSpec.IntValue CRAFTING_PATTERN_BUS_PAGES = BUILDER
        .comment(
            "每个 ECO 智能样板总线暴露的样板页数。",
            "每页可存储 63 个已编码样板。",
            "Number of pattern pages exposed by one ECO smart pattern bus.",
            "Each page stores 63 encoded patterns.")
        .defineInRange("craftingPatternBusPages", DEFAULT_CRAFTING_PATTERN_BUS_PAGES, PATTERN_BUS_MIN_PAGES, PATTERN_BUS_MAX_PAGES);

    static {
        BUILDER
            .comment(
                "ECO AE2 快速路径缓存与批量合成限制。",
                "FastPath 默认启用；启用原版合成事件时会回退到逐次合成，以保留事件语义。",
                "ECO AE2 fast path cache and batch crafting limits.",
                "Fast Path is enabled by default and falls back to single-craft execution when Post Crafting Event is enabled.")
            .push("fastPath");
    }

    private static final ModConfigSpec.IntValue ECO_FAST_PATH_CACHE_SIZE = BUILDER
        .comment(
            "每个 ECO 快速路径缓存最多保留的配方条目数量。",
            "Maximum recipe entries kept in each ECO fast path cache.")
        .worldRestart()
        .defineInRange(
            "ecoFastPathCacheSize",
            DEFAULT_ECO_FAST_PATH_CACHE_SIZE,
            ECOCraftingFastPathCache.MIN_CACHE_SIZE,
            ECOCraftingFastPathCache.MAX_CACHE_SIZE
        );

    private static final ModConfigSpec.IntValue ECO_PLANNING_MAX_MILLIS = BUILDER
        .comment(
            "一次 ECO 规划会话的总耗时预算（毫秒），路线、种子和 CRAFT_LESS 探测共享；超限显示未解。",
            "Shared cooperative time budget for one ECO planning session, including routes, seeds and CRAFT_LESS probes.",
            "Exceeding it shows the plan as unknown.")
        .defineInRange("ecoPlanningMaxMillis", DEFAULT_ECO_PLANNING_MAX_MILLIS, 1, 600_000);

    private static final ModConfigSpec.IntValue ECO_PLANNING_MAX_WORK = BUILDER
        .comment(
            "一次 ECO 规划会话的检查点总预算；切换路线和种子不会重置预算。",
            "Shared checkpoint work budget for one ECO planning session.",
            "Changing routes or startup seeds does not reset it.")
        .defineInRange("ecoPlanningMaxWork", DEFAULT_ECO_PLANNING_MAX_WORK, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ECO_PLANNING_MAX_MEMORY_MIB = BUILDER
        .comment(
            "循环求解保留数据的估算内存预算（MiB），覆盖矩阵和搜索状态；超限显示未解。",
            "Estimated memory budget in MiB for data retained by cycle solving, including cycle matrices and search states.",
            "Exhaustion shows the plan as unknown.")
        .defineInRange("ecoPlanningMaxMemoryMiB", DEFAULT_ECO_PLANNING_MAX_MEMORY_MIB, 1, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER
            .comment(
                "仅用于排查问题的选项。正常游玩时建议保持关闭。",
                "Troubleshooting options. Keep these disabled during normal play.")
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
            "Also logs whether each request selects ECO fast planning or the native AE2 planner, and why.",
            "Includes stage duration, success state, failure reason, target item and requested amount.")
        .define("ecoPlanningStageDebug", DEFAULT_ECO_PLANNING_STAGE_DEBUG);

    private static final ModConfigSpec.BooleanValue ECO_CRAFT_SUBMISSION_DEBUG = BUILDER
        .comment(
            "用于诊断合成确认界面的开始按钮无法启用或提交延迟/失败的问题。",
            "记录开始按钮状态、计划与 CPU 选择、提交路由和提交耗时。",
            "Diagnose why the crafting-confirm Start button is unavailable or why submission is delayed or fails.",
            "Includes Start-button state, plan and CPU selection, submission route and elapsed time.")
        .define("ecoCraftSubmissionDebug", DEFAULT_ECO_CRAFT_SUBMISSION_DEBUG);

    private static final ModConfigSpec.BooleanValue ECO_DISPATCH_WATCHDOG_DEBUG = BUILDER
        .comment(
            "记录 ECO 发配候选原因及发配前后的任务记账状态。",
            "连续 200 tick 没有实际进展时记录汇总诊断，之后每 1200 tick 再次记录。",
            "此选项只收集诊断信息，不会修改合成状态。",
            "Log ECO dispatch candidate reasons and task accounting before and after dispatch.",
            "Also logs an aggregated diagnostic after 200 ticks without real progress, then every 1200 ticks.",
            "This option only collects diagnostics and does not change crafting state.")
        .define("ecoDispatchWatchdogDebug", DEFAULT_ECO_DISPATCH_WATCHDOG_DEBUG);

    static {
        BUILDER.pop();
    }

    private static final ModConfigSpec.BooleanValue ECO_CRAFTING_OUTPUT_DELIVERY_DEBUG = BUILDER
        .comment(
            "同一合成任务的 Worker 连续 200 tick 无法交付产物后记录首条汇总警告，之后每 1200 tick 再次记录。",
            "等待结束后会记录一条恢复信息。",
            "此选项只控制日志，不会改变产物所有权、重试或恢复逻辑。",
            "Log one aggregated warning per crafting job after its workers have been unable to deliver outputs for 200 ticks, then every 1200 ticks.",
            "A recovery message is logged when the wait ends.",
            "This option only controls logging and never changes output ownership, retry or recovery behavior.")
        .define("ecoCraftingOutputDeliveryDebug", DEFAULT_ECO_CRAFTING_OUTPUT_DELIVERY_DEBUG);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength = DEFAULT_CRAFTING_SYSTEM_MAX_LENGTH;
    public static int computationSystemMaxLength = DEFAULT_COMPUTATION_SYSTEM_MAX_LENGTH;
    public static int storageSystemMaxLength = DEFAULT_STORAGE_SYSTEM_MAX_LENGTH;
    public static long megaBulkAutoMarkThreshold = DEFAULT_MEGA_BULK_AUTO_MARK_THRESHOLD;
    public static boolean postCraftingEvent = DEFAULT_POST_CRAFTING_EVENT;
    public static boolean largeWorkstationCompatRecipesEnabled = DEFAULT_LARGE_WORKSTATION_COMPAT_RECIPES_ENABLED;
    public static int craftingPatternBusPages = DEFAULT_CRAFTING_PATTERN_BUS_PAGES;
    public static int ecoFastPathCacheSize = DEFAULT_ECO_FAST_PATH_CACHE_SIZE;
    public static int ecoPlanningMaxMillis = DEFAULT_ECO_PLANNING_MAX_MILLIS;
    public static int ecoPlanningMaxWork = DEFAULT_ECO_PLANNING_MAX_WORK;
    public static int ecoPlanningMaxMemoryMiB = DEFAULT_ECO_PLANNING_MAX_MEMORY_MIB;
    public static boolean ecoPlanningStageDebug = DEFAULT_ECO_PLANNING_STAGE_DEBUG;
    public static boolean ecoCraftSubmissionDebug = DEFAULT_ECO_CRAFT_SUBMISSION_DEBUG;
    public static boolean ecoDispatchWatchdogDebug = DEFAULT_ECO_DISPATCH_WATCHDOG_DEBUG;
    public static boolean ecoCraftingOutputDeliveryDebug = DEFAULT_ECO_CRAFTING_OUTPUT_DELIVERY_DEBUG;

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
        largeWorkstationCompatRecipesEnabled = LARGE_WORKSTATION_COMPAT_RECIPES_ENABLED.get();
        craftingPatternBusPages = CRAFTING_PATTERN_BUS_PAGES.get();
        ecoFastPathCacheSize = ECO_FAST_PATH_CACHE_SIZE.get();
        ecoPlanningMaxMillis = ECO_PLANNING_MAX_MILLIS.get();
        ecoPlanningMaxWork = ECO_PLANNING_MAX_WORK.get();
        ecoPlanningMaxMemoryMiB = ECO_PLANNING_MAX_MEMORY_MIB.get();
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
