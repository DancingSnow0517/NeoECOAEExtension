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
            .comment(
                "Multiblock structure size limits."
            )
            .push("structure");
    }

    private static final ModConfigSpec.IntValue CRAFTING_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "Maximum length (in blocks) allowed for the Crafting System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost."
        )
        .defineInRange("craftingSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue COMPUTATION_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "Maximum length (in blocks) allowed for the Computation System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost."
        )
        .defineInRange("computationSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue STORAGE_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "Maximum length (in blocks) allowed for the Storage System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost."
        )
        .defineInRange("storageSystemMaxLength", 15, 4, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER
            .comment(
                "ECO crafting CPU scheduler limits.",
                "These caps keep large crafting jobs from monopolizing a single server tick."
            )
            .push("ecoCrafting");
    }

    private static final ModConfigSpec.IntValue ECO_CRAFTING_MAX_OPERATIONS_PER_TICK = BUILDER
        .comment(
            "Maximum number of ECO crafting scheduler operations allowed per tick.",
            "Lower values reduce worst-case tick time, higher values may improve throughput."
        )
        .defineInRange("maxOperationsPerTick", 32, 1, 4096);

    private static final ModConfigSpec.IntValue ECO_CRAFTING_MAX_PATTERNS_PER_TICK = BUILDER
        .comment(
            "Maximum number of crafting patterns an ECO CPU may dispatch in a single tick.",
            "Co-processors still improve throughput, but never beyond this cap."
        )
        .defineInRange("maxPatternsPerTick", 8, 1, 512);

    private static final ModConfigSpec.IntValue ECO_CRAFTING_MAX_PROVIDER_CHECKS_PER_TICK = BUILDER
        .comment(
            "Maximum provider checks an ECO CPU may perform in a single tick.",
            "This bounds provider scans when many pattern providers are present."
        )
        .defineInRange("maxProviderChecksPerTick", 64, 1, 4096);

    private static final ModConfigSpec.LongValue ECO_CRAFTING_TIME_BUDGET_NANOS = BUILDER
        .comment(
            "Approximate per-tick wall-clock budget for ECO crafting scheduling in nanoseconds.",
            "Once exceeded, remaining work is deferred to the next tick."
        )
        .defineInRange("timeBudgetNanos", 2_000_000L, 100_000L, 50_000_000L);

    private static final ModConfigSpec.IntValue ECO_CRAFTING_EFFECTIVE_COPROCESSOR_CAP = BUILDER
        .comment(
            "Maximum co-processors counted by the bounded ECO crafting scheduler.",
            "Raw co-processor count is clamped before throughput scaling is calculated."
        )
        .defineInRange("effectiveCoProcessorCap", 256, 1, 65_536);

    private static final ModConfigSpec.BooleanValue ECO_CRAFTING_DEBUG_PROFILING = BUILDER
        .comment(
            "Enable extra ECO crafting scheduler diagnostics in the log.",
            "Disabled by default to avoid log spam on production servers."
        )
        .define("debugProfiling", false);

    private static final ModConfigSpec.BooleanValue POST_CRAFTING_EVENT = BUILDER
        .comment(
            "Post a vanilla crafting event (ItemCraftedEvent) when the Crafting System finishes a recipe.",
            "May introduce extra event/listener overhead; can be more noticeable with mods like Balm installed."
        )
        .define("postCraftingEvent", false);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength;
    public static int computationSystemMaxLength;
    public static int storageSystemMaxLength;
    public static int ecoCraftingMaxOperationsPerTick;
    public static int ecoCraftingMaxPatternsPerTick;
    public static int ecoCraftingMaxProviderChecksPerTick;
    public static long ecoCraftingTimeBudgetNanos;
    public static int ecoCraftingEffectiveCoProcessorCap;
    public static boolean ecoCraftingDebugProfiling;
    public static boolean postCraftingEvent;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.get();
        computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.get();
        storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.get();
        ecoCraftingMaxOperationsPerTick = ECO_CRAFTING_MAX_OPERATIONS_PER_TICK.get();
        ecoCraftingMaxPatternsPerTick = ECO_CRAFTING_MAX_PATTERNS_PER_TICK.get();
        ecoCraftingMaxProviderChecksPerTick = ECO_CRAFTING_MAX_PROVIDER_CHECKS_PER_TICK.get();
        ecoCraftingTimeBudgetNanos = ECO_CRAFTING_TIME_BUDGET_NANOS.get();
        ecoCraftingEffectiveCoProcessorCap = ECO_CRAFTING_EFFECTIVE_COPROCESSOR_CAP.get();
        ecoCraftingDebugProfiling = ECO_CRAFTING_DEBUG_PROFILING.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
    }
}
