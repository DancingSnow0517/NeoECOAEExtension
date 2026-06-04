package cn.dancingsnow.neoecoae.util;

/**
 * Human-readable byte formatting.
 * Converts raw byte counts into short unit-suffixed strings (B, K, M, G, T, P, E)
 * with 1024-based steps. Integer values are shown without decimals; non-integer
 * values show at most one decimal place.
 */
public final class NETextFormat {

    private static final String[] UNITS = {"B", "K", "M", "G", "T", "P", "E"};

    private NETextFormat() {}

    /**
     * Format a byte count into a human-readable string.
     * Examples: 1024 → "1K", 1536 → "1.5K", 1048576 → "1M", 6442450944 → "6G".
     */
    public static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "0B";
        }
        int unitIndex = 0;
        double value = bytes;
        while (value >= 1024.0 && unitIndex < UNITS.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        // Round to at most 1 decimal; if whole number, show as integer.
        if (value == (long) value) {
            return (long) value + UNITS[unitIndex];
        }
        return String.format("%.1f", value) + UNITS[unitIndex];
    }

    /**
     * Format bytes, appending a space before the unit for use in narrative text.
     * Example: 6442450944 → "6 G".
     */
    public static String formatBytesSpaced(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        int unitIndex = 0;
        double value = bytes;
        while (value >= 1024.0 && unitIndex < UNITS.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        if (value == (long) value) {
            return (long) value + " " + UNITS[unitIndex];
        }
        return String.format("%.1f", value) + " " + UNITS[unitIndex];
    }

    /**
     * Format a value already expressed in K-units (kilobytes) into human-readable form.
     * The input is in K steps (1024-based), NOT raw bytes.
     *
     * <p>Examples:
     * <ul>
     *   <li>6291456 → "6G"  (6,291,456 K = 6 G)</li>
     *   <li>4194304 → "4G"  (4,194,304 K = 4 G)</li>
     *   <li>1024    → "1M"  (1,024 K = 1 M)</li>
     *   <li>1536    → "1.5M"</li>
     *   <li>512     → "512K"</li>
     * </ul>
     *
     * <p>No space between number and unit, matching AE2's compact style.</p>
     */
    public static String formatKiloUnit(long kiloUnits) {
        if (kiloUnits <= 0) {
            return "0K";
        }
        // Start at index 1 because input is already K
        int unitIndex = 1; // "K"
        double value = kiloUnits;
        while (value >= 1024.0 && unitIndex < UNITS.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        if (value == (long) value) {
            return (long) value + UNITS[unitIndex];
        }
        return String.format("%.1f", value) + UNITS[unitIndex];
    }
}
