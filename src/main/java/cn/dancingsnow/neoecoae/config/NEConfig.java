package cn.dancingsnow.neoecoae.config;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public class NEConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER
            .comment(
                "Multiblock structure size limits."
            )
            .push("structure");
    }

    private static final ForgeConfigSpec.IntValue CRAFTING_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "Maximum length (in blocks) allowed for the Crafting System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost."
        )
        .defineInRange("craftingSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue COMPUTATION_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "Maximum length (in blocks) allowed for the Computation System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost."
        )
        .defineInRange("computationSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue STORAGE_SYSTEM_MAX_LENGTH = BUILDER
        .comment(
            "Maximum length (in blocks) allowed for the Storage System multiblock.",
            "Higher values allow longer expansions but may increase structure check cost."
        )
        .defineInRange("storageSystemMaxLength", 15, 4, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    private static final ForgeConfigSpec.BooleanValue POST_CRAFTING_EVENT = BUILDER
        .comment(
            "Post a vanilla crafting event (ItemCraftedEvent) when the Crafting System finishes a recipe.",
            "May introduce extra event/listener overhead; can be more noticeable with mods like Balm installed."
        )
        .define("postCraftingEvent", false);

    private static final ForgeConfigSpec.BooleanValue ENABLE_ECO_AE2_FAST_PATH = BUILDER
        .comment(
            "Enable the verified AE2-assisted fast path for ECO crafting workers.",
            "Set JVM property -Dneoecoae.ecoFastPath=false to force-disable this optimization without editing the config."
        )
        .define("enableEcoAe2FastPath", true);

    private static final ForgeConfigSpec.BooleanValue INCREASE_STORAGE_CELL_CAPACITY = BUILDER
        .comment(
            "Increase ECO Storage Matrix capacity.",
            "false keeps the old capacity.",
            "true changes ECO Storage Matrix capacity to L4=256MiB, L6=4GiB, L9=64GiB."
        )
        .define("increaseStorageCellCapacity", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength = 15;
    public static int computationSystemMaxLength = 15;
    public static int storageSystemMaxLength = 15;
    public static boolean postCraftingEvent;
    public static boolean enableEcoAe2FastPath;
    public static boolean increaseStorageCellCapacity;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.get();
        computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.get();
        storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
        enableEcoAe2FastPath = ENABLE_ECO_AE2_FAST_PATH.get();
        increaseStorageCellCapacity = INCREASE_STORAGE_CELL_CAPACITY.get();
    }

    public static boolean isEcoAe2FastPathEnabled() {
        return enableEcoAe2FastPath && !"false".equalsIgnoreCase(System.getProperty("neoecoae.ecoFastPath", "true"));
    }

    public static long getEcoStorageCellCapacity(IECOTier tier, long fallbackBytes) {
        if (!increaseStorageCellCapacity) {
            return fallbackBytes;
        }

        return switch (tier.getTier()) {
            case 1 -> 256L << 20;
            case 2 -> 4L << 30;
            case 3 -> 64L << 30;
            default -> fallbackBytes;
        };
    }
}
