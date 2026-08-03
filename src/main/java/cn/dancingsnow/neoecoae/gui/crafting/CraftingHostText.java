package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.core.localization.Tooltips;
import net.minecraft.network.chat.Component;

/** Formatting and overflow-safe arithmetic for crafting host statistics. */
final class CraftingHostText {
    private CraftingHostText() {
    }

    static Component hostBatchLine(boolean highEnergy, int threads, long batch) {
        int color = highEnergy ? CraftingHostStyles.PANEL_HOST_HIGH_ENERGY : CraftingHostStyles.PANEL_HOST_NORMAL;
        return Component.translatable(
                "gui.neoecoae.host.crafting.host_line",
                Component.translatable(
                        highEnergy
                                ? "gui.neoecoae.host.crafting.host_type.high_energy"
                                : "gui.neoecoae.host.crafting.host_type.normal")
                        .withColor(color),
                threads,
                infiniteAwareAmount(batch))
                .withColor(color);
    }

    static Component infiniteAwareAmount(long amount) {
        return amount == Long.MAX_VALUE
                ? Component.translatable("gui.neoecoae.storage.infinite_value")
                : Tooltips.ofNumber(amount);
    }

    static long saturatingBatchTotal(long total, int threads, long batch) {
        if (total == Long.MAX_VALUE || batch == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long contribution = threads <= 0 || batch <= 0
                ? 0
                : batch > Long.MAX_VALUE / threads ? Long.MAX_VALUE : batch * threads;
        return contribution == Long.MAX_VALUE || total > Long.MAX_VALUE - contribution
                ? Long.MAX_VALUE
                : total + contribution;
    }

    static String recipeTimeMultiplier(int effectiveOverclockTimes) {
        int level = Math.clamp(effectiveOverclockTimes, 0, 9);
        int ticks = (int) Math.ceil(10.0D / (level + 1));
        return String.format(java.util.Locale.ROOT, "%.1fx", ticks / 10.0D);
    }
}
