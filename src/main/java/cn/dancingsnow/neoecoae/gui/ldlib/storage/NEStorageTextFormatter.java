package cn.dancingsnow.neoecoae.gui.ldlib.storage;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NEStorageTextFormatter {
    private static final ThreadLocal<DecimalFormat> PERFORMANCE_MS_FORMAT =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US)));

    public static String performanceCorner(long averageNanos) {
        long safeNanos = Math.max(0L, averageNanos);
        long micros = Math.round(safeNanos / 1_000.0D);
        return micros < 1_000L ? micros + " us" : PERFORMANCE_MS_FORMAT.get().format(safeNanos / 1_000_000.0D) + " ms";
    }

    public static String performanceTooltip(long averageNanos) {
        long safeNanos = Math.max(0L, averageNanos);
        long micros = Math.round(safeNanos / 1_000.0D);
        return micros + " us/" + PERFORMANCE_MS_FORMAT.get().format(safeNanos / 1_000_000.0D) + " ms";
    }

    public static BigInteger totalInfiniteAmount(NEStorageUiState state) {
        BigInteger total = BigInteger.ZERO;
        for (NEStorageUiTypeState type : state.typeStates()) {
            total = total.add(parseAmount(type.safeUsedAmount()));
        }
        return total;
    }

    public static BigInteger parseAmount(String value) {
        try {
            return new BigInteger(value == null || value.isBlank() ? "0" : value).max(BigInteger.ZERO);
        } catch (NumberFormatException ignored) {
            return BigInteger.ZERO;
        }
    }

    private NEStorageTextFormatter() {}
}
