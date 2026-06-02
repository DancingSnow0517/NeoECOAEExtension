package cn.dancingsnow.neoecoae.compat.jade.provider;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class JadeText {
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.##%",
            DecimalFormatSymbols.getInstance(Locale.ROOT));

    private JadeText() {
    }

    static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    static String formatPercent(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return "0%";
        }
        return PERCENT_FORMAT.format(ratio);
    }

    static Component threadLine(long used, long total) {
        return label("线程：")
                .append(value(formatNumber(used), ChatFormatting.WHITE))
                .append(separator(" / "))
                .append(value(formatNumber(total), ChatFormatting.WHITE));
    }

    static Component storageLine(long used, long total) {
        return label("存储：")
                .append(value(formatNumber(used), ChatFormatting.AQUA))
                .append(separator(" / "))
                .append(value(formatNumber(total), ChatFormatting.AQUA));
    }

    static Component energyLine(long energyPerTick) {
        return label("耗能：")
                .append(value(formatNumber(Math.max(0L, energyPerTick)) + " AE/t", ChatFormatting.AQUA));
    }

    static Component timeMultiplierLine(double multiplier) {
        return label("耗时倍率：")
                .append(value(formatPercent(multiplier), ChatFormatting.AQUA));
    }

    static Component overclockLine(int effective, int theoretical) {
        return label("超频倍率：")
                .append(value(Integer.toString(Math.max(0, effective)), ChatFormatting.AQUA))
                .append(separator(" / "))
                .append(value(Integer.toString(Math.max(0, theoretical)), ChatFormatting.AQUA));
    }

    static Component parallelRecipesLine(long recipes) {
        return label("每轮处理：")
                .append(value(formatNumber(Math.max(0L, recipes)), ChatFormatting.AQUA))
                .append(label(" 个配方"));
    }

    static Component structureLine(boolean formed) {
        return Component.literal(formed ? "结构已成型" : "结构未成型")
                .withStyle(formed ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    static Component runningLine(boolean running) {
        return label("运行中：")
                .append(value(running ? "是" : "否", running ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    static Component recipesPerOperationLine(long recipes) {
        return label("每次处理配方数量：")
                .append(value(formatNumber(Math.max(0L, recipes)), ChatFormatting.AQUA));
    }

    static MutableComponent label(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    static MutableComponent separator(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    static MutableComponent value(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }
}