package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Compact machine details in the existing item tooltip. All times are machine ticks. */
final class ComputationProcessingTooltips {
    private ComputationProcessingTooltips() {}

    /** Machine rows for an item that is actively crafting; empty when nothing is reported. */
    static List<Row> rows(ComputationCpuItemEntry item) {
        return item.active().signum() > 0 ? rows(item.processing()) : List.of();
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
}
