package cn.dancingsnow.neoecoae.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Loaded before server datapack discovery; recipes themselves are synchronized by Minecraft. */
public final class RecipeProfileConfig {
    public enum Profile {
        STANDALONE, GTM, GTL
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.EnumValue<Profile> PROFILE = BUILDER
        .comment(
            "集成工作站制作路线：STANDALONE 保留独立配方（默认），GTM 使用 GTM 路线，GTL 使用 GTL 路线。",
            "GTM 要求 gtceu；GTL 要求 gtceu 和 gtlcore。只加载所选档案，替换对应旧配方。",
            "此配置在服务端读取配方前加载；修改后完整重启游戏或服务器。客户端通过配方同步获取结果。",
            "STANDALONE keeps the original recipes (default); GTM and GTL select exclusive GT progression recipes.",
            "GTM requires gtceu; GTL requires both gtceu and gtlcore. Restart the game/server after changing this.")
        .worldRestart()
        .defineEnum("profile", Profile.STANDALONE);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private RecipeProfileConfig() {}

    public static Profile profile() {
        return PROFILE.get();
    }
}
