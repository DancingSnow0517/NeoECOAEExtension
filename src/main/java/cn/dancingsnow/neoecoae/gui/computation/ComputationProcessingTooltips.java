package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUITooltipComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

/** Compact machine details in the existing item tooltip. All times are machine ticks. */
final class ComputationProcessingTooltips {
    private ComputationProcessingTooltips() {}

    static HoverTooltips create(List<Component> prefix, ComputationCpuItemEntry item) {
        Component hint = Component.translatable("gui.neoecoae.processing.context_hint")
            .withStyle(ChatFormatting.DARK_GRAY);
        if (item.active().signum() <= 0 || item.processing().machineCount() == 0 && !item.processing().refreshing()) {
            var lines = new ArrayList<>(prefix);
            lines.add(hint);
            return new HoverTooltips(lines, null, null, null);
        }
        var rows = new ArrayList<Row>();
        prefix.stream().skip(1).forEach(line -> rows.add(text(line)));
        rows.addAll(rows(item.processing()));
        rows.add(text(hint));
        var client = Minecraft.getInstance();
        int maximum = Math.max(80, Math.min(260, client.getWindow().getGuiScaledWidth() - 20));
        return new HoverTooltips(List.of(prefix.isEmpty() ? Component.empty() : prefix.getFirst()),
            new ModularUITooltipComponent(new TooltipElement(rows, client.font, maximum)), null, null);
    }

    static List<Row> rows(ECOProcessingProgress progress) {
        if (progress.machineCount() == 0 && !progress.refreshing()) return List.of();
        var rows = new ArrayList<Row>();
        if (progress.machineCount() > 0) {
            rows.add(text(Component.translatable("gui.neoecoae.processing.count", progress.machineCount())));
        }
        if (progress.refreshing()) rows.add(text(Component.translatable("gui.neoecoae.processing.refreshing")
            .withStyle(ChatFormatting.GRAY)));
        if (progress.machineCount() > 0 && progress.machines().isEmpty()) {
            rows.add(text(Component.translatable("gui.neoecoae.processing.loading_details").withStyle(ChatFormatting.GRAY)));
        }
        var visible = progress.machines().stream().limit(ECOProcessingProgress.DETAIL_PAGE_SIZE).toList();
        for (var machine : visible) {
            rows.add(new Row(Component.empty(), Component.empty(), Kind.SEPARATOR, 0));
            ChatFormatting color = machine.state() == State.WAITING ? ChatFormatting.GOLD
                : machine.state() == State.RUNNING ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            rows.add(text(machine.name(), Component.translatable("gui.neoecoae.processing.state."
                + machine.state().name().toLowerCase(Locale.ROOT)).withStyle(color)));
            var reason = reason(machine.reason());
            if (!reason.getString().isBlank()) rows.add(text(reason.withStyle(ChatFormatting.GOLD)));
            Component parallel = machine.parallel() > 0
                ? Component.translatable("gui.neoecoae.processing.parallel", machine.parallel()) : Component.empty();
            Component cycle = machine.durationTicks() > 0 ? Component.translatable("gui.neoecoae.processing.cycle",
                (int) (100L * machine.progressTicks() / machine.durationTicks())) : Component.empty();
            if (machine.parallel() > 0 || machine.durationTicks() > 0) rows.add(text(parallel, cycle));
            if (machine.durationTicks() > 0) {
                rows.add(new Row(Component.empty(), Component.empty(), Kind.PROGRESS,
                    (float) machine.progressTicks() / machine.durationTicks()));
                rows.add(text(Component.translatable("gui.neoecoae.processing.time",
                    machine.progressTicks(), machine.durationTicks()), machine.state() == State.RUNNING
                    ? Component.translatable("gui.neoecoae.processing.remaining", machine.durationTicks() - machine.progressTicks())
                    : Component.empty()));
            }
            rows.add(text(location(machine.location()).withStyle(ChatFormatting.GRAY)));
        }
        int folded = progress.machineCount() - visible.size();
        if (folded > 0 && !visible.isEmpty()) {
            rows.add(new Row(Component.empty(), Component.empty(), Kind.SEPARATOR, 0));
            rows.add(text(Component.translatable("gui.neoecoae.processing.folded", folded).withStyle(ChatFormatting.GRAY)));
        }
        return rows;
    }

