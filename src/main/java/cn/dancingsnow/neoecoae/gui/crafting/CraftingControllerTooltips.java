package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Tooltip text and shared display calculations over the synchronized view. */
final class CraftingControllerTooltips {
    private final CraftingControllerViewState view;

    CraftingControllerTooltips(CraftingControllerViewState view) {
        this.view = view;
    }
    String percent(int permille) {
        return (Math.round(Math.clamp(permille, 0, CraftingControllerPanelState.FULL) / 10.0f)) + "%";
    }

    String formatCapacity(long value) {
        return value == Long.MAX_VALUE ? "∞" : Long.toString(Math.max(0L, value));
    }

    long parallelLimit() {
        return view.parallelLimit;
    }

    String overflowPercent() {
        return (view.ftParallelCapacity <= 0L ? 0L
            : Math.clamp(Math.round(view.overflowCapacity * 100.0D / view.ftParallelCapacity), 0L, 100L)) + "%";
    }

    List<Object> queueTooltip() {
        long count = Math.max(0L, view.queuedCraftCount);
        int permille = Math.clamp(view.state.queuePermille(), 0, CraftingControllerPanelState.FULL);
        boolean unbounded = view.batchCapacity == Long.MAX_VALUE;
        boolean full = !unbounded && view.batchCapacity > 0L && count >= view.batchCapacity;
        String state = full ? "full" : count == 0L ? "idle" : "active";
        int stateColor = full ? CraftingTooltipCard.WARN : count == 0L ? CraftingTooltipCard.MUTED : CraftingTooltipCard.CYAN;
        return List.of(CraftingTooltipCard.builder(CraftingTooltipCard.Icon.STACK,
                Component.translatable("gui.neoecoae.crafting.ui.queue_heading"))
            .headerRight(CraftingTooltipCard.upper(Component.translatable("gui.neoecoae.crafting.ui.queue_card." + state)),
                stateColor)
            .value(CraftingTooltipCard.group(count),
                unbounded ? "∞" : CraftingTooltipCard.group(Math.max(0L, view.batchCapacity)))
            .segment(permille / (double) CraftingControllerPanelState.FULL, CraftingTooltipCard.CYAN, false)
            .row(Component.translatable("gui.neoecoae.crafting.ui.queue_card.used").withColor(CraftingTooltipCard.MUTED),
                Component.literal(percent(permille)), full ? CraftingTooltipCard.WARN : CraftingTooltipCard.PRIMARY)
            .build());
    }

    List<Object> parallelTooltip() {
        var card = CraftingTooltipCard.builder(CraftingTooltipCard.Icon.CHIP,
            Component.translatable("gui.neoecoae.crafting.ui.parallel_heading"));
        Component runtime = CraftingTooltipCard.label("gui.neoecoae.crafting.ui.parallel_card.runtime",
            view.executionTicks + " t", CraftingTooltipCard.PRIMARY);
        if (view.virtualMode) {
            return List.of(card.value("∞", null)
                .status(Component.translatable("gui.neoecoae.crafting.ui.virtual_crafting"), CraftingTooltipCard.ACCENT)
                .segment(1.0D, CraftingTooltipCard.ACCENT, false)
                .row(runtime, Component.empty(), CraftingTooltipCard.PRIMARY)
                .build());
        }
        long total = Math.max(0L, view.ftParallelCapacity);
        long limit = Math.max(0L, parallelLimit());
        long overflow = Math.max(0L, view.overflowCapacity);
        boolean over = overflow > 0L;
        if (limit > 0L) {
            String multiple = total % limit == 0L ? CraftingTooltipCard.group(total / limit)
                : String.format(Locale.ROOT, "%.1f", (double) total / limit);
            card.headerRight(Component.translatable("gui.neoecoae.crafting.ui.parallel_card.multiple", multiple),
                CraftingTooltipCard.WARN);
        }
        card.value(CraftingTooltipCard.group(total), CraftingTooltipCard.group(limit))
            .status(Component.translatable(over ? "gui.neoecoae.crafting.ui.parallel_card.overflow"
                : "gui.neoecoae.crafting.ui.parallel_card.within"), over ? CraftingTooltipCard.WARN : CraftingTooltipCard.OK);
        if (over && total > 0L) {
            card.segment((total - overflow) / (double) total, CraftingTooltipCard.ACCENT, true)
                .segment(1.0D, CraftingTooltipCard.WARN, false);
        } else {
            card.segment(limit <= 0L ? (total > 0L ? 1.0D : 0.0D) : total / (double) limit,
                CraftingTooltipCard.ACCENT, false);
        }
        String ratio = total <= 0L ? "0.0%"
            : String.format(Locale.ROOT, "%.1f%%", Math.clamp(overflow * 100.0D / total, 0.0D, 100.0D));
        return List.of(card
            .row(CraftingTooltipCard.label("gui.neoecoae.crafting.ui.parallel_card.excess",
                    "+" + CraftingTooltipCard.group(overflow), over ? CraftingTooltipCard.WARN : CraftingTooltipCard.PRIMARY),
                Component.literal(ratio), over ? CraftingTooltipCard.WARN : CraftingTooltipCard.PRIMARY)
            .dividedRow(runtime, CraftingTooltipCard.label("gui.neoecoae.crafting.ui.parallel_card.overclock",
                    view.effectiveOverclockTimes + " / " + view.theoreticalOverclockTimes, CraftingTooltipCard.PRIMARY),
                CraftingTooltipCard.PRIMARY)
            .build());
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
