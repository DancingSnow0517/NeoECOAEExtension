package cn.dancingsnow.neoecoae.dev;

/** Logical endpoints all run on the owning server thread, once per active tick. */
public record FeWorkloadSettings(int producers, long produceMin, long produceMax,
                                 int consumers, long consumeMin, long consumeMax,
                                 long seed, boolean simulateFirst) {
    public FeWorkloadSettings {
        if (producers < 0 || consumers < 0 || producers > 65536 || consumers > 65536
                || producers + consumers == 0) throw new IllegalArgumentException("Require 0..65536 endpoints and at least one endpoint");
        if (produceMin < 1 || produceMax < produceMin || consumeMin < 1 || consumeMax < consumeMin)
            throw new IllegalArgumentException("Require 1 <= minimum <= maximum for both FE ranges");
    }
}
