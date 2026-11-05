package cn.dancingsnow.neoecoae.dev;

import java.util.Random;

/** Amounts use each AE key's native storage unit (items, mB, etc.). */
public record GeneratorSettings(int types, long minimum, long maximum, int interval) {
    public static final GeneratorSettings DEFAULT = new GeneratorSettings(16, 1, 64, 20);

    public GeneratorSettings {
        if (types < 1 || types > 65536) throw new IllegalArgumentException("Types must be 1..65536");
        if (minimum < 1 || maximum < minimum) throw new IllegalArgumentException("Require 1 <= minimum <= maximum");
        if (interval < 1) throw new IllegalArgumentException("Interval must be at least 1 tick");
    }

    public long nextAmount(Random random) {
        // maximum - minimum + 1 cannot overflow because minimum is positive.
        long bound = maximum - minimum + 1;
        long bits, value;
        do {
            bits = random.nextLong() >>> 1;
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return minimum + value;
    }
}
