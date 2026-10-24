package cn.dancingsnow.neoecoae.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Loaded before server datapack discovery; recipes themselves are synchronized by Minecraft. */
public final class RecipeProfileConfig {
    public enum Profile {
        AUTO, STANDALONE, GTM, GTL
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.EnumValue<Profile> PROFILE = BUILDER
        .comment(
            "集成工作站制作路线：AUTO 自动识别（默认），也可手动指定 STANDALONE、GTM 或 GTL。",
            "AUTO：同时安装 gtceu 和 gtlcore 时使用 GTL；仅安装 gtceu 时使用 GTM；否则保留独立配方。",
            "GTM 要求 gtceu；GTL 要求 gtceu 和 gtlcore。只加载所选档案，替换对应旧配方。",
            "已有配置会保留原来的选择；需要自动识别时将 profile 改为 AUTO。",
            "此配置在服务端读取配方前加载；修改后完整重启游戏或服务器。客户端通过配方同步获取结果。",
            "AUTO (default) selects GTL with gtceu and gtlcore, GTM with gtceu alone, or STANDALONE otherwise.",
            "Explicit STANDALONE, GTM and GTL selections are preserved; set existing configs to AUTO to enable detection.",
            "GTM requires gtceu; GTL requires both gtceu and gtlcore. Restart the game/server after changing this.")
        .worldRestart()
        .defineEnum("profile", Profile.AUTO);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private RecipeProfileConfig() {}

    public static Profile profile() {
        return PROFILE.get();
    }
}
