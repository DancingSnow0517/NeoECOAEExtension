package cn.dancingsnow.neoecoae.crafting.display.format;

/** Decimal prefixes continued beyond Q by appending another Q every ten groups. */
public final class ExtendedDecimalUnits {
    private static final String[] PREFIXES = { "", "K", "M", "G", "T", "P", "E", "Z", "Y", "R" };

    private ExtendedDecimalUnits() {}

    /** Returns the suffix for a power of 1000: Q, KQ, ... QQ, KQQ, ... */
    public static String suffix(int group) {
        if (group < 0) throw new IllegalArgumentException("Negative decimal unit group");
        return PREFIXES[group % PREFIXES.length] + "Q".repeat(group / PREFIXES.length);
    }
}
