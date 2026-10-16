package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.core.content.ConfigDefinitions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public class NEConfig {
    public static final int PATTERN_BUS_SLOTS_PER_PAGE = ConfigDefinitions.PATTERN_BUS_SLOTS_PER_PAGE;
    public static final int PATTERN_BUS_MIN_PAGES = ConfigDefinitions.PATTERN_BUS_MIN_PAGES;
    public static final int PATTERN_BUS_MAX_PAGES = ConfigDefinitions.PATTERN_BUS_MAX_PAGES;
    public static final int CRAFTING_WORKER_BASE_CRAFTS = ConfigDefinitions.CRAFTING_WORKER_BASE_CRAFTS;

    /*
     * Spec defaults. Each one is used in its define call and as the initial value of the matching static mirror
     * field, so the two cannot drift apart before the config is loaded.
     */
    private static final int DEFAULT_CRAFTING_SYSTEM_MAX_LENGTH = ConfigDefinitions.entry("craftingSystemMaxLength").intDefault();
    private static final int DEFAULT_COMPUTATION_SYSTEM_MAX_LENGTH = ConfigDefinitions.entry("computationSystemMaxLength").intDefault();
    private static final int DEFAULT_STORAGE_SYSTEM_MAX_LENGTH = ConfigDefinitions.entry("storageSystemMaxLength").intDefault();
    private static final long DEFAULT_MEGA_BULK_AUTO_MARK_THRESHOLD = ConfigDefinitions.entry("megaBulkAutoMarkThreshold").longDefault();
    private static final boolean DEFAULT_POST_CRAFTING_EVENT = ConfigDefinitions.entry("postCraftingEvent").booleanDefault();
    private static final boolean DEFAULT_LARGE_WORKSTATION_COMPAT_RECIPES_ENABLED = ConfigDefinitions.entry("largeWorkstationCompatRecipesEnabled").booleanDefault();
    private static final int DEFAULT_CRAFTING_PATTERN_BUS_PAGES = ConfigDefinitions.entry("craftingPatternBusPages").intDefault();
    private static final int DEFAULT_ECO_FAST_PATH_CACHE_SIZE = ConfigDefinitions.entry("ecoFastPathCacheSize").intDefault();
    private static final int DEFAULT_ECO_PLANNING_MAX_MILLIS = ConfigDefinitions.entry("ecoPlanningMaxMillis").intDefault();
    private static final int DEFAULT_ECO_PLANNING_MAX_WORK = ConfigDefinitions.entry("ecoPlanningMaxWork").intDefault();
    private static final int DEFAULT_ECO_PLANNING_MAX_MEMORY_MIB = ConfigDefinitions.entry("ecoPlanningMaxMemoryMiB").intDefault();
    private static final boolean DEFAULT_ECO_PLANNING_STAGE_DEBUG = ConfigDefinitions.entry("ecoPlanningStageDebug").booleanDefault();
    private static final boolean DEFAULT_ECO_CRAFT_SUBMISSION_DEBUG = ConfigDefinitions.entry("ecoCraftSubmissionDebug").booleanDefault();
    private static final boolean DEFAULT_ECO_DISPATCH_WATCHDOG_DEBUG = ConfigDefinitions.entry("ecoDispatchWatchdogDebug").booleanDefault();
    private static final boolean DEFAULT_ECO_CRAFTING_OUTPUT_DELIVERY_DEBUG = ConfigDefinitions.entry("ecoCraftingOutputDeliveryDebug").booleanDefault();

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final java.util.Map<String, ModConfigSpec.ConfigValue<?>> VALUES = bind(BUILDER);

    private static final ModConfigSpec.IntValue CRAFTING_SYSTEM_MAX_LENGTH =
        (ModConfigSpec.IntValue) VALUES.get("craftingSystemMaxLength");
    private static final ModConfigSpec.IntValue COMPUTATION_SYSTEM_MAX_LENGTH =
        (ModConfigSpec.IntValue) VALUES.get("computationSystemMaxLength");
    private static final ModConfigSpec.IntValue STORAGE_SYSTEM_MAX_LENGTH =
        (ModConfigSpec.IntValue) VALUES.get("storageSystemMaxLength");
    private static final ModConfigSpec.LongValue MEGA_BULK_AUTO_MARK_THRESHOLD =
        (ModConfigSpec.LongValue) VALUES.get("megaBulkAutoMarkThreshold");
    private static final ModConfigSpec.BooleanValue POST_CRAFTING_EVENT =
        (ModConfigSpec.BooleanValue) VALUES.get("postCraftingEvent");
    private static final ModConfigSpec.BooleanValue LARGE_WORKSTATION_COMPAT_RECIPES_ENABLED =
        (ModConfigSpec.BooleanValue) VALUES.get("largeWorkstationCompatRecipesEnabled");
    private static final ModConfigSpec.IntValue CRAFTING_PATTERN_BUS_PAGES =
        (ModConfigSpec.IntValue) VALUES.get("craftingPatternBusPages");
    private static final ModConfigSpec.IntValue ECO_FAST_PATH_CACHE_SIZE =
        (ModConfigSpec.IntValue) VALUES.get("ecoFastPathCacheSize");
    private static final ModConfigSpec.IntValue ECO_PLANNING_MAX_MILLIS =
        (ModConfigSpec.IntValue) VALUES.get("ecoPlanningMaxMillis");
    private static final ModConfigSpec.IntValue ECO_PLANNING_MAX_WORK =
        (ModConfigSpec.IntValue) VALUES.get("ecoPlanningMaxWork");
    private static final ModConfigSpec.IntValue ECO_PLANNING_MAX_MEMORY_MIB =
        (ModConfigSpec.IntValue) VALUES.get("ecoPlanningMaxMemoryMiB");
    private static final ModConfigSpec.BooleanValue ECO_PLANNING_STAGE_DEBUG =
        (ModConfigSpec.BooleanValue) VALUES.get("ecoPlanningStageDebug");
    private static final ModConfigSpec.BooleanValue ECO_CRAFT_SUBMISSION_DEBUG =
        (ModConfigSpec.BooleanValue) VALUES.get("ecoCraftSubmissionDebug");
    private static final ModConfigSpec.BooleanValue ECO_DISPATCH_WATCHDOG_DEBUG =
        (ModConfigSpec.BooleanValue) VALUES.get("ecoDispatchWatchdogDebug");
    private static final ModConfigSpec.BooleanValue ECO_CRAFTING_OUTPUT_DELIVERY_DEBUG =
        (ModConfigSpec.BooleanValue) VALUES.get("ecoCraftingOutputDeliveryDebug");

    private static java.util.Map<String, ModConfigSpec.ConfigValue<?>> bind(ModConfigSpec.Builder builder) {
        var values = new java.util.LinkedHashMap<String, ModConfigSpec.ConfigValue<?>>();
        java.util.List<String> previous = java.util.List.of();
        for (var entry : ConfigDefinitions.entries()) {
            int common = 0;
            while (common < previous.size() && common < entry.group().size()
                    && previous.get(common).equals(entry.group().get(common))) common++;
            for (int i = previous.size(); i > common; i--) builder.pop();
            for (int i = common; i < entry.group().size(); i++) {
                var path = entry.group().subList(0, i + 1);
                var section = ConfigDefinitions.section(path);
                builder.comment(section.comments().toArray(String[]::new)).translation(section.translation())
                    .push(entry.group().get(i));
            }
            builder.comment(entry.comments().toArray(String[]::new)).translation(entry.translation());
            if (entry.worldRestart()) builder.worldRestart();
            ModConfigSpec.ConfigValue<?> value = switch (entry.kind()) {
                case INT -> builder.defineInRange(entry.key(), entry.intDefault(), (int) entry.minimum(),
                    (int) entry.maximum());
                case LONG -> builder.defineInRange(entry.key(), entry.longDefault(), entry.minimum(), entry.maximum());
                case BOOLEAN -> builder.define(entry.key(), entry.booleanDefault());
            };
            values.put(entry.key(), value);
            previous = entry.group();
        }
        for (int i = 0; i < previous.size(); i++) builder.pop();
        return java.util.Map.copyOf(values);
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
