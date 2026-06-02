package cn.dancingsnow.neoecoae.compat.jade.provider;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

final class JadeText {
    private JadeText() {
    }

    static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    static Component threadLine(long used, long total) {
        return label("线程：")
            .append(value(formatNumber(used), ChatFormatting.WHITE))
            .append(separator(" / "))
            .append(value(formatNumber(total), ChatFormatting.WHITE));
    }

    static Component overclockLine(int effective, int theoretical) {
        return label("超频倍率：")
            .append(value(Integer.toString(effective), ChatFormatting.AQUA))
            .append(separator(" / "))
            .append(value(Integer.toString(theoretical), ChatFormatting.AQUA));
    }

    static Component progressLine(int ticks) {
        return label("进度：")
            .append(value(ticks + "t", ChatFormatting.LIGHT_PURPLE))
            .append(label("（"))
            .append(value(String.format(Locale.ROOT, "%.2fs", ticks / 20.0), ChatFormatting.LIGHT_PURPLE))
            .append(label("）"));
    }

    static Component coolantLine(long coolant, int maxOverclock) {
        return label("冷却液：")
            .append(value(formatNumber(coolant), ChatFormatting.AQUA))
            .append(separator(" · "))
            .append(label("增频上限："))
            .append(value(maxOverclock + "×", ChatFormatting.AQUA));
    }

    static Component acceleratorLine(long accelerators, long dispatchLimit) {
        return label("加速器：")
            .append(value(formatNumber(accelerators), ChatFormatting.AQUA))
            .append(separator(" · "))
            .append(label("下发上限："))
            .append(value(formatNumber(dispatchLimit), ChatFormatting.AQUA))
            .append(value(" patterns/t", ChatFormatting.WHITE));
    }

    static Component storageLine(long used, long total) {
        return label("存储：")
            .append(value(formatNumber(used), ChatFormatting.WHITE))
            .append(separator(" / "))
            .append(value(formatNumber(total), ChatFormatting.WHITE));
    }

    static Component onlineLine(boolean online) {
        return Component.literal(online ? "设备在线" : "设备离线")
            .withStyle(online ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static MutableComponent label(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent separator(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static MutableComponent value(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }
}
