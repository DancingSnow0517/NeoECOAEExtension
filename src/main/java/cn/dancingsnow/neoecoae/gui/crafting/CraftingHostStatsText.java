package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.api.me.CraftingCapabilitySnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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

    public static Component capability(CraftingCapabilitySnapshot state) {
        MutableComponent text = detailTitle().copy();
        text.append("\n").append(Component.translatable(
            "gui.neoecoae.crafting.capability.fx", state.activeFxCount(), state.physicalFxCount()));
        text.append("\n").append(Component.translatable(
            "gui.neoecoae.crafting.capability.network_composition",
            state.normalSwitchHosts(), state.highEnergySwitchHosts()));
        text.append("\n").append(Component.translatable(
            "gui.neoecoae.crafting.capability.network_multiplier", state.networkMultiplier()));
        text.append("\n").append(Component.translatable(
            "gui.neoecoae.crafting.capability.batch_per_fx", amount(state.batchPerFx())));
        text.append("\n").append(Component.translatable(
            "gui.neoecoae.crafting.capability.total", amount(state.totalBatchCapacity())));
        text.append("\n").append(Component.translatable(
            "gui.neoecoae.crafting.capability.ft_parallel", Tooltips.ofNumber(state.ftParallelCapacity())));
        text.append("\n").append(Component.translatable(
            "gui.neoecoae.crafting.capability.overclock",
            state.theoreticalOverclock(), state.effectiveOverclock()));
        return text;
    }

    private static Component amount(long value) {
        return Tooltips.ofNumber(Math.max(0L, value));
    }

    private static Component amount(CraftingCapabilitySnapshot.Capacity value) {
        return value.unlimited()
            ? Component.translatable("gui.neoecoae.storage.infinite_value")
            : amount(value.finiteValue());
    }
}
