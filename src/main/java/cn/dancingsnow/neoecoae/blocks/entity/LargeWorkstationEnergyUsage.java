package cn.dancingsnow.neoecoae.blocks.entity;

import java.util.Arrays;

/** Actual AE paid over the most recent second, including preheating. Never persisted or charged. */
final class LargeWorkstationEnergyUsage {
    private final long[] ticks = new long[20];
    private final double[] paid = new double[20];

    LargeWorkstationEnergyUsage() { Arrays.fill(ticks, Long.MIN_VALUE); }

    void record(long tick, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        int index = Math.floorMod(tick, 20);
        if (ticks[index] != tick) {
            ticks[index] = tick;
            paid[index] = 0;
        }
        paid[index] += amount;
    }

    double average(long now) {
        double total = 0;
        for (int i = 0; i < ticks.length; i++) {
            if (ticks[i] <= now && ticks[i] > now - 20) total += paid[i];
        }
        return total / 20;
    }
}
