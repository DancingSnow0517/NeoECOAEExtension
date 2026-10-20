package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.api.me.network.CraftingCapabilitySnapshot;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Tooltip text and shared display calculations over the synchronized view. */
final class CraftingControllerTooltips {
    private final CraftingControllerViewState view;

    CraftingControllerTooltips(CraftingControllerViewState view) {
        this.view = view;
    }
    String percent(int permille) {
        return (Math.round(JdkCompat.clamp(permille, 0, CraftingControllerPanelState.FULL) / 10.0f)) + "%";
    }

    String formatCapacity(long value) {
        return value == Long.MAX_VALUE ? "∞" : Long.toString(Math.max(0L, value));
    }

    long parallelLimit() {
        return view.workerCores * CraftingCapabilitySnapshot.BASE_BATCH_PER_FX;
    }

    String overflowPercent() {
        return (view.ftParallelCapacity <= 0L ? 0L
            : JdkCompat.clamp(Math.round(view.overflowCapacity * 100.0D / view.ftParallelCapacity), 0L, 100L)) + "%";
    }

    List<Component> queueTooltip() {
        return List.of(Component.translatable("gui.neoecoae.crafting.ui.queue",
                HostText.usedPart(Long.toString(view.queuedCraftCount), view.queuedCraftCount, view.batchCapacity),
                HostText.totalPart(formatCapacity(view.batchCapacity))),
            HostText.usedOfTotal(HostText.usedPart(Long.toString(view.queuedCraftCount), view.queuedCraftCount,
                view.batchCapacity), " / ", HostText.totalPart(formatCapacity(view.batchCapacity))),
            Component.translatable("gui.neoecoae.crafting.ui.queue_percent", percent(view.state.queuePermille())));
    }

    List<Component> parallelTooltip() {
        return List.of(Component.translatable("gui.neoecoae.crafting.ui.total_parallel", view.ftParallelCapacity),
            Component.translatable("gui.neoecoae.crafting.ui.parallel_limit", parallelLimit()),
            Component.translatable("gui.neoecoae.crafting.ui.parallel_overflow", view.overflowCapacity, overflowPercent()),
            Component.translatable("gui.neoecoae.crafting.ui.runtime_ticks", view.executionTicks),
            Component.translatable("gui.neoecoae.crafting.ui.runtime",
                view.effectiveOverclockTimes, view.theoreticalOverclockTimes));
    }

    List<Component> energyTooltip() {
        return List.of(Component.translatable("gui.neoecoae.crafting.ui.energy_usage"),
            Tooltips.ofNumber(view.energyUsage).append(" AE/t"),
            Tooltips.ofPercent(view.state.energyPermille() / (double) CraftingControllerPanelState.FULL));
    }

    List<Component> coolantTooltip() {
        return List.of(Component.translatable("gui.neoecoae.host.crafting.coolant"),
            coolantVisible() ? view.coolantFluid.getHoverName() : Component.translatable("ldlib.fluid.empty"),
            HostText.usedOfTotal(HostText.usedPart(HostText.expandedNumber(view.coolantAmount), view.coolantAmount,
                view.coolantCapacity), " / ", HostText.totalPart(HostText.expandedNumber(view.coolantCapacity) + " mB")),
            Component.translatable("gui.neoecoae.crafting.ui.coolant_overclock", Math.max(0, view.coolantMaxOverclock)));
    }

    List<Component> outputTooltip() {
        return List.of(Component.translatable("gui.neoecoae.crafting.ui.cooling_output"),
            view.outputFluid.isEmpty() ? Component.translatable("ldlib.fluid.empty") : view.outputFluid.getHoverName(),
            HostText.usedOfTotal(HostText.usedPart(HostText.expandedNumber(view.outputFluid.getAmount()),
                view.outputFluid.getAmount(), view.outputCapacity), " / ",
                HostText.totalPart(HostText.expandedNumber(view.outputCapacity) + " mB")));
    }

    List<Component> fxTooltip() {
        return List.of(Component.translatable("gui.neoecoae.crafting.ui.fx_active", view.activeWorkerCores, view.workerCores),
            Component.empty(),
            Component.translatable("gui.neoecoae.crafting.ui.ft_parallel", view.ftParallelCapacity),
            Component.empty(),
            Component.translatable("gui.neoecoae.crafting.ui.batch_capacity", formatCapacity(view.batchCapacity)));
    }

    List<Component> coolantRowTooltip() {
        return coolantTooltip();
    }

    List<Component> switchTooltip() {
        return List.of(Component.translatable("gui.neoecoae.crafting.ui.switch_detail",
                view.normalSwitchCount, view.highEnergySwitchCount),
            Component.translatable("gui.neoecoae.crafting.ui.network_multiplier", view.networkMultiplier));
    }

    boolean coolantVisible() {
        return CraftingControllerPanelState.coolantVisible(view.activeCooling, view.coolantFluid, view.coolantAmount);
    }
}
