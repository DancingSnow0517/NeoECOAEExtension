package cn.dancingsnow.neoecoae.gui.ldlib.storage;

import net.minecraft.Util;
import net.minecraft.util.Mth;

/** Stateful 500 ms easing used by the storage load gauge. */
public final class NEStorageAnimatedRatio {
    private static final long DURATION_MS = 500L;
    private static final double EPSILON = 0.0001D;

    private double start;
    private double target = -1.0D;
    private long startMs;

    public double update(double newTarget) {
        long now = Util.getMillis();
        if (target < 0.0D) {
            start = 0.0D;
            target = newTarget;
            startMs = now;
        } else if (Math.abs(target - newTarget) > EPSILON) {
            start = current(now);
            target = newTarget;
            startMs = now;
        }
        return current(now);
    }

    private double current(long now) {
        double elapsed = Mth.clamp((double) (now - startMs) / DURATION_MS, 0.0D, 1.0D);
        return start + (target - start) * cubicBezierEase(elapsed);
    }

    private static double cubicBezierEase(double progress) {
        double t = Mth.clamp(progress, 0.0D, 1.0D);
        for (int i = 0; i < 5; i++) {
            double x = cubicBezier(t, 0.25D, 0.25D);
            double slope = cubicBezierSlope(t, 0.25D, 0.25D);
            if (slope == 0.0D) {
                break;
            }
            t = Mth.clamp(t - (x - progress) / slope, 0.0D, 1.0D);
        }
        return cubicBezier(t, 0.1D, 1.0D);
    }

    private static double cubicBezier(double t, double p1, double p2) {
        double inverse = 1.0D - t;
        return 3.0D * inverse * inverse * t * p1 + 3.0D * inverse * t * t * p2 + t * t * t;
    }

    private static double cubicBezierSlope(double t, double p1, double p2) {
        double inverse = 1.0D - t;
        return 3.0D * inverse * inverse * p1
                + 6.0D * inverse * t * (p2 - p1)
                + 3.0D * t * t * (1.0D - p2);
    }
}
