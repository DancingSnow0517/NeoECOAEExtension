package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.CpuSelectionMode;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard.Glyph;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingTooltipCard;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.utils.ModularUITooltipComponent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Card tooltips for the computation host's CPU rows and crafting-status items. */
final class ComputationCards {
    private static final String CARD = "gui.neoecoae.computation.ui.card.";
    private static final int SCHEDULED = 0xFFC9A6FF;

    private ComputationCards() {}

    static ModularUITooltipComponent cpu(ComputationCpuEntry entry) {
        return cpu(entry, Screen.hasShiftDown());
    }

    static ModularUITooltipComponent cpu(ComputationCpuEntry entry, boolean exact) {
        var card = CraftingListCard.builder()
            .header(Glyph.CHIP, ComputationCpuSelector.name(entry),
                CraftingTooltipCard.upper(ComputationCpuSelector.status(entry)), statusColor(entry.status()))
            .divider()
            .row(null, CraftingTooltipCard.MUTED, Component.translatable(CARD + "storage_capacity"),
                Component.literal(exact ? HostText.expandedNumber(entry.storage()) + " B"
                    : ComputationStatCards.bytes(entry.storage())), CraftingTooltipCard.PRIMARY)
            .row(null, CraftingTooltipCard.MUTED, Component.translatable(CARD + "co_processors"),
                Component.literal(exact ? CraftingTooltipCard.group(entry.parallel())
                    : ComputationStatCards.count(entry.parallel())), CraftingTooltipCard.ACCENT);
        if (entry.mode() != CpuSelectionMode.ANY) {
            card.text(entry.mode() == CpuSelectionMode.PLAYER_ONLY
                ? ButtonToolTips.CpuSelectionModePlayersOnly.text()
                : ButtonToolTips.CpuSelectionModeAutomationOnly.text(), Component.empty(), CraftingTooltipCard.MUTED);
        }
        if (entry.output() != null) {
            card.divider()
                .row(null, CraftingTooltipCard.MUTED, entry.output().getDisplayName(),
                    Component.literal(Math.round(entry.progress() * 100.0F) + "%"), CraftingTooltipCard.CYAN)
                .bar(entry.progress(), CraftingTooltipCard.CYAN)
                .text(Component.translatable("gui.neoecoae.cpu.remaining", entry.amount(entry.remaining(), exact)),
                    Component.empty(), CraftingTooltipCard.PRIMARY)
                .text(Component.translatable("gui.neoecoae.big_order.requested", entry.amount(entry.requested(), exact)),
                    Component.empty(), CraftingTooltipCard.PRIMARY)
                .text(ButtonToolTips.CpuStatusCraftedIn.text(Tooltips.ofPercent(entry.progress()),
                    Tooltips.ofDuration(entry.elapsed(), TimeUnit.NANOSECONDS)), Component.empty(), CraftingTooltipCard.MUTED);
        }
        if (!exact) card.divider().hint(Glyph.INFO, Component.translatable(CARD + "hold_shift"));
        return card.build();
    }

    /**
     * Item card for the crafting-status grid. {@code keyTooltip} is the key's normal tooltip: its first line is
     * the name, its last line the mod name, anything in between (such as the guide hint) moves to the footer.
     */
    static ModularUITooltipComponent item(List<Component> keyTooltip, ComputationCpuItemEntry item) {
        return item(keyTooltip, item, Screen.hasShiftDown());
    }

    static ModularUITooltipComponent item(List<Component> keyTooltip, ComputationCpuItemEntry item, boolean exact) {
        Component name = keyTooltip.isEmpty() ? item.key().getDisplayName() : keyTooltip.get(0);
        Component mod = keyTooltip.size() > 1 ? keyTooltip.get(keyTooltip.size() - 1) : Component.empty();
        var card = CraftingListCard.builder()
            .iconHeader((graphics, x, y) -> AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x, y, item.key()),
                name, mod)
            .divider();
        String state = item.stepStopped() ? "stopped" : item.active().signum() > 0 ? "crafting"
            : item.pending().signum() > 0 ? "scheduled" : "stored";
        int stateColor = item.stepStopped() ? CraftingTooltipCard.WARN : item.active().signum() > 0
            ? CraftingTooltipCard.OK : item.pending().signum() > 0 ? CraftingTooltipCard.CYAN : CraftingTooltipCard.MUTED;
        card.section(Component.translatable(CARD + "crafting_status"),
            CraftingTooltipCard.upper(Component.translatable(CARD + "state." + state)), stateColor);
        if (item.stored().signum() > 0) card.largeValue(Component.translatable(CARD + "stored"),
            item.amount(item.stored(), exact), CraftingTooltipCard.PRIMARY);
        if (item.active().signum() > 0) card.largeValue(Component.translatable(CARD + "crafting"),
            item.amount(item.active(), exact), CraftingTooltipCard.PRIMARY);
        if (item.pending().signum() > 0) card.largeValue(Component.translatable(CARD + "scheduled"),
            item.amount(item.pending(), exact), SCHEDULED);
        if (item.stepStopped()) {
            card.text(Component.translatable("gui.neoecoae.processing.step_stopped"), Component.empty(),
                CraftingTooltipCard.WARN);
        }

        card.divider();
        if (item.batched()) {
            card.hint(Glyph.STEPS, Component.translatable(CARD + "processing_step"))
                .note(Component.translatable("gui.neoecoae.crafting.smart_doubling"), CraftingTooltipCard.ACCENT);
        }
        var machines = ComputationProcessingTooltips.rows(item);
        if (item.active().signum() > 0 && machines.isEmpty()) {
            card.row(null, CraftingTooltipCard.MUTED, Component.translatable(CARD + "cycle_progress"),
                Component.translatable(CARD + "unavailable"), CraftingTooltipCard.MUTED);
        } else if (item.active().signum() <= 0 && machines.isEmpty() && item.pending().signum() > 0 && !item.stepStopped()) {
            card.text(Component.translatable("gui.neoecoae.processing.awaiting_dispatch"), Component.empty(),
                CraftingTooltipCard.MUTED);
        }
        for (var row : machines) {
            switch (row.kind()) {
                case SEPARATOR -> card.divider();
                case PROGRESS -> card.bar(row.progress(), CraftingTooltipCard.OK);
                case TEXT -> card.text(row.left(), row.right(), CraftingTooltipCard.PRIMARY);
            }
        }

        card.divider().hint(Glyph.POINTER, Component.translatable(CARD + "context_hint"));
        for (int i = 1; i < keyTooltip.size() - 1; i++) card.hint(Glyph.BOOK, keyTooltip.get(i));
        return card.build();
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "idle" -> CraftingTooltipCard.OK;
            case "running" -> CraftingTooltipCard.CYAN;
            case "suspended", "returning" -> CraftingTooltipCard.WARN;
            default -> CraftingListCard.DANGER;
        };
    }
}
