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
                        "多方块结构尺寸限制。")
                .push("structure");
    }

    private static final ModConfigSpec.IntValue CRAFTING_SYSTEM_MAX_LENGTH = BUILDER
            .comment(
                    "合成系统多方块结构允许的最大长度（以方块计）。",
                    "更高的值允许更长的扩展，但可能增加结构检查开销。")
            .defineInRange("craftingSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue COMPUTATION_SYSTEM_MAX_LENGTH = BUILDER
            .comment(
                    "运算系统多方块结构允许的最大长度（以方块计）。",
                    "更高的值允许更长的扩展，但可能增加结构检查开销。")
            .defineInRange("computationSystemMaxLength", 15, 5, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue STORAGE_SYSTEM_MAX_LENGTH = BUILDER
            .comment(
                    "存储系统多方块结构允许的最大长度（以方块计）。",
                    "更高的值允许更长的扩展，但可能增加结构检查开销。")
            .defineInRange("storageSystemMaxLength", 15, 4, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    private static final ModConfigSpec.BooleanValue POST_CRAFTING_EVENT = BUILDER
            .comment(
                    "合成系统完成配方时发送原版合成事件（ItemCraftedEvent）。",
                    "可能引入额外的事件/监听器开销；安装 Balm 等模组时可能会有较明显影响。")
            .define("postCraftingEvent", false);

    private static final ModConfigSpec.BooleanValue ECO_AE2_FAST_PATH_ENABLED = BUILDER
            .comment(
                    "启用 ECO AE2 快速路径批量合成缓存。",
                    "可大幅减少重复 pattern 执行开销；如遇到特定整合包配方兼容问题，可关闭此选项回退到慢速路径。",
                    "启用原版合成事件 postCraftingEvent 时，FastPath 会自动禁用以保留事件语义。")
            .define("ecoAe2FastPathEnabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int craftingSystemMaxLength;
    public static int computationSystemMaxLength;
    public static int storageSystemMaxLength;
    public static boolean postCraftingEvent;
    public static boolean ecoAe2FastPathEnabled;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        craftingSystemMaxLength = CRAFTING_SYSTEM_MAX_LENGTH.get();
        computationSystemMaxLength = COMPUTATION_SYSTEM_MAX_LENGTH.get();
        storageSystemMaxLength = STORAGE_SYSTEM_MAX_LENGTH.get();
        postCraftingEvent = POST_CRAFTING_EVENT.get();
        ecoAe2FastPathEnabled = ECO_AE2_FAST_PATH_ENABLED.get();
    }

    public static boolean isEcoAe2FastPathEnabled() {
        return ecoAe2FastPathEnabled;
    }
}
