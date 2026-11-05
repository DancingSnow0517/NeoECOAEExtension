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
            var config = data.getCompound("fe");
            line(lines, "fe_types", number(1, ChatFormatting.AQUA));
            line(lines, "producers", number(config.getInt("producers"), ChatFormatting.GREEN));
            line(lines, "produce_range", amount(config.getLong("produceMin"), ChatFormatting.GREEN), amount(config.getLong("produceMax"), ChatFormatting.GREEN));
            line(lines, "consumers", number(config.getInt("consumers"), ChatFormatting.GOLD));
            line(lines, "consume_range", amount(config.getLong("consumeMin"), ChatFormatting.GOLD), amount(config.getLong("consumeMax"), ChatFormatting.GOLD));
            lines.add(Component.translatable(config.getBoolean("running") && enabled ? "tooltip.neoecoae.dev.fe_enabled" : "tooltip.neoecoae.dev.fe_paused")
                .withStyle(config.getBoolean("running") && enabled ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("tooltip.neoecoae.dev.fe_consumes").withStyle(ChatFormatting.RED));
        } else {
            var defaults = GeneratorSettings.DEFAULT;
            boolean configured = data.contains("types");
            line(lines, "types", number(configured ? data.getInt("types") : defaults.types(), ChatFormatting.AQUA));
            line(lines, "range", amount(configured ? data.getLong("minimum") : defaults.minimum(), ChatFormatting.GOLD),
                amount(configured ? data.getLong("maximum") : defaults.maximum(), ChatFormatting.GOLD));
            line(lines, "interval", number(configured ? data.getInt("interval") : defaults.interval(), ChatFormatting.GREEN));
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
