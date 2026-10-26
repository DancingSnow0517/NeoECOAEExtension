package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

final class ComputationProcessingTooltips {
    private ComputationProcessingTooltips() {}
    static List<Component> lines(ECOProcessingProgress progress) {
        if (progress.machineCount() == 0) return List.of();
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("gui.neoecoae.processing.summary", progress.machineCount(),
            state(progress.state()), progress.hasProgress() ? percent(progress.progress()) : "—").withStyle(ChatFormatting.LIGHT_PURPLE));
        for (var machine : progress.machines()) {
            String percent = machine.durationTicks() > 0 ? percent(machine.progressTicks() / (float) machine.durationTicks()) : "—";
            lines.add(Component.translatable("gui.neoecoae.processing.machine", machine.name(), state(machine.state()), percent,
                machine.parallel()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(machine.location()).withStyle(ChatFormatting.DARK_GRAY));
            if (!machine.reason().getString().isEmpty()) lines.add(machine.reason().copy().withStyle(ChatFormatting.GRAY));
        }
        if (progress.machineCount() > progress.machines().size()) lines.add(Component.translatable(
            "gui.neoecoae.processing.more", progress.machineCount() - progress.machines().size()).withStyle(ChatFormatting.DARK_GRAY));
        if (progress.refreshing()) lines.add(Component.translatable("gui.neoecoae.processing.refreshing").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static Component state(ECOProcessingProgress.State state) {
        return Component.translatable("gui.neoecoae.processing.state." + state.name().toLowerCase(Locale.ROOT));
    }
    private static String percent(float value) { return Math.round(value * 100) + "%"; }
}
