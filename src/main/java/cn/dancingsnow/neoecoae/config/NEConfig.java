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

    private static final ModConfigSpec.BooleanValue POST_CRAFTING_EVENT = BUILDER
        .comment(
            "Post a vanilla crafting event (ItemCraftedEvent) when the Crafting System finishes a recipe.",
            "May introduce extra event/listener overhead; can be more noticeable with mods like Balm installed."
        )
        .define("postCraftingEvent", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength;
    public static int computationSystemMaxLength;
    public static int storageSystemMaxLength;
    public static boolean postCraftingEvent;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.get();
        computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.get();
        storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
    }
}
