package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
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

    private static final ForgeConfigSpec.BooleanValue ENABLE_ECO_AE2_FAST_PATH = BUILDER.comment(
                    "Enable the verified AE2-assisted fast path for ECO crafting workers.",
                    "Set JVM property -Dneoecoae.ecoFastPath=false to force-disable this optimization without editing the config.")
            .define("ecoAe2FastPathEnabled", true);

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

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength = 15;
    public static int computationSystemMaxLength = 15;
    public static int storageSystemMaxLength = 15;
    public static boolean postCraftingEvent;
    public static boolean enableEcoAe2FastPath;
    public static int craftingPatternBusPages = 2;
    public static boolean increaseStorageCellCapacity;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        syncValues();
    }

    public static void applyClientConfig(
            int craftingMaxLength,
            int computationMaxLength,
            int storageMaxLength,
            int patternBusPages,
            boolean increaseCapacity) {
        CRAFTING_SYSTEM_MAX_LENGTH.set(Math.max(CRAFTING_SYSTEM_MIN_LENGTH, craftingMaxLength));
        COMPUTATION_SYSTEM_MAX_LENGTH.set(Math.max(COMPUTATION_SYSTEM_MIN_LENGTH, computationMaxLength));
        STORAGE_SYSTEM_MAX_LENGTH.set(Math.max(STORAGE_SYSTEM_MIN_LENGTH, storageMaxLength));
        CRAFTING_PATTERN_BUS_PAGES.set(
                Math.max(PATTERN_BUS_MIN_PAGES, Math.min(PATTERN_BUS_MAX_PAGES, patternBusPages)));
        INCREASE_STORAGE_CELL_CAPACITY.set(increaseCapacity);
        SPEC.save();
        syncValues();
    }

    private static void syncValues() {
        craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.get();
        computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.get();
        storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
        enableEcoAe2FastPath = ENABLE_ECO_AE2_FAST_PATH.get();
        craftingPatternBusPages = CRAFTING_PATTERN_BUS_PAGES.get();
        increaseStorageCellCapacity = INCREASE_STORAGE_CELL_CAPACITY.get();
    }

    public static boolean isEcoAe2FastPathEnabled() {
        return enableEcoAe2FastPath && !"false".equalsIgnoreCase(System.getProperty("neoecoae.ecoFastPath", "true"));
    }

    public static boolean isIncreaseStorageCellCapacity() {
        return increaseStorageCellCapacity;
    }

    public static int getCraftingPatternBusPages() {
        return Math.max(PATTERN_BUS_MIN_PAGES, Math.min(PATTERN_BUS_MAX_PAGES, craftingPatternBusPages));
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
        return saturatedMultiply(fallbackBytes, 16L);
    }

    private static long saturatedMultiply(long value, long multiplier) {
        if (value <= 0L || multiplier <= 0L) {
            return 0L;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
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
