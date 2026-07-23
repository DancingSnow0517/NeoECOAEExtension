package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public class NEConfig {
    public static final int PATTERN_BUS_SLOTS_PER_PAGE = 63;
    public static final int PATTERN_BUS_MIN_PAGES = 1;
    public static final int PATTERN_BUS_MAX_PAGES = 8;
    public static final int CAPACITY_POWER_MIN = 0;
    public static final int CAPACITY_POWER_MAX = 16;
    private static final int CAPACITY_POWER_DEFAULT = 0;
    public static final int CRAFTING_WORKER_BASE_CRAFTS = 32;

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

    private static final ModConfigSpec.BooleanValue ENABLE_INFINITE_STORAGE = BUILDER
        .comment(
            "在存储控制器上启用 ECO 无限存储。",
            "需要 64 个无限组件和 16 个 L9 存储矩阵；禁用后会阻止新的无限存储迁移。",
            "已有的无限存储域文件会保留，不会被此选项删除。",
            "Enable ECO infinite storage on the storage controller.",
            "Requires 64 infinite components and 16 L9 storage matrices; disabling it blocks new infinite migrations.",
            "Existing infinite storage domain files are preserved and are not deleted by this option.")
        .define("enableInfiniteStorage", false);

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
                "ECO 合成与运算系统的容量倍率暂时固定为默认值。",
                "Capacity multipliers for ECO crafting and computation systems are temporarily locked to defaults.")
            .push("capacity");
    }

    private static final ModConfigSpec.IntValue CRAFTING_CAPACITY_POWER = BUILDER
        .comment(
            "合成容量倍率暂时锁定为默认值 0（x1），无法调整。",
            "L4/L6/L9 合成工作器和 FT 并行核心均使用各自的默认数值。",
            "The crafting capacity multiplier is temporarily locked to the default value 0 (x1) and cannot be changed.",
            "L4/L6/L9 crafting workers and FT parallel cores use their respective default values.")
        .worldRestart()
        .defineInRange(
            "craftingCapacityPower",
            CAPACITY_POWER_DEFAULT,
            CAPACITY_POWER_DEFAULT,
            CAPACITY_POWER_DEFAULT
        );

    private static final ModConfigSpec.IntValue COMPUTATION_PARALLEL_CORE_POWER = BUILDER
        .comment(
            "运算并行核心倍率暂时锁定为默认值 0（x1），无法调整。",
            "L4/L6/L9 运算并行核心均使用各自的默认数值。",
            "The computation parallel-core multiplier is temporarily locked to the default value 0 (x1) and cannot be changed.",
            "L4/L6/L9 computation parallel cores use their respective default values.")
        .worldRestart()
        .defineInRange(
            "computationParallelCorePower",
            CAPACITY_POWER_DEFAULT,
            CAPACITY_POWER_DEFAULT,
            CAPACITY_POWER_DEFAULT
        );

    static {
        BUILDER.pop();
    }

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

    private static final ModConfigSpec.BooleanValue DEBUG_ECO_FAST_PATH = BUILDER
        .comment(
            "定期向日志输出 ECO 快速路径缓存统计信息。",
            "Periodically write ECO fast-path cache statistics to the log.")
        .define("debugEcoFastPath", false);

    private static final ModConfigSpec.IntValue ECO_CPU_PUSH_TICK_LIMIT = BUILDER
        .comment(
            "每个 CPU 每 tick 最多尝试推送的普通合成 pattern 数量。",
            "实际值仍会受可用协处理器数量限制。",
            "Maximum number of regular crafting patterns each CPU attempts to push per tick.",
            "The effective value is still limited by the number of available co-processors.")
        .defineInRange("ecoCpuPushTickLimit", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

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

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength;
    public static int computationSystemMaxLength;
    public static int storageSystemMaxLength;
    public static boolean postCraftingEvent;
    public static boolean enableInfiniteStorage;
    public static int craftingPatternBusPages = 1;
    public static boolean ecoAe2FastPathEnabled = true;
    public static boolean debugEcoFastPath;
    public static int ecoCpuPushTickLimit = Integer.MAX_VALUE;
    public static int ecoFastPathCacheSize = 512;

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
        enableInfiniteStorage = ENABLE_INFINITE_STORAGE.get();
        craftingPatternBusPages = CRAFTING_PATTERN_BUS_PAGES.get();
        // Read the locked entries so NeoForge can correct legacy values, but never apply them at runtime.
        CRAFTING_CAPACITY_POWER.get();
        COMPUTATION_PARALLEL_CORE_POWER.get();
        ecoAe2FastPathEnabled = ECO_AE2_FAST_PATH_ENABLED.get();
        debugEcoFastPath = DEBUG_ECO_FAST_PATH.get();
        ecoCpuPushTickLimit = ECO_CPU_PUSH_TICK_LIMIT.get();
        ecoFastPathCacheSize = ECO_FAST_PATH_CACHE_SIZE.get();
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

    public static int getCraftingWorkerBaseCrafts() {
        return multiplyByPowerOfTwo(CRAFTING_WORKER_BASE_CRAFTS, CAPACITY_POWER_DEFAULT);
    }

    public static int getCraftingParallelCoreCount(int baseCount) {
        return multiplyByPowerOfTwo(baseCount, CAPACITY_POWER_DEFAULT);
    }

    public static int getComputationParallelCoreCount(int baseCount) {
        return multiplyByPowerOfTwo(baseCount, CAPACITY_POWER_DEFAULT);
    }

    static int multiplyByPowerOfTwo(int baseValue, int power) {
        int clampedPower = Math.clamp(power, CAPACITY_POWER_MIN, CAPACITY_POWER_MAX);
        long result = (long) Math.max(0, baseValue) << clampedPower;
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    public static boolean isInfiniteStorageEnabled() {
        return enableInfiniteStorage;
    }
}
