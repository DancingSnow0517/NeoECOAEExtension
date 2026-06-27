package cn.dancingsnow.neoecoae.gui.host;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class NEHostFormat {
    private NEHostFormat() {
    }

    public static String number(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        if (safe < 1_000_000L) {
            return compactDecimal(safe, 1_000L, "K");
        }
        if (safe < 1_000_000_000L) {
            return compactDecimal(safe, 1_000_000L, "M");
        }
        if (safe < 1_000_000_000_000L) {
            return compactDecimal(safe, 1_000_000_000L, "G");
        }
        return compactDecimal(safe, 1_000_000_000_000L, "T");
    }

    public static String bytes(long value) {
        Tooltips.Amount amount = Tooltips.getByteAmount(Math.max(0L, value));
        return amount.digit() + amount.unit();
    }

    public static String usedTotal(long used, long total) {
        return number(used) + " / " + number(total);
    }

    public static String usedTotalBytes(long used, long total) {
        return bytes(used) + " / " + bytes(total);
    }

    public static Component coloredUsedTotal(long used, long total) {
        return coloredUsedTotal(number(used), number(total));
    }

    public static Component coloredUsedTotalBytes(long used, long total) {
        return coloredUsedTotal(bytes(used), bytes(total));
    }

    private static Component coloredUsedTotal(String used, String total) {
        return Component.empty()
            .append(Component.literal(used).withColor(ECOHostStyles.HOST_STAT_USED))
            .append(Component.literal(" / ").withColor(ECOHostStyles.HOST_STAT_SEPARATOR))
            .append(Component.literal(total).withColor(ECOHostStyles.HOST_STAT_TOTAL));
    }

    public static String percent(long used, long total) {
        if (total <= 0L) {
            return "-";
        }
        return Math.max(0L, Math.min(100L, used * 100L / total)) + "%";
    }

    private static String compactDecimal(long value, long unit, String suffix) {
        double scaled = (double) value / (double) unit;
        if (scaled >= 100.0D || Math.abs(scaled - Math.rint(scaled)) < 0.05D) {
            return String.format(Locale.US, "%.0f%s", scaled, suffix);
        }
        return String.format(Locale.US, "%.1f%s", scaled, suffix);
    }
}
