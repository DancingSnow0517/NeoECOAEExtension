package cn.dancingsnow.neoecoae.gui.storage;

import net.minecraft.Util;
import net.minecraft.util.Mth;

final class StorageHostAnimatedRatio {
    private static final long USAGE_ANIMATION_MS = 500L;
    private static final double USAGE_ANIMATION_EPSILON = 0.0001D;

    private double animationStart;
    private double animationTarget = -1.0D;
    private boolean infinite;
    private boolean migrating;
    private double migrationProgress;
    private long animationStartMs;

    void setTarget(float ratio) {
        long now = Util.getMillis();
        if (ratio < 0.0F) {
            boolean nextInfinite = ratio > -1.5F;
            double nextMigrationProgress = nextInfinite ? 1.0D : Mth.clamp(-ratio - 2.0D, 0.0D, 1.0D);
            if (infinite == nextInfinite && migrating == !nextInfinite && Math.abs(animationTarget - 1.0D) <= USAGE_ANIMATION_EPSILON) {
                migrationProgress = nextMigrationProgress;
                return;
            }
            animationStart = animationTarget < 0.0D ? 0.0D : currentValue(now);
            animationTarget = 1.0D;
            animationStartMs = now;
            infinite = nextInfinite;
            migrating = !nextInfinite;
            migrationProgress = nextMigrationProgress;
            return;
        }
        infinite = false;
        migrating = false;
        migrationProgress = 0.0D;
        double target = Math.max(0.0D, Math.min(1.0D, ratio));
        if (animationTarget < 0.0D) {
            animationStart = 0.0D;
            animationTarget = target;
            animationStartMs = now;
        } else if (Math.abs(animationTarget - target) > USAGE_ANIMATION_EPSILON) {
            animationStart = currentValue(now);
            animationTarget = target;
            animationStartMs = now;
        }
    }

    double value() {
        if (animationTarget < 0.0D) {
            return 0.0D;
        }
        return currentValue(Util.getMillis());
    }

    boolean infinite() {
        return infinite;
    }

    boolean migrating() {
        return migrating;
    }

    double migrationProgress() {
        return migrationProgress;
    }

    private double currentValue(long now) {
        double elapsed = Mth.clamp((double)(now - animationStartMs) / (double)USAGE_ANIMATION_MS, 0.0D, 1.0D);
        double eased = cubicBezierEase(elapsed);
        return animationStart + (animationTarget - animationStart) * eased;
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
        return 3.0D * inverse * inverse * p1 + 6.0D * inverse * t * (p2 - p1) + 3.0D * t * t * (1.0D - p2);
    }
}
