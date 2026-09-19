package cn.dancingsnow.neoecoae.crafting.display.format;

/** Locale-independent grouping for display strings, preserving fractions and unit suffixes. */
public final class DisplayNumbers {
    private DisplayNumbers() {}

    public static String grouped(String value) {
        int start = value.startsWith("-") || value.startsWith("+") ? 1 : 0;
        int end = start;
        while (end < value.length() && (value.charAt(end) >= '0' && value.charAt(end) <= '9'
                || value.charAt(end) == ',')) end++;
        String digits = value.substring(start, end).replace(",", "");
        StringBuilder result = new StringBuilder(value.substring(0, start));
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) result.append(',');
            result.append(digits.charAt(i));
        }
        return result.append(value.substring(end)).toString();
    }

    /** Converts a compact amount such as 500PQ to a complete byte label such as 500 PQB. */
    public static String bytes(String compactAmount) {
        int end = 0;
        while (end < compactAmount.length()) {
            char c = compactAmount.charAt(end);
            if (!(c >= '0' && c <= '9' || c == ',' || c == '.' || c == '-' || c == '+')) break;
            end++;
        }
        return grouped(compactAmount.substring(0, end)) + " "
            + compactAmount.substring(end).trim().toUpperCase(java.util.Locale.ROOT) + "B";
    }
}
