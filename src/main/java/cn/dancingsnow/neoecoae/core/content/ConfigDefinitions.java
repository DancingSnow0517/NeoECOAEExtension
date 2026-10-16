package cn.dancingsnow.neoecoae.core.content;

import java.util.List;

/** Config paths, defaults, limits and documentation shared by every loader binding. */
public final class ConfigDefinitions {
    private ConfigDefinitions() {}
    public static final int PATTERN_BUS_SLOTS_PER_PAGE = 63;
    public static final int PATTERN_BUS_MIN_PAGES = 1;
    public static final int PATTERN_BUS_MAX_PAGES = 8;
    public static final int CRAFTING_WORKER_BASE_CRAFTS = 32;
    public static final int FAST_PATH_CACHE_MIN = 16;
    public static final int FAST_PATH_CACHE_MAX = 16_384;

    public enum Kind { INT, LONG, BOOLEAN }
    public record Section(List<String> path, String translation, List<String> comments) {}
    public record Entry(String key, List<String> group, Kind kind, Object defaultValue, long minimum, long maximum,
            String translation, List<String> comments, boolean worldRestart) {
        public int intDefault() { return (Integer) defaultValue; }
        public long longDefault() { return (Long) defaultValue; }
        public boolean booleanDefault() { return (Boolean) defaultValue; }
        public String path() { return group.isEmpty() ? key : String.join(".", group) + "." + key; }
    }

    private static final List<Section> SECTIONS = List.of(
        new Section(List.of("structure"), "neoecoae.configuration.structure", List.of(
                "多方块结构尺寸限制。",
                "Multiblock structure size limits.")),
        new Section(List.of("storage"), "neoecoae.configuration.storage", List.of(
                "ECO 存储系统选项。",
                "ECO storage system options.")),
        new Section(List.of("fastPath"), "neoecoae.configuration.fastPath", List.of(
                "ECO AE2 快速路径缓存与批量合成限制。",
                "快速路径默认启用；启用原版合成事件时会回退到逐次合成，以保留事件语义。",
                "ECO AE2 fast path cache and batch crafting limits.",
                "Fast Path is enabled by default and falls back to single-craft execution when Post Crafting Event is enabled.")),
        new Section(List.of("debug"), "neoecoae.configuration.debug", List.of(
                "仅用于排查问题的选项。正常游玩时建议保持关闭。",
                "Troubleshooting options. Keep these disabled during normal play.")),
        new Section(List.of("debug", "calculating"), "neoecoae.configuration.calculating", List.of(
                "ECO 合成计算与发配诊断。",
                "ECO crafting calculation and dispatch diagnostics."))
    );

