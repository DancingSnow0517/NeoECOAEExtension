package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class NEConfig {
    public static final int PATTERN_BUS_SLOTS_PER_PAGE = 63;
    public static final int PATTERN_BUS_MIN_PAGES = 1;
    public static final int PATTERN_BUS_MAX_PAGES = 8;
    public static final int CRAFTING_WORKER_BASE_CRAFTS = 32;
    /** Temporary ordinary-path parallel dispatch ceiling until adaptive scheduling is wired in. */
    public static final int MAX_ECO_CPU_PUSH_TICK_LIMIT = 393_216;

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER
            .comment(
                "多方块结构尺寸限制。",
                "Multiblock structure size limits.")
            .translation("neoecoae.configuration.structure")
            .push("structure");
    }

    private static final ForgeConfigSpec.IntValue CRAFTING_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "合成系统多方块结构允许的最大长度（以方块计）。",
            "更高的值允许更长的扩展，但可能增加结构检查开销。",
            "Maximum allowed length of the crafting system multiblock, measured in blocks.",
            "Higher values allow longer extensions but may increase structure validation overhead.")
        .translation("neoecoae.configuration.craftingSystemMaxLength")
        .defineInRange("craftingSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue COMPUTATION_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "运算系统多方块结构允许的最大长度（以方块计）。",
            "更高的值允许更长的扩展，但可能增加结构检查开销。",
            "Maximum allowed length of the computation system multiblock, measured in blocks.",
            "Higher values allow longer extensions but may increase structure validation overhead.")
        .translation("neoecoae.configuration.computationSystemMaxLength")
        .defineInRange("computationSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue STORAGE_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "存储系统多方块结构允许的最大长度（以方块计）。",
            "更高的值允许更长的扩展，但可能增加结构检查开销。",
            "Maximum allowed length of the storage system multiblock, measured in blocks.",
            "Higher values allow longer extensions but may increase structure validation overhead.")
        .translation("neoecoae.configuration.storageSystemMaxLength")
        .defineInRange("storageSystemMaxLength", 15, 4, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER
            .comment(
                "ECO 存储系统选项。",
                "ECO storage system options.")
            .translation("neoecoae.configuration.storage")
            .push("storage");
    }

    private static final ForgeConfigSpec.LongValue MEGA_BULK_AUTO_MARK_THRESHOLD = BUILDER
        .comment(
            "ECO 存储主机自动标记可压缩物品时使用的数量阈值；只有数量严格大于此值的物品才会被标记。",
            "Amount threshold used when an ECO storage host automatically marks compressible items; only amounts strictly greater than this value are marked.")
        .translation("neoecoae.configuration.megaBulkAutoMarkThreshold")
        .defineInRange("megaBulkAutoMarkThreshold", 20_000L, 0L, Long.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue INFINITE_MIGRATION_TICK_BUDGET_MICROS = BUILDER
        .comment(
            "迁移到无限存储时，每个主机每 tick 可用于迁移的时间预算（微秒）。",
            "每 tick 至少迁移一种资源；预算在同一 tick 内由各驱动器共享。",
            "Time budget, in microseconds, each storage host may spend per tick migrating disks into infinite storage.",
            "At least one resource is migrated per tick; the budget is shared by all drives within the same tick.")
        .translation("neoecoae.configuration.infiniteMigrationTickBudgetMicros")
        .defineInRange("infiniteMigrationTickBudgetMicros", 2_000, 1, 1_000_000);

    private static final ForgeConfigSpec.IntValue INFINITE_MIGRATION_MAX_KEYS_PER_TICK = BUILDER
        .comment(
            "迁移到无限存储时，每个主机每 tick 最多迁移的资源种类数。",
            "Maximum number of resource types each storage host migrates into infinite storage per tick.")
        .translation("neoecoae.configuration.infiniteMigrationMaxKeysPerTick")
        .defineInRange("infiniteMigrationMaxKeysPerTick", 512, 1, 1_000_000);

    static {
        BUILDER.pop();
    }

    private static final ForgeConfigSpec.BooleanValue POST_CRAFTING_EVENT = BUILDER
        .comment(
            "合成系统完成配方时发送原版合成事件（ItemCraftedEvent）。",
            "可能引入额外的事件/监听器开销；安装 Balm 等模组时可能会有较明显影响。",
            "Post the vanilla ItemCraftedEvent when the crafting system completes a recipe.",
            "This may add event/listener overhead, especially when mods such as Balm are installed.")
        .translation("neoecoae.configuration.postCraftingEvent")
        .define("postCraftingEvent", false);

    private static final ForgeConfigSpec.BooleanValue LARGE_WORKSTATION_TEMPERATURE_ENABLED = BUILDER
        .comment(
            "启用大型集成工作站的温度、预热和过热恢复机制，默认开启。",
            "关闭后使用冷却液模式：同时开启超频与主动冷却，水/钠/凛冰分别提供 16384/65536/262144 并行，常规为 256。",
            "冷却液模式仅在超频批次实际推进时消耗 100 mB 冷却液，不检查温度；全部档位保持 1 倍配方能耗。",
            "修改后重新进入世界或重启服务器。",
            "Enable large workstation temperature, preheating and overheat recovery. Enabled by default.",
            "When disabled, overclock plus active cooling selects 16384/65536/262144 parallel from water/sodium/cryotheum; normal is 256.",
            "Coolant mode consumes 100 mB only when an overclocked batch advances, ignores temperature and keeps x1 recipe energy.",
            "Reopen the world or restart the server after changing this option.")
        .worldRestart()
        .translation("neoecoae.configuration.largeWorkstationTemperatureEnabled")
        .define("largeWorkstationTemperatureEnabled", true);

    private static final ForgeConfigSpec.BooleanValue LARGE_WORKSTATION_IGNORE_UPGRADE_CARDS = BUILDER
        .comment(
            "忽略大型集成工作站的升级卡限制，直接解锁所有受支持的兼容配方，默认关闭。",
            "兼容配方默认加载；关闭此选项时，高级 AE、扩展 AE、水晶科技和闪电科技的配方由对应升级卡解锁。",
            "空白卡不增加配方类别，常规配方与没有专用升级卡的兼容配方始终可用。",
            "修改后重新进入世界或重启服务器。",
            "Ignore large workstation upgrade cards and unlock every supported compatible recipe. Disabled by default.",
            "Compatible recipes are always loaded; with this option off, install the matching card to unlock its recipe family.",
            "The blank card adds no recipe family; native recipes and adapters without a dedicated card remain available.",
            "Reopen the world or restart the server after changing this option.")
        .worldRestart()
        .translation("neoecoae.configuration.largeWorkstationIgnoreUpgradeCards")
        .define("largeWorkstationIgnoreUpgradeCards", false);

    private static final ForgeConfigSpec.IntValue CRAFTING_PATTERN_BUS_PAGES = BUILDER
        .comment(
            "一个 ECO 智能样板总线提供的样板页数。",
            "每页可存储 63 个编码样板。",
            "Number of pattern pages exposed by one ECO smart pattern bus.",
            "Each page stores 63 encoded patterns.")
        .translation("neoecoae.configuration.craftingPatternBusPages")
        .defineInRange("craftingPatternBusPages", 1, PATTERN_BUS_MIN_PAGES, PATTERN_BUS_MAX_PAGES);

    static {
        BUILDER
            .comment(
                "ECO AE2 快速路径缓存与批量合成限制。",
                "FastPath 默认启用；启用原版合成事件时会回退到逐次合成，以保留事件语义。",
                "ECO AE2 fast-path cache and batch crafting limits.",
                "FastPath is enabled by default and falls back to single-craft execution when postCraftingEvent is enabled.")
            .translation("neoecoae.configuration.fastPath")
            .push("fastPath");
    }

    private static final ForgeConfigSpec.IntValue ECO_CPU_PUSH_TICK_LIMIT = BUILDER
        .comment(
            "每个 CPU 每 tick 最多尝试推送的普通合成 pattern 数量。",
            "普通回退路径每 CPU 每 tick 最多 64 次；可配置更低额度，不受协处理器数和历史 tick 限制。",
            "当前上限为 393216；FastPath 批量发配不消耗此额度。",
            "Maximum number of regular crafting patterns each CPU attempts to push per tick.",
            "Verified FastPath batches do not consume this limit.",
            "Ordinary fallback is capped at 64 per CPU per tick, independent of co-processors and previous ticks.",
            "The hard ceiling is 393216 to keep ordinary dispatch bounded.")
        .translation("neoecoae.configuration.ecoCpuPushTickLimit")
        .defineInRange("ecoCpuPushTickLimit", 200_000, 1, MAX_ECO_CPU_PUSH_TICK_LIMIT);

    private static final ForgeConfigSpec.IntValue ECO_FAST_PATH_CACHE_SIZE = BUILDER
        .comment(
            "每个 ECO 快速路径缓存最多保留的配方条目数量。",
            "Maximum number of recipe entries retained by each ECO fast-path cache.")
        .worldRestart()
        .translation("neoecoae.configuration.ecoFastPathCacheSize")
        .defineInRange(
            "ecoFastPathCacheSize",
            512,
            ECOCraftingFastPathCache.MIN_CACHE_SIZE,
            ECOCraftingFastPathCache.MAX_CACHE_SIZE
        );

    private static final ForgeConfigSpec.IntValue ECO_PLANNING_MAX_MILLIS = BUILDER
        .comment("一次 ECO 规划会话的总耗时预算（毫秒），路线、种子和 CRAFT_LESS 探测共享；超限显示未解。",
            "Shared cooperative time budget for one ECO planning session, including routes, seeds and CRAFT_LESS probes.")
        .translation("neoecoae.configuration.ecoPlanningMaxMillis")
        .defineInRange("ecoPlanningMaxMillis", 120_000, 1, 600_000);

    private static final ForgeConfigSpec.IntValue ECO_PLANNING_MAX_WORK = BUILDER
        .comment("一次 ECO 规划会话的检查点总预算；切换路线和种子不会重置预算。",
            "Shared checkpoint work budget; changing routes or startup seeds does not reset it.")
        .translation("neoecoae.configuration.ecoPlanningMaxWork")
        .defineInRange("ecoPlanningMaxWork", 50_000_000, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue ECO_PLANNING_MAX_MEMORY_MIB = BUILDER
        .comment("循环求解保留数据的估算内存预算（MiB），覆盖矩阵和搜索状态；超限显示未解。",
            "Estimated retained memory budget in MiB for cycle matrices and search states; exhaustion remains unknown.")
        .translation("neoecoae.configuration.ecoPlanningMaxMemoryMiB")
        .defineInRange("ecoPlanningMaxMemoryMiB", 64, 1, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER
            .comment(
                "仅用于排查问题的调试选项。正常游玩时建议保持关闭。",
                "Debug options intended only for troubleshooting. Keep these disabled during normal play.")
            .translation("neoecoae.configuration.debug")
            .push("debug");
    }

    static {
        BUILDER
            .comment(
                "ECO 合成计算与发配诊断。",
                "ECO crafting calculation and dispatch diagnostics.")
            .translation("neoecoae.configuration.calculating")
            .push("calculating");
    }

    private static final ForgeConfigSpec.BooleanValue ECO_PLANNING_STAGE_DEBUG = BUILDER
        .comment(
            "记录 ECO 快速规划的每个计算阶段，包括非循环规划与循环规划。",
            "同时记录每次规划请求实际选择 ECO 快速规划还是 AE2 原版规划及原因。",
            "日志包含阶段耗时、成功状态、失败原因、目标物品和请求数量。",
            "Log every ECO fast-planning calculation stage, including acyclic and cyclic planning.",
            "Also log whether each request selects ECO fast planning or the native AE2 planner, and why.",
            "Logs stage duration, success state, failure reason, target item and requested amount.")
        .translation("neoecoae.configuration.ecoPlanningStageDebug")
        .define("ecoPlanningStageDebug", false);

    private static final ForgeConfigSpec.BooleanValue ECO_CRAFT_SUBMISSION_DEBUG = BUILDER
        .comment(
            "诊断合成确认界面的开始按钮无法启用或提交延迟/失败的问题。",
            "记录开始按钮状态、计划与 CPU 选择、提交路由和提交耗时。",
            "Diagnose why the crafting-confirm Start button is unavailable or why submission is delayed or fails.",
            "Logs the Start-button state, plan and CPU selection, submission route and submission duration.")
        .translation("neoecoae.configuration.ecoCraftSubmissionDebug")
        .define("ecoCraftSubmissionDebug", false);

    private static final ForgeConfigSpec.BooleanValue ECO_DISPATCH_WATCHDOG_DEBUG = BUILDER
        .comment(
            "记录 ECO 发配候选原因及发配前后的任务记账状态。",
            "连续 200 tick 没有实际进展时记录汇总诊断，之后每 1200 tick 再次记录。",
            "日志可能较多；此选项只收集诊断信息，不会修改合成状态。",
            "Log ECO dispatch candidate reasons and task accounting before and after dispatch.",
            "Logs an aggregated diagnostic after 200 ticks without real progress, then every 1200 ticks.",
            "This may produce many logs and only collects diagnostics; it does not change crafting state.")
        .translation("neoecoae.configuration.ecoDispatchWatchdogDebug")
        .define("ecoDispatchWatchdogDebug", false);

    static {
        BUILDER.pop();
    }

    private static final ForgeConfigSpec.BooleanValue ECO_CRAFTING_OUTPUT_DELIVERY_DEBUG = BUILDER
        .comment(
            "启用 ECO 合成产物交付等待诊断日志。",
            "同一任务的 Worker 连续 200 tick 无法交付产物后记录首条汇总警告，之后每 1200 tick 再次记录。",
            "等待结束后会记录一条恢复信息；此选项只控制日志，不会改变产物所有权、重试或恢复逻辑。",
            "Enable ECO crafting output-delivery wait diagnostic logs.",
            "Logs one aggregated warning per job after its workers have been unable to deliver outputs for 200 ticks, then every 1200 ticks.",
            "Logs a recovery message when the wait ends; this option only controls logging and never changes output ownership, retry or recovery behavior.")
        .translation("neoecoae.configuration.ecoCraftingOutputDeliveryDebug")
        .define("ecoCraftingOutputDeliveryDebug", false);

    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.getDefault();
    public static int computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.getDefault();
    public static int storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.getDefault();
    public static long megaBulkAutoMarkThreshold = 20_000L;
    public static int infiniteMigrationTickBudgetMicros = 2_000;
    public static int infiniteMigrationMaxKeysPerTick = 512;
    public static boolean postCraftingEvent;
    public static boolean largeWorkstationIgnoreUpgradeCards = false;
    public static boolean largeWorkstationTemperatureEnabled = true;
    public static int craftingPatternBusPages = 1;
    public static int ecoCpuPushTickLimit = MAX_ECO_CPU_PUSH_TICK_LIMIT;
    public static int ecoFastPathCacheSize = 512;
    public static int ecoPlanningMaxMillis = 120_000;
    public static int ecoPlanningMaxWork = 50_000_000;
    public static int ecoPlanningMaxMemoryMiB = 64;
    public static boolean ecoPlanningStageDebug = false;
    public static boolean ecoCraftSubmissionDebug = false;
    public static boolean ecoDispatchWatchdogDebug = false;
    public static boolean ecoCraftingOutputDeliveryDebug = false;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) applyConfig();
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) applyConfig();
    }

    private static void applyConfig() {
        craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.get();
        computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.get();
        storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.get();
        megaBulkAutoMarkThreshold = MEGA_BULK_AUTO_MARK_THRESHOLD.get();
        infiniteMigrationTickBudgetMicros = INFINITE_MIGRATION_TICK_BUDGET_MICROS.get();
        infiniteMigrationMaxKeysPerTick = INFINITE_MIGRATION_MAX_KEYS_PER_TICK.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
        largeWorkstationIgnoreUpgradeCards = LARGE_WORKSTATION_IGNORE_UPGRADE_CARDS.get();
        largeWorkstationTemperatureEnabled = LARGE_WORKSTATION_TEMPERATURE_ENABLED.get();
        craftingPatternBusPages = CRAFTING_PATTERN_BUS_PAGES.get();
        ecoCpuPushTickLimit = JdkCompat.clamp(ECO_CPU_PUSH_TICK_LIMIT.get(), 1, MAX_ECO_CPU_PUSH_TICK_LIMIT);
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
        return JdkCompat.clamp(craftingPatternBusPages, PATTERN_BUS_MIN_PAGES, PATTERN_BUS_MAX_PAGES);
    }

    public static int getCraftingPatternBusSlotCount() {
        return PATTERN_BUS_SLOTS_PER_PAGE * getCraftingPatternBusPages();
    }

    public static int getMaxCraftingPatternBusSlotCount() {
        return PATTERN_BUS_SLOTS_PER_PAGE * PATTERN_BUS_MAX_PAGES;
    }

}
