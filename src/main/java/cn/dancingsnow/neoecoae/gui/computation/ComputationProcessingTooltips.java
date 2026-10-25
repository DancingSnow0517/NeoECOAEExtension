package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib.gui.util.WidgetTooltipComponent;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Compact machine details in the existing item tooltip. All times are machine ticks. */
final class ComputationProcessingTooltips {
    private ComputationProcessingTooltips() {}

    static HoverTooltips create(List<Component> prefix, ComputationCpuItemEntry item) {
        var rows = new ArrayList<Row>();
        prefix.stream().skip(1).forEach(line -> rows.add(text(line)));
        if (item.active().signum() > 0 && item.processing().machineCount() > 0) {
            rows.addAll(rows(item.processing()));
        } else {
            describe(item).forEach(line -> rows.add(text(line)));
        }
        rows.add(text(Component.translatable("gui.neoecoae.processing.context_hint")
            .withStyle(ChatFormatting.DARK_GRAY)));
        return new HoverTooltips(List.of(prefix.isEmpty() ? Component.empty() : prefix.get(0)),
            new WidgetTooltipComponent(new TooltipWidget(rows)), null, null);
    }

    static List<Component> describe(ComputationCpuItemEntry item) {
        if (item.active().signum() <= 0) {
            return item.pending().signum() > 0 && !item.stepStopped() ? List.of(Component.translatable(
                "gui.neoecoae.processing.awaiting_dispatch").withStyle(ChatFormatting.GRAY)) : List.of();
        }
        var lines = describe(item.processing());
        if (!lines.isEmpty()) return lines;
        return List.of(Component.translatable("gui.neoecoae.processing.progress_unavailable").withStyle(ChatFormatting.GRAY));
    }

    static List<Component> describe(ECOProcessingProgress progress) {
        var lines = new ArrayList<Component>();
        for (var row : rows(progress)) {
            if (row.kind() != Kind.TEXT) continue;
            lines.add(row.right().getString().isEmpty() ? row.left()
                : row.left().copy().append("    ").append(row.right()));
        }
        return lines;
    }

    private static List<Row> rows(ECOProcessingProgress progress) {
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
            if (!machine.reason().getString().isEmpty()) rows.add(text(Component.translatable(
                "gui.neoecoae.processing.reason", machine.reason()).withStyle(ChatFormatting.GOLD)));
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

    private static MutableComponent location(String location) {
        int coordinates = location.indexOf(" [");
        if (coordinates < 0) return Component.literal(location);
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
    private enum Kind { TEXT, SEPARATOR, PROGRESS }
    private record Row(Component left, Component right, Kind kind, float progress) {}
    private record Layout(Row row, List<FormattedCharSequence> left, List<FormattedCharSequence> right, int height) {}

    /** LDLib supplies the normal tooltip background, placement, and component registration. */
    private static final class TooltipWidget extends Widget {
        private static final int GAP = 12;
        private final List<Layout> layout = new ArrayList<>();

        TooltipWidget(List<Row> rows) {
            super(0, 0, 0, 0);
            setClientSideWidget();
            var client = Minecraft.getInstance();
            Font font = client.font;
            int maximum = Math.max(80, Math.min(260, client.getWindow().getGuiScaledWidth() - 20));
            int width = Math.min(180, maximum);
            for (var row : rows) {
                int right = font.width(row.right());
                width = Math.min(maximum, Math.max(width, font.width(row.left()) + (right == 0 ? 0 : GAP + right)));
            }
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
                layout.add(new Layout(row, left, right, rowHeight));
                height += rowHeight;
            }
            setSize(width, height);
        }

        @Override public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            Font font = Minecraft.getInstance().font;
            int x = getPosition().x, y = getPosition().y, width = getSizeWidth();
            for (var entry : layout) {
                if (entry.row().kind() == Kind.SEPARATOR) {
                    graphics.fill(x, y + 2, x + width, y + 3, 0xFF37303F);
                } else if (entry.row().kind() == Kind.PROGRESS) {
                    graphics.fill(x, y + 1, x + width, y + 3, 0xFF332E3B);
                    int filled = Math.round(width * entry.row().progress());
                    if (filled > 0) graphics.fill(x, y + 1, x + filled, y + 3, 0xFF60CD64);
                } else {
                    for (int i = 0; i < entry.left().size(); i++) {
                        graphics.drawString(font, entry.left().get(i), x, y + i * (font.lineHeight + 2), 0xFFEEEAF2, true);
                    }
                    for (int i = 0; i < entry.right().size(); i++) {
                        var line = entry.right().get(i);
                        graphics.drawString(font, line, x + width - font.width(line), y + i * (font.lineHeight + 2), 0xFFEEEAF2, true);
                    }
                }
                y += entry.height();
            }
        }
    }
}
