package cn.dancingsnow.neoecoae.platform;

/** Java 17 implementation of the range semantics used by Java 21 Math.clamp call sites. */
public final class JdkCompat {
    private JdkCompat() {}

    public static int clamp(long value, int min, int max) {
        if (min > max) throw new IllegalArgumentException("Inverted integer bounds");
        return (int) Math.min(max, Math.max(value, min));
    }

    public static long clamp(long value, long min, long max) {
        if (min > max) throw new IllegalArgumentException("Inverted long bounds");
        return Math.min(max, Math.max(value, min));
    }

    public static float clamp(float value, float min, float max) {
        if (Float.isNaN(min) || Float.isNaN(max) || Float.compare(min, max) > 0) {
            throw new IllegalArgumentException("Invalid float bounds");
        }
        return Math.min(max, Math.max(value, min));
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max) || Double.compare(min, max) > 0) {
            throw new IllegalArgumentException("Invalid double bounds");
        }
        return Math.min(max, Math.max(value, min));
    }
}
