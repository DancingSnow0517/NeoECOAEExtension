package cn.dancingsnow.neoecoae.dev;

import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;

final class GeneratorTooltip {
    private GeneratorTooltip() {}

    static void append(CompoundTag data, boolean enabled, List<Component> lines) {
        boolean fe = data.contains("fe");
        lines.add(Component.translatable(fe ? "tooltip.neoecoae.dev.fe_mode" : "tooltip.neoecoae.dev.resource_mode")
            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        lines.add(Component.translatable("tooltip.neoecoae.dev.mount").withStyle(ChatFormatting.GRAY));
        if (!enabled) {
            lines.add(Component.translatable("tooltip.neoecoae.dev.disabled").withStyle(ChatFormatting.RED));
            lines.add(Component.literal("neoecoae-dev-tools.toml → enabled").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (fe) {
            var config = data.getCompoundOrEmpty("fe");
            line(lines, "fe_types", number(1, ChatFormatting.AQUA));
            line(lines, "producers", number(config.getIntOr("producers", 0), ChatFormatting.GREEN));
            line(lines, "produce_range", amount(config.getLongOr("produceMin", 0L), ChatFormatting.GREEN), amount(config.getLongOr("produceMax", 0L), ChatFormatting.GREEN));
            line(lines, "consumers", number(config.getIntOr("consumers", 0), ChatFormatting.GOLD));
            line(lines, "consume_range", amount(config.getLongOr("consumeMin", 0L), ChatFormatting.GOLD), amount(config.getLongOr("consumeMax", 0L), ChatFormatting.GOLD));
            lines.add(Component.translatable(config.getBooleanOr("running", false) && enabled ? "tooltip.neoecoae.dev.fe_enabled" : "tooltip.neoecoae.dev.fe_paused")
                .withStyle(config.getBooleanOr("running", false) && enabled ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("tooltip.neoecoae.dev.fe_consumes").withStyle(ChatFormatting.RED));
        } else {
            var defaults = GeneratorSettings.DEFAULT;
            boolean configured = data.contains("types");
            line(lines, "types", number(configured ? data.getIntOr("types", 0) : defaults.types(), ChatFormatting.AQUA));
            line(lines, "range", amount(configured ? data.getLongOr("minimum", 0L) : defaults.minimum(), ChatFormatting.GOLD),
                amount(configured ? data.getLongOr("maximum", 0L) : defaults.maximum(), ChatFormatting.GOLD));
            line(lines, "interval", number(configured ? data.getIntOr("interval", 0) : defaults.interval(), ChatFormatting.GREEN));
        }
        lines.add(Component.translatable("tooltip.neoecoae.dev.help").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component number(Number value, ChatFormatting color) {
        return Component.literal(DisplayNumbers.grouped(value)).withStyle(color);
    }
    private static Component amount(Number value, ChatFormatting color) {
        return Component.literal(DisplayNumbers.compact(value)).withStyle(color);
    }
    private static void line(List<Component> lines, String key, Object... arguments) {
        lines.add(Component.translatable("tooltip.neoecoae.dev." + key, arguments).withStyle(ChatFormatting.GRAY));
    }
}
