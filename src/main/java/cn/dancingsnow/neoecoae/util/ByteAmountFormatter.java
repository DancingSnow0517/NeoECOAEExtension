package cn.dancingsnow.neoecoae.util;

import appeng.core.localization.Tooltips;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Formats AE2 byte amounts beyond the largest unit supported by AE2 itself. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ByteAmountFormatter {
    private static final long AE2_BYTE_FORMAT_OVERFLOW_THRESHOLD = 1_000_000_000_000L;
    private static final String[] LARGE_BYTE_UNITS = { "TB", "PB", "EB" };

    public static String format(long bytes) {
        if (bytes < AE2_BYTE_FORMAT_OVERFLOW_THRESHOLD) {
            Tooltips.Amount amount = Tooltips.getByteAmount(bytes);
            return amount.digit() + amount.unit();
        }

        double scaled = bytes / (double) AE2_BYTE_FORMAT_OVERFLOW_THRESHOLD;
        int unitIndex = 0;
        while (scaled >= 1_000.0 && unitIndex < LARGE_BYTE_UNITS.length - 1) {
            scaled /= 1_000.0;
            unitIndex++;
        }
        if (scaled >= 999.5 && unitIndex < LARGE_BYTE_UNITS.length - 1) {
            scaled /= 1_000.0;
            unitIndex++;
        }

        return String.format(Locale.ROOT, "%.3g%s", scaled, LARGE_BYTE_UNITS[unitIndex]);
    }
}
