package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
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
                        "ECO AE2 fast path cache and batch crafting options.",
                        "Disable or lower these values if a modpack has recipe compatibility issues.")
                .push("fastPath");
    }

    private static final ForgeConfigSpec.BooleanValue ENABLE_ECO_AE2_FAST_PATH = BUILDER.comment(
                    "Enable ECO AE2 fast path batch crafting cache.",
                    "This can greatly reduce repeated pattern execution cost. If recipe compatibility issues occur in a modpack, disable this option to fall back to the slow path.",
                    "Fast Path is automatically disabled when Post Crafting Event is enabled to preserve event semantics.",
                    "Set JVM property -Dneoecoae.ecoFastPath=false to force-disable this optimization without editing the config.")
            .define("ecoAe2FastPathEnabled", true);

    private static final ForgeConfigSpec.BooleanValue DEBUG_ECO_FAST_PATH = BUILDER.comment(
                    "Periodically log ECO fast path cache statistics.",
                    "Set JVM property -Dneoecoae.debugEcoFastPath=true to force-enable this without editing the config.")
            .define("debugEcoFastPath", false);

    private static final ForgeConfigSpec.IntValue ECO_CPU_PUSH_TICK_LIMIT = BUILDER.comment(
                    "Maximum normal crafting pattern pushes a CPU may attempt per tick.",
                    "The effective value is still capped by available co-processors.",
                    "Set JVM property -Dneoecoae.ecoCpuPushTickLimit=<value> to override this config.")
            .defineInRange("ecoCpuPushTickLimit", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue ECO_BATCH_FAST_PATH_LIMIT = BUILDER.comment(
                    "Maximum crafts merged into a single fast path batch push.",
                    "Set JVM property -Dneoecoae.ecoBatchFastPathLimit=<value> to override this config.")
            .defineInRange("ecoBatchFastPathLimit", 64, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue ECO_BATCH_FAST_PATH_TICK_LIMIT = BUILDER.comment(
                    "Maximum fast path batch crafts a CPU may push per tick.",
                    "Set JVM property -Dneoecoae.ecoBatchFastPathTickLimit=<value> to override this config.")
            .defineInRange("ecoBatchFastPathTickLimit", 256, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.BooleanValue ENABLE_ECO_AGGRESSIVE_FAST_PATH = BUILDER.comment(
                    "Enable the aggressive ECO fast path.",
                    "This keeps the same safety checks as the normal fast path, but allows much larger ECO Pattern Bus batch pushes.",
                    "Default false. Enable only after validating recipe behavior in the modpack.",
                    "Set JVM property -Dneoecoae.ecoAggressiveFastPath=true to force-enable this optimization without editing the config.")
            .define("ecoAggressiveFastPathEnabled", false);

    private static final ForgeConfigSpec.IntValue ECO_AGGRESSIVE_FAST_PATH_LIMIT = BUILDER.comment(
                    "Maximum crafts merged into a single aggressive fast path batch push.",
                    "Only used when ecoAggressiveFastPathEnabled is true.",
                    "Set JVM property -Dneoecoae.ecoAggressiveFastPathLimit=<value> to override this config.")
            .defineInRange("ecoAggressiveFastPathLimit", 4096, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue ECO_AGGRESSIVE_FAST_PATH_TICK_LIMIT = BUILDER.comment(
                    "Maximum aggressive fast path batch crafts a CPU may push per tick.",
                    "Only used when ecoAggressiveFastPathEnabled is true.",
                    "Set JVM property -Dneoecoae.ecoAggressiveFastPathTickLimit=<value> to override this config.")
            .defineInRange("ecoAggressiveFastPathTickLimit", 4096, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue ECO_FAST_PATH_CACHE_SIZE = BUILDER.comment(
                    "Maximum recipe entries kept in each ECO fast path cache.",
                    "Set JVM property -Dneoecoae.ecoFastPathCacheSize=<value> to override this config.",
                    "Changes fully apply to newly created caches after re-entering the world or restarting the server.")
            .defineInRange("ecoFastPathCacheSize", 512, 16, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    private static final ForgeConfigSpec.IntValue CRAFTING_PATTERN_BUS_PAGES = BUILDER.comment(
                    "Number of 63-slot pages available in each smart crafting pattern bus.",
                    "Range: 1-8. Changes are fully applied after re-entering the world or restarting the server.")
            .defineInRange("craftingPatternBusPages", 2, PATTERN_BUS_MIN_PAGES, PATTERN_BUS_MAX_PAGES);

    private static final ForgeConfigSpec.BooleanValue INCREASE_STORAGE_CELL_CAPACITY = BUILDER.comment(
                    "Increase ECO Storage Matrix capacity.",
                    "Defaults to true when GregTech Modern/GTCEu is loaded, otherwise false.",
                    "false keeps the old capacity.",
                    "true changes ECO Storage Matrix capacity to L4=256MiB, L6=4GiB, L9=64GiB and multiplies computation flash capacity by 16.",
                    "Changing this config is fully applied after re-entering the world or restarting the server.")
            .define("increaseStorageCellCapacity", DEFAULT_INCREASE_STORAGE_CELL_CAPACITY);

    private static final ForgeConfigSpec.BooleanValue ENABLE_INFINITE_STORAGE = BUILDER.comment(
                    "Enable the L9 infinite storage gameplay.",
                    "Default false. When disabled, the infinite component slot and new migrations are hidden/blocked.",
                    "Existing infinite storage domain files are preserved and are not deleted by this option.")
            .define("enableInfiniteStorage", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength = 15;
    public static int computationSystemMaxLength = 15;
    public static int storageSystemMaxLength = 15;
    public static boolean postCraftingEvent;
    public static boolean enableEcoAe2FastPath;
    public static boolean debugEcoFastPath;
    public static int ecoCpuPushTickLimit = Integer.MAX_VALUE;
    public static int ecoBatchFastPathLimit = 64;
    public static int ecoBatchFastPathTickLimit = 256;
    public static boolean enableEcoAggressiveFastPath;
    public static int ecoAggressiveFastPathLimit = 4096;
    public static int ecoAggressiveFastPathTickLimit = 4096;
    public static int ecoFastPathCacheSize = 512;
    public static int craftingPatternBusPages = 2;
    public static boolean increaseStorageCellCapacity;
    public static boolean enableInfiniteStorage;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        syncValues();
    }

    public static void applyClientConfig(
            int craftingMaxLength,
            int computationMaxLength,
            int storageMaxLength,
            int patternBusPages,
            boolean increaseCapacity,
            boolean aggressiveFastPath) {
        CRAFTING_SYSTEM_MAX_LENGTH.set(Math.max(CRAFTING_SYSTEM_MIN_LENGTH, craftingMaxLength));
        COMPUTATION_SYSTEM_MAX_LENGTH.set(Math.max(COMPUTATION_SYSTEM_MIN_LENGTH, computationMaxLength));
        STORAGE_SYSTEM_MAX_LENGTH.set(Math.max(STORAGE_SYSTEM_MIN_LENGTH, storageMaxLength));
        CRAFTING_PATTERN_BUS_PAGES.set(Mth.clamp(patternBusPages, PATTERN_BUS_MIN_PAGES, PATTERN_BUS_MAX_PAGES));
        INCREASE_STORAGE_CELL_CAPACITY.set(increaseCapacity);
        ENABLE_ECO_AGGRESSIVE_FAST_PATH.set(aggressiveFastPath);
        SPEC.save();
        syncValues();
    }

    private static void syncValues() {
        craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.get();
        computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.get();
        storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
        enableEcoAe2FastPath = ENABLE_ECO_AE2_FAST_PATH.get();
        debugEcoFastPath = getBooleanProperty("neoecoae.debugEcoFastPath", DEBUG_ECO_FAST_PATH.get());
        ecoCpuPushTickLimit = getPositiveIntProperty("neoecoae.ecoCpuPushTickLimit", ECO_CPU_PUSH_TICK_LIMIT.get());
        ecoBatchFastPathLimit =
                getPositiveIntProperty("neoecoae.ecoBatchFastPathLimit", ECO_BATCH_FAST_PATH_LIMIT.get());
        ecoBatchFastPathTickLimit =
                getPositiveIntProperty("neoecoae.ecoBatchFastPathTickLimit", ECO_BATCH_FAST_PATH_TICK_LIMIT.get());
        enableEcoAggressiveFastPath =
                getBooleanProperty("neoecoae.ecoAggressiveFastPath", ENABLE_ECO_AGGRESSIVE_FAST_PATH.get());
        ecoAggressiveFastPathLimit =
                getPositiveIntProperty("neoecoae.ecoAggressiveFastPathLimit", ECO_AGGRESSIVE_FAST_PATH_LIMIT.get());
        ecoAggressiveFastPathTickLimit = getPositiveIntProperty(
                "neoecoae.ecoAggressiveFastPathTickLimit", ECO_AGGRESSIVE_FAST_PATH_TICK_LIMIT.get());
        ecoFastPathCacheSize =
                Math.max(16, getPositiveIntProperty("neoecoae.ecoFastPathCacheSize", ECO_FAST_PATH_CACHE_SIZE.get()));
        craftingPatternBusPages = CRAFTING_PATTERN_BUS_PAGES.get();
        increaseStorageCellCapacity = INCREASE_STORAGE_CELL_CAPACITY.get();
        enableInfiniteStorage = ENABLE_INFINITE_STORAGE.get();
    }

    public static boolean isEcoAe2FastPathEnabled() {
        return enableEcoAe2FastPath
                && !postCraftingEvent
                && !"false".equalsIgnoreCase(System.getProperty("neoecoae.ecoFastPath", "true"));
    }

    public static boolean isEcoAggressiveFastPathEnabled() {
        return isEcoAe2FastPathEnabled() && enableEcoAggressiveFastPath;
    }

    public static int getEcoFastPathBatchLimit() {
        return isEcoAggressiveFastPathEnabled() ? ecoAggressiveFastPathLimit : ecoBatchFastPathLimit;
    }

    public static int getEcoFastPathTickLimit() {
        return isEcoAggressiveFastPathEnabled() ? ecoAggressiveFastPathTickLimit : ecoBatchFastPathTickLimit;
    }

    public static boolean isIncreaseStorageCellCapacity() {
        return increaseStorageCellCapacity;
    }

    public static boolean isInfiniteStorageEnabled() {
        return enableInfiniteStorage;
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

    private static boolean getBooleanProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int getPositiveIntProperty(String name, int fallback) {
        String value = System.getProperty(name);
        if (value == null) {
            return Math.max(1, fallback);
        }
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            LOGGER.warn("Ignoring invalid integer system property {}={}", name, value);
            return Math.max(1, fallback);
        }
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

        return switch (tier.getTier()) {
            case 1 -> 256L << 20;
            case 2 -> 4L << 30;
            case 3 -> 64L << 30;
            default -> fallbackBytes;
        };
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