    /** Exact job matching is routine; keep blocking reasons and shared-machine warnings. */
    private static MutableComponent reason(Component source) {
        if (source.getContents() instanceof TranslatableContents translated
                && translated.getKey().equals("gui.neoecoae.processing.association.job")) return Component.empty();
        var result = MutableComponent.create(source.getContents()).setStyle(source.getStyle());
        for (var sibling : source.getSiblings()) result.append(reason(sibling));
        return result;
    }

    static MutableComponent location(String location) {
        int coordinates = location.indexOf(" [");
        if (coordinates < 0 || !location.endsWith("]")) return Component.literal(location);
        String dimension = location.substring(0, coordinates);
        String key = switch (dimension) {
            case "minecraft:overworld" -> "gui.neoecoae.processing.dimension.overworld";
            case "minecraft:the_nether" -> "gui.neoecoae.processing.dimension.nether";
            case "minecraft:the_end" -> "gui.neoecoae.processing.dimension.end";
            default -> null;
        };
        var label = key == null ? Component.literal(dimension) : Component.translatable(key);
        return label.append("  (" + location.substring(coordinates + 2, location.length() - 1) + ")");
    }

    private static Row text(Component left) { return text(left, Component.empty()); }
    private static Row text(Component left, Component right) { return new Row(left, right, Kind.TEXT, 0); }
    enum Kind { TEXT, SEPARATOR, PROGRESS }
    record Row(Component left, Component right, Kind kind, float progress) {}
    private record Layout(Row row, List<FormattedCharSequence> left, List<FormattedCharSequence> right, int height) {}

    /** LDLib2 supplies the normal tooltip background, placement, and component registration. */
    static final class TooltipElement extends UIElement {
        private static final int GAP = 12;
        private final List<Layout> lines = new ArrayList<>();
        private final Font font;
        private final int width;

        TooltipElement(List<Row> rows, Font font, int maximum) {
            this.font = font;
            int width = Math.min(180, maximum);
            for (var row : rows) {
                int right = font.width(row.right());
                width = Math.min(maximum, Math.max(width, font.width(row.left()) + (right == 0 ? 0 : GAP + right)));
            }
            this.width = width;
            int height = 0;
            for (var row : rows) {
                List<FormattedCharSequence> left = List.of(), right = List.of();
                int rowHeight = 6;
                if (row.kind() == Kind.TEXT) {
                    int rightWidth = Math.min(font.width(row.right()), width / 2);
                    left = font.split(row.left(), Math.max(1, width - (rightWidth == 0 ? 0 : GAP + rightWidth)));
                    right = rightWidth == 0 ? List.of() : font.split(row.right(), rightWidth);
                    rowHeight = Math.max(1, Math.max(left.size(), right.size())) * (font.lineHeight + 2);
                }
                lines.add(new Layout(row, left, right, rowHeight));
                height += rowHeight;
            }
            int tooltipHeight = height;
            layout(layout -> layout.width(this.width).height(tooltipHeight).paddingAll(0));
        }

        @Override public void drawBackgroundAdditional(GUIContext context) {
            int x = Math.round(getPositionX()), y = Math.round(getPositionY());
            for (var entry : lines) {
                if (entry.row().kind() == Kind.SEPARATOR) {
                    context.graphics.fill(x, y + 2, x + width, y + 3, 0xFF37303F);
                } else if (entry.row().kind() == Kind.PROGRESS) {
                    context.graphics.fill(x, y + 1, x + width, y + 3, 0xFF332E3B);
                    int filled = Math.round(width * entry.row().progress());
                    if (filled > 0) context.graphics.fill(x, y + 1, x + filled, y + 3, 0xFF60CD64);
                } else {
                    for (int i = 0; i < entry.left().size(); i++) {
                        context.graphics.drawString(font, entry.left().get(i), x, y + i * (font.lineHeight + 2), 0xFFEEEAF2, true);
                    }
                    for (int i = 0; i < entry.right().size(); i++) {
                        var line = entry.right().get(i);
                        context.graphics.drawString(font, line, x + width - font.width(line), y + i * (font.lineHeight + 2), 0xFFEEEAF2, true);
                    }
                }
                y += entry.height();
            }
        }
    }
}