    private static final List<Entry> ENTRIES = List.of(
        new Entry("craftingSystemMaxLength", List.of("structure"), Kind.INT, 15, 5, Integer.MAX_VALUE,
            "neoecoae.configuration.craftingSystemMaxLength", List.of(
                "合成子系统多方块结构允许的最大长度（单位：方块）。",
            "数值越大，可扩展的模块/组件越长，但结构检测开销也可能更高。",
            "Maximum length (in blocks) allowed for the Crafting System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost."), false),
        new Entry("computationSystemMaxLength", List.of("structure"), Kind.INT, 15, 5, Integer.MAX_VALUE,
            "neoecoae.configuration.computationSystemMaxLength", List.of(
                "计算子系统多方块结构允许的最大长度（单位：方块）。",
            "数值越大，可扩展的模块/组件越长，但结构检测开销也可能更高。",
            "Maximum length (in blocks) allowed for the Computation System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost."), false),
        new Entry("storageSystemMaxLength", List.of("structure"), Kind.INT, 15, 4, Integer.MAX_VALUE,
            "neoecoae.configuration.storageSystemMaxLength", List.of(
                "存储子系统多方块结构允许的最大长度（单位：方块）。",
            "数值越大，可扩展的模块/组件越长，但结构检测开销也可能更高。",
            "Maximum length (in blocks) allowed for the Storage System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost."), false),
        new Entry("megaBulkAutoMarkThreshold", List.of("storage"), Kind.LONG, 20_000L, 0L, Long.MAX_VALUE,
            "neoecoae.configuration.megaBulkAutoMarkThreshold", List.of(
                "ECO 存储主机自动标记可压缩物品时使用的数量阈值。",
            "只有数量严格大于此值的物品才会被标记。",
            "Amount threshold used when an ECO storage host automatically marks compressible items.",
            "Only items with amounts strictly greater than this value are marked."), false),
        new Entry("postCraftingEvent", List.of(), Kind.BOOLEAN, false, 0, 0,
            "neoecoae.configuration.postCraftingEvent", List.of(
                "当合成子系统完成一次配方时，是否触发原版的合成事件（ItemCraftedEvent）。",
            "开启后可能引入额外事件/监听开销；在安装 Balm 等模组时可能更明显。",
            "Post a vanilla crafting event (ItemCraftedEvent) when the Crafting System finishes a recipe.",
            "May introduce extra event/listener overhead; can be more noticeable with mods like Balm installed."), false),
        new Entry("largeWorkstationCompatRecipesEnabled", List.of(), Kind.BOOLEAN, false, 0, 0,
            "neoecoae.configuration.largeWorkstationCompatRecipesEnabled", List.of(
                "允许大型集成工作站处理受支持模组的联动配方，默认关闭。",
            "关闭时仅处理本模组的集成工作站配方。",
            "修改后重新进入世界或重启服务器，以刷新配方浏览器。",
            "Allow the large integrated workstation to process supported mods' recipes. Disabled by default.",
            "When disabled, only native integrated workstation recipes are available.",
            "Reopen the world or restart the server after changing this option to refresh recipe viewers."), true),
        new Entry("craftingPatternBusPages", List.of(), Kind.INT, 1, PATTERN_BUS_MIN_PAGES, PATTERN_BUS_MAX_PAGES,
            "neoecoae.configuration.craftingPatternBusPages", List.of(
                "每个 ECO 智能样板总线暴露的样板页数。",
            "每页可存储 63 个已编码样板。",
            "Number of pattern pages exposed by one ECO smart pattern bus.",
            "Each page stores 63 encoded patterns."), false),
        new Entry("ecoFastPathCacheSize", List.of("fastPath"), Kind.INT, 512, FAST_PATH_CACHE_MIN, FAST_PATH_CACHE_MAX,
            "neoecoae.configuration.ecoFastPathCacheSize", List.of(
                "每个 ECO 快速路径缓存最多保留的配方条目数量。",
            "Maximum recipe entries kept in each ECO fast path cache."), true),
        new Entry("ecoPlanningMaxMillis", List.of("fastPath"), Kind.INT, 10_000, 1, 600_000,
            "neoecoae.configuration.ecoPlanningMaxMillis", List.of(
                "一次 ECO 规划会话的总耗时预算（毫秒），由路线、启动种子和减少合成数量的探测共享。",
            "超出预算时显示未解。",
            "Shared cooperative time budget in milliseconds for one ECO planning session, including routes, startup seeds and craft-less probes.",
            "Exceeding the budget leaves the result unresolved."), false),
        new Entry("ecoPlanningMaxWork", List.of("fastPath"), Kind.INT, 5_000_000, 1, Integer.MAX_VALUE,
            "neoecoae.configuration.ecoPlanningMaxWork", List.of(
                "一次 ECO 规划会话的检查点总预算。",
            "切换路线或启动种子不会重置预算。",
            "Shared checkpoint work budget for one ECO planning session.",
            "Changing routes or startup seeds does not reset it."), false),
        new Entry("ecoPlanningMaxMemoryMiB", List.of("fastPath"), Kind.INT, 64, 1, Integer.MAX_VALUE,
            "neoecoae.configuration.ecoPlanningMaxMemoryMiB", List.of(
                "循环求解保留数据的估算内存预算（MiB），覆盖矩阵和搜索状态。",
            "超出预算时显示未解。",
            "Estimated retained memory budget in MiB for cycle matrices and search states.",
            "Exceeding the budget leaves the result unresolved."), false),
        new Entry("ecoPlanningStageDebug", List.of("debug", "calculating"), Kind.BOOLEAN, false, 0, 0,
            "neoecoae.configuration.ecoPlanningStageDebug", List.of(
                "记录 ECO 快速规划的每个计算阶段，包括非循环规划与循环规划。",
            "同时记录每次规划请求实际选择 ECO 快速规划还是 AE2 原版规划及原因。",
            "日志包含阶段耗时、成功状态、失败原因、目标物品和请求数量。",
            "Log every ECO fast-planning calculation stage, including acyclic and cyclic planning.",
            "Also logs whether each request selects ECO fast planning or the native AE2 planner, and why.",
            "Includes stage duration, success state, failure reason, target item and requested amount."), false),
        new Entry("ecoCraftSubmissionDebug", List.of("debug", "calculating"), Kind.BOOLEAN, false, 0, 0,
            "neoecoae.configuration.ecoCraftSubmissionDebug", List.of(
                "用于诊断合成确认界面的开始按钮无法启用或提交延迟/失败的问题。",
            "记录开始按钮状态、计划与 CPU 选择、提交路由和提交耗时。",
            "Diagnose why the crafting-confirm Start button is unavailable or why submission is delayed or fails.",
            "Includes Start-button state, plan and CPU selection, submission route and elapsed time."), false),
        new Entry("ecoDispatchWatchdogDebug", List.of("debug", "calculating"), Kind.BOOLEAN, false, 0, 0,
            "neoecoae.configuration.ecoDispatchWatchdogDebug", List.of(
                "记录 ECO 发配候选原因及发配前后的任务记账状态。",
            "连续 200 tick 没有实际进展时记录汇总诊断，之后每 1200 tick 再次记录。",
            "此选项只收集诊断信息，不会修改合成状态。",
            "Log ECO dispatch candidate reasons and task accounting before and after dispatch.",
            "Also logs an aggregated diagnostic after 200 ticks without real progress, then every 1200 ticks.",
            "This option only collects diagnostics and does not change crafting state."), false),
        new Entry("ecoCraftingOutputDeliveryDebug", List.of("debug"), Kind.BOOLEAN, false, 0, 0,
            "neoecoae.configuration.ecoCraftingOutputDeliveryDebug", List.of(
                "同一合成任务的 Worker 连续 200 tick 无法交付产物后记录首条汇总警告，之后每 1200 tick 再次记录。",
            "等待结束后会记录一条恢复信息。",
            "此选项只控制日志，不会改变产物所有权、重试或恢复逻辑。",
            "Log one aggregated warning per crafting job after its workers have been unable to deliver outputs for 200 ticks, then every 1200 ticks.",
            "A recovery message is logged when the wait ends.",
            "This option only controls logging and never changes output ownership, retry or recovery behavior."), false)
    );

    public static List<Entry> entries() { return ENTRIES; }
    public static List<Section> sections() { return SECTIONS; }
    public static Entry entry(String key) {
        return ENTRIES.stream().filter(entry -> entry.key().equals(key)).findFirst().orElseThrow();
    }
    public static Section section(List<String> path) {
        return SECTIONS.stream().filter(section -> section.path().equals(path)).findFirst().orElseThrow();
    }
}
