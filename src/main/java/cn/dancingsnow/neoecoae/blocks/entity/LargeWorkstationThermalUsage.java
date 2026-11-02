package cn.dancingsnow.neoecoae.blocks.entity;

import java.util.Arrays;

/** Actual thermal changes over the last loaded second. Telemetry only; never persisted or replayed. */
final class LargeWorkstationThermalUsage {
    private final Sample[] samples = new Sample[LargeWorkstationThermal.TICKS_PER_SECOND];

    record Rates(double processing, double preheat, double cooling, double naturalCooling, double net) {
        static final Rates ZERO = new Rates(0, 0, 0, 0, 0);
    }

    private record Sample(long tick, LargeWorkstationThermal.TickResult result, double net) {}

    void record(long tick, LargeWorkstationThermal.State before, LargeWorkstationThermal.TickResult result) {
        samples[Math.floorMod(tick, samples.length)] = new Sample(tick, result, result.state().temperature() - before.temperature());
    }

    Rates rates(long now) {
        double processing = 0, preheat = 0, cooling = 0, naturalCooling = 0, net = 0;
        for (Sample sample : samples) {
            if (sample == null || sample.tick > now || sample.tick <= now - samples.length) continue;
            processing += sample.result.processingHeat();
            preheat += sample.result.preheatHeat();
            cooling += sample.result.cooling();
            naturalCooling += sample.result.naturalCooling();
            net += sample.net;
        }
        return new Rates(processing, preheat, cooling, naturalCooling, net);
    }

    void clear() { Arrays.fill(samples, null); }
}
