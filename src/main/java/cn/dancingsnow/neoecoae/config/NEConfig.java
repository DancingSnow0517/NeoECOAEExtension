package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import com.google.common.math.LongMath;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public class NEConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int CRAFTING_SYSTEM_MIN_LENGTH = 5;
    private static final int COMPUTATION_SYSTEM_MIN_LENGTH = 5;
    private static final int STORAGE_SYSTEM_MIN_LENGTH = 4;
    public static final int PATTERN_BUS_SLOTS_PER_PAGE = 63;
    public static final int PATTERN_BUS_MIN_PAGES = 1;
    public static final int PATTERN_BUS_MAX_PAGES = 8;
    public static final int ECO_CPU_PUSH_TICK_LIMIT_MAX = 393_216;
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final boolean DEFAULT_INCREASE_STORAGE_CELL_CAPACITY = isGtmLoaded();

    static {
        BUILDER.comment("Multiblock structure size limits.").push("structure");
    }

    private static final ForgeConfigSpec.IntValue CRAFTING_SYSTEM_MAX_LENGTH = BUILDER.comment(
                    "Maximum length (in blocks) allowed for the Crafting System multiblock.",
                    "Higher values allow longer expansions but may increase structure check cost.")
            .defineInRange("craftingSystemMaxLength", 15, CRAFTING_SYSTEM_MIN_LENGTH, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue COMPUTATION_SYSTEM_MAX_LENGTH = BUILDER.comment(
                    "Maximum length (in blocks) allowed for the Computation System multiblock.",
                    "Higher values allow longer expansions but may increase structure check cost.")
            .defineInRange("computationSystemMaxLength", 15, COMPUTATION_SYSTEM_MIN_LENGTH, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue STORAGE_SYSTEM_MAX_LENGTH = BUILDER.comment(
                    "Maximum length (in blocks) allowed for the Storage System multiblock.",
                    "Higher values allow longer expansions but may increase structure check cost.")
            .defineInRange("storageSystemMaxLength", 15, STORAGE_SYSTEM_MIN_LENGTH, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    private static final ForgeConfigSpec.BooleanValue POST_CRAFTING_EVENT = BUILDER.comment(
                    "Post a vanilla crafting event (ItemCraftedEvent) when the Crafting System finishes a recipe.",
                    "May introduce extra event/listener overhead; can be more noticeable with mods like Balm installed.")
            .define("postCraftingEvent", false);

    static {
        BUILDER.comment(
                        "ECO crafting planner diagnostic options.",
                        "Enable these only while investigating crafting-plan problems.")
                .push("debug");
    }

    private static final ForgeConfigSpec.BooleanValue DEBUG_ECO_PLANNER = BUILDER.comment(
                    "Log the stage, reason, request, solver state and fallback details for ECO planning failures.",
                    "Messages are deduplicated and rate-limited; enable only while diagnosing planning issues.",
                    "Set JVM property -Dneoecoae.debugECOPlanner=true to force-enable this without editing the config.")
            .define("debugECOPlanner", false);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment(
                        "ECO AE2 fast path cache and batch crafting diagnostics.",
                        "Fast Path is always enabled unless Post Crafting Event requires slow-path event semantics.")
                .push("fastPath");
    }

    private static final ForgeConfigSpec.BooleanValue DEBUG_ECO_FAST_PATH = BUILDER.comment(
                    "Log Fast Path cache statistics and fallback diagnostics.",
                    "Enable only while diagnosing Fast Path compatibility problems.")
            .define("debugEcoFastPath", false);

    private static final ForgeConfigSpec.IntValue ECO_CPU_PUSH_TICK_LIMIT = BUILDER.comment(
                    "Maximum crafting operations a CPU may schedule per tick.",
                    "This is a safety cap for both batch fast paths and regular paths; the effective value is still limited by available co-processors.")
            .defineInRange("ecoCpuPushTickLimit", ECO_CPU_PUSH_TICK_LIMIT_MAX, 1, ECO_CPU_PUSH_TICK_LIMIT_MAX);

    private static final ForgeConfigSpec.IntValue ECO_FAST_PATH_CACHE_SIZE = BUILDER.comment(
                    "Maximum recipe entries kept in each ECO fast path cache.",
                    "Set JVM property -Dneoecoae.ecoFastPathCacheSize=<value> to override this config.",
                    "Changes fully apply to newly created caches after re-entering the world or restarting the server.")
            .defineInRange(
                    "ecoFastPathCacheSize",
                    512,
                    ECOCraftingFastPathCache.MIN_CACHE_SIZE,
                    ECOCraftingFastPathCache.MAX_CACHE_SIZE);

    static {
        BUILDER.pop();
    }

    private static final ForgeConfigSpec.IntValue CRAFTING_PATTERN_BUS_PAGES = BUILDER.comment(
                    "Number of 63-slot pages available in each smart crafting pattern bus.",
                    "Range: 1-8. Changes are fully applied after re-entering the world or restarting the server.")
            .defineInRange("craftingPatternBusPages", 1, PATTERN_BUS_MIN_PAGES, PATTERN_BUS_MAX_PAGES);

    private static final ForgeConfigSpec.BooleanValue INCREASE_STORAGE_CELL_CAPACITY = BUILDER.comment(
                    "Increase ECO Storage Matrix capacity.",
                    "Defaults to true when GregTech Modern/GTCEu is loaded, otherwise false.",
                    "false keeps the old capacity.",
                    "true changes ECO Storage Matrix capacity to L4=256MiB, L6=4GiB, L9=64GiB and multiplies computation flash capacity by 16.",
                    "Changing this config is fully applied after re-entering the world or restarting the server.")
            .define("increaseStorageCellCapacity", DEFAULT_INCREASE_STORAGE_CELL_CAPACITY);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength = 15;
    public static int computationSystemMaxLength = 15;
    public static int storageSystemMaxLength = 15;
    public static boolean postCraftingEvent;
    public static boolean debugEcoFastPath;
    public static boolean debugECOPlanner;
    public static int ecoCpuPushTickLimit = ECO_CPU_PUSH_TICK_LIMIT_MAX;
    public static int ecoFastPathCacheSize = 512;
    public static int craftingPatternBusPages = 1;
    public static boolean increaseStorageCellCapacity;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        syncValues();
    }

    private static void syncValues() {
        craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.get();
        computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.get();
        storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
        debugEcoFastPath = DEBUG_ECO_FAST_PATH.get();
        debugECOPlanner = DEBUG_ECO_PLANNER.get();
        ecoCpuPushTickLimit = ECO_CPU_PUSH_TICK_LIMIT.get();
        ecoFastPathCacheSize = ECO_FAST_PATH_CACHE_SIZE.get();
        craftingPatternBusPages = CRAFTING_PATTERN_BUS_PAGES.get();
        increaseStorageCellCapacity = INCREASE_STORAGE_CELL_CAPACITY.get();
    }

    public static boolean isEcoAe2FastPathEnabled() {
        return !postCraftingEvent;
    }

    public static int getEcoFastPathTickLimit() {
        return ECOBatchCraftingHelper.MAX_BATCH_SIZE;
    }

    public static boolean isIncreaseStorageCellCapacity() {
        return increaseStorageCellCapacity;
    }

    public static boolean isInfiniteStorageEnabled() {
        return true;
    }

    public static int getCraftingPatternBusPages() {
        return Mth.clamp(craftingPatternBusPages, PATTERN_BUS_MIN_PAGES, PATTERN_BUS_MAX_PAGES);
    }

    public static int getCraftingPatternBusSlotCount() {
        return PATTERN_BUS_SLOTS_PER_PAGE * getCraftingPatternBusPages();
    }

    public static int getMaxCraftingPatternBusSlotCount() {
        return PATTERN_BUS_SLOTS_PER_PAGE * PATTERN_BUS_MAX_PAGES;
    }

    public static long getEcoStorageCellCapacity(IECOTier tier, long fallbackBytes) {
        return getEcoStorageCellCapacity(tier, fallbackBytes, increaseStorageCellCapacity);
    }

    public static long getExpandedEcoStorageCellCapacity(IECOTier tier, long fallbackBytes) {
        return getEcoStorageCellCapacity(tier, fallbackBytes, true);
    }

    private static long getEcoStorageCellCapacity(IECOTier tier, long fallbackBytes, boolean increaseCapacity) {
        if (!increaseCapacity) {
            return fallbackBytes;
        }

        long expandedBaseBytes =
                switch (tier.getTier()) {
                    case 1 -> 256L << 20;
                    case 2 -> 4L << 30;
                    case 3 -> 64L << 30;
                    default -> fallbackBytes;
                };
        long baseBytes = tier.getStorageTotalBytes();
        if (baseBytes <= 0L || fallbackBytes == baseBytes) {
            return expandedBaseBytes;
        }
        return LongMath.saturatedMultiply(expandedBaseBytes, fallbackBytes / baseBytes);
    }

    public static long getEcoComputationCellCapacity(IECOTier tier, long fallbackBytes) {
        if (!increaseStorageCellCapacity) {
            return fallbackBytes;
        }
        return LongMath.saturatedMultiply(Math.max(0L, fallbackBytes), 16L);
    }

    private static boolean isGtmLoaded() {
        try {
            return ModList.get().isLoaded("gtceu")
                    || ModList.get().isLoaded("gtm")
                    || ModList.get().isLoaded("gregtech");
        } catch (RuntimeException | LinkageError e) {
            LOGGER.debug("Unable to detect GregTech-compatible mods while initializing ECO config defaults.", e);
            return false;
        }
    }
}
