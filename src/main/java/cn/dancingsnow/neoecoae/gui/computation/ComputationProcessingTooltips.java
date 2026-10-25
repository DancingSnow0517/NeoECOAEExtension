package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Detailed processing information stays in the item tooltip, leaving the grid's existing text unchanged. */
final class ComputationProcessingTooltips {
    private ComputationProcessingTooltips() {}

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
        if (progress.machineCount() == 0 && !progress.refreshing()) return List.of();
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("gui.neoecoae.processing.scope").withStyle(ChatFormatting.DARK_GRAY));
        if (progress.machineCount() > 0) lines.add(Component.translatable("gui.neoecoae.processing.count", progress.machineCount()));
        if (progress.machineCount() > 1) {
            lines.add(Component.translatable("gui.neoecoae.processing.aggregate").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (progress.refreshing()) lines.add(Component.translatable("gui.neoecoae.processing.refreshing").withStyle(ChatFormatting.GRAY));
        if (progress.machineCount() > 0 && progress.machines().isEmpty()) {
            lines.add(Component.translatable("gui.neoecoae.processing.loading_details").withStyle(ChatFormatting.GRAY));
        }
        if (progress.pages() > 1 && progress.detailPage() >= 0) lines.add(Component.translatable(
            "gui.neoecoae.processing.page", progress.detailPage() + 1, progress.pages()).withStyle(ChatFormatting.GRAY));
        for (var machine : progress.machines()) {
            ChatFormatting color = machine.state() == State.WAITING ? ChatFormatting.GOLD
                : machine.state() == State.RUNNING ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            lines.add(machine.name().copy().withStyle(color).append(" · ")
                .append(Component.translatable("gui.neoecoae.processing.state." + machine.state().name().toLowerCase(Locale.ROOT))));
            lines.add(Component.literal(machine.location()).withStyle(ChatFormatting.DARK_GRAY));
            if (!machine.recipe().isEmpty()) lines.add(Component.translatable("gui.neoecoae.processing.recipe", machine.recipe()));
            if (machine.parallel() > 0) lines.add(Component.translatable("gui.neoecoae.processing.parallel", machine.parallel()));
            if (machine.durationTicks() > 0) {
                int percent = (int) (100L * machine.progressTicks() / machine.durationTicks());
                lines.add(Component.translatable("gui.neoecoae.processing.time", percent,
                    seconds(machine.progressTicks()), seconds(machine.durationTicks())));
                if (machine.state() == State.RUNNING) lines.add(Component.translatable("gui.neoecoae.processing.remaining",
                    seconds(machine.durationTicks() - machine.progressTicks())));
            }
            if (!machine.reason().getString().isEmpty()) {
                lines.add(Component.translatable("gui.neoecoae.processing.reason", machine.reason()).withStyle(ChatFormatting.GOLD));
            }
        }
        return lines;
    }

    private static String seconds(int ticks) { return String.format(Locale.ROOT, "%.1f", ticks / 20.0); }
}
