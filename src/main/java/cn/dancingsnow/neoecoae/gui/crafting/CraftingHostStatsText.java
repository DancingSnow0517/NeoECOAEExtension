package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.core.localization.Tooltips;
import net.minecraft.network.chat.Component;

/** Exact text and colors used by the network-switch crafting statistics tooltip. */
public final class CraftingHostStatsText {
    private static final int MUTED = 0xFFC7BFCD;
    private static final int VALUE = 0xFF8377FF;
    private static final int NORMAL_HOST = 0xFF8FE3A0;
    private static final int HIGH_ENERGY_HOST = 0xFFFFB469;

    private CraftingHostStatsText() {
    }

    public static Component detailTitle() {
        return Component.translatable("gui.neoecoae.crafting.ui.batch_per_thread.detail").withColor(MUTED);
    }

    public static Component hostLine(boolean highEnergy, int threads, long batch) {
        int color = highEnergy ? HIGH_ENERGY_HOST : NORMAL_HOST;
        return Component.translatable(
            "gui.neoecoae.host.crafting.host_line",
            Component.translatable(highEnergy
                ? "gui.neoecoae.host.crafting.host_type.high_energy"
                : "gui.neoecoae.host.crafting.host_type.normal").withColor(color),
            threads,
            amount(batch)
        ).withColor(color);
    }

    public static Component totalLine(long total) {
        return Component.translatable(
            "gui.neoecoae.crafting.ui.batch_per_thread.total",
            amount(total).copy().withColor(VALUE)
        ).withColor(MUTED);
    }

    private static Component amount(long value) {
        return value == Long.MAX_VALUE || value >= Integer.MAX_VALUE
            ? Component.translatable("gui.neoecoae.storage.infinite_value")
            : Tooltips.ofNumber(Math.max(0L, value));
    }
}
