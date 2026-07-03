package cn.dancingsnow.neoecoae.compat.jade.provider;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class JadeText {
    private static final DecimalFormat PERCENT_FORMAT =
            new DecimalFormat("0.##%", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT =
            ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));

    private JadeText() {}

    static String formatNumber(long value) {
        return NUMBER_FORMAT.get().format(value);
    }

    static String formatPercent(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return "0%";
        }
        return PERCENT_FORMAT.format(ratio);
    }

    static Component threadLine(long used, long total) {
        return label("jade.neoecoae.thread_label")
                .append(value(formatNumber(used), ChatFormatting.WHITE))
                .append(separator(" / "))
                .append(value(formatNumber(total), ChatFormatting.WHITE));
    }

    static Component storageLine(long used, long total) {
        return label("jade.neoecoae.storage_label")
                .append(value(formatNumber(used), ChatFormatting.AQUA))
                .append(separator(" / "))
                .append(value(formatNumber(total), ChatFormatting.AQUA));
    }

    static Component energyLine(long energyPerTick) {
        return label("jade.neoecoae.energy_per_tick_label")
                .append(value(formatNumber(Math.max(0L, energyPerTick)) + " AE/t", ChatFormatting.AQUA));
    }

    static Component timeMultiplierLine(double multiplier) {
        return label("jade.neoecoae.time_multiplier_label")
                .append(value(formatPercent(multiplier), ChatFormatting.AQUA));
    }

    static Component overclockLine(int effective, int theoretical) {
        return label("jade.neoecoae.overclock_multiplier_label")
                .append(value(Integer.toString(Math.max(0, effective)), ChatFormatting.AQUA))
                .append(separator(" / "))
                .append(value(Integer.toString(Math.max(0, theoretical)), ChatFormatting.AQUA));
    }

    static Component runningLine(boolean running) {
        return Component.translatable(
                        "jade.neoecoae.running",
                        Component.translatable(running ? "jade.neoecoae.yes" : "jade.neoecoae.no")
                                .withStyle(running ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .withStyle(ChatFormatting.GRAY);
    }

    static Component recipesPerOperationLine(long recipes) {
        return label("jade.neoecoae.recipes_per_operation_label")
                .append(value(formatNumber(Math.max(0L, recipes)), ChatFormatting.AQUA));
    }

    static MutableComponent label(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }

    static MutableComponent separator(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    static MutableComponent value(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }
}
