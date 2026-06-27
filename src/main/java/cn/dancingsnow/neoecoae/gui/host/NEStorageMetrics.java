package cn.dancingsnow.neoecoae.gui.host;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class NEStorageMetrics {
    private NEStorageMetrics() {
    }

    static List<Metric> storageMetrics(List<NEStorageTypeStat> stats) {
        List<Metric> metrics = new ArrayList<>();
        NEStorageTypeStat itemStat = findTypeStat(stats, "item");
        NEStorageTypeStat fluidStat = findTypeStat(stats, "fluid");
        metrics.add(createMetric("neoecoae:items", itemStat, NEHostUiPrimitives.tr("gui.neoecoae.storage.items", "Items"), 0xFF43B678));
        metrics.add(createMetric("neoecoae:fluids", fluidStat, NEHostUiPrimitives.tr("gui.neoecoae.storage.fluids", "Fluids"), 0xFF3A8FD6));
        for (NEStorageTypeStat stat : stats) {
            if (matchesTypeStat(stat, "item") || matchesTypeStat(stat, "fluid")) {
                continue;
            }
            metrics.add(createMetric(stat.typeId().toString(), stat, stat.displayName(), typeAccentColor(stat, metrics.size())));
        }
        return List.copyOf(metrics);
    }

    static List<Metric> columnStats(List<NEStorageTypeStat> stats) {
        List<Metric> metrics = storageMetrics(stats);
        List<Metric> columns = metrics.stream()
            .filter(stat -> stat.totalBytes() > 0 || stat.totalTypes() > 0)
            .toList();
        return columns.isEmpty() ? metrics : columns;
    }

    static int usedValueColor(long used, long max) {
        if (used <= 0 || max <= 0) {
            return 0xFF00FC00;
        }
        double pct = (double) used / (double) max;
        if (pct >= 1.0D) {
            return NEHostUiPrimitives.TEXT_ERROR;
        }
        if (pct >= 0.9D) {
            return 0xFFFF9A3D;
        }
        if (pct >= 0.75D) {
            return NEHostUiPrimitives.TEXT_WARNING;
        }
        return 0xFF00FC00;
    }

    static int metricColor(int accentColor, long max, double pct) {
        if (max <= 0L) {
            return NEHostUiPrimitives.TEXT_MUTED;
        }
        return lerpColor(darken(accentColor, 0.72D), accentColor, Mth.clamp(pct + 0.2D, 0.0D, 1.0D));
    }

    private static Metric createMetric(String key, NEStorageTypeStat stat, Component fallbackLabel, int accentColor) {
        if (stat == null) {
            return new Metric(key, fallbackLabel, 0L, 0L, 0L, 0L, accentColor);
        }
        return new Metric(key, fallbackLabel, stat.usedBytes(), stat.totalBytes(), stat.usedTypes(), stat.totalTypes(), accentColor);
    }

    private static NEStorageTypeStat findTypeStat(List<NEStorageTypeStat> stats, String needle) {
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        String pluralNeedle = lowerNeedle + "s";
        for (NEStorageTypeStat stat : stats) {
            String path = stat.typeId().getPath().toLowerCase(Locale.ROOT);
            if (path.equals(lowerNeedle) || path.equals(pluralNeedle)) {
                return stat;
            }
        }
        for (NEStorageTypeStat stat : stats) {
            String path = stat.typeId().getPath().toLowerCase(Locale.ROOT);
            String name = stat.displayName().getString().toLowerCase(Locale.ROOT);
            if (path.contains(lowerNeedle) || name.contains(lowerNeedle)) {
                return stat;
            }
        }
        return null;
    }

    private static boolean matchesTypeStat(NEStorageTypeStat stat, String needle) {
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        String pluralNeedle = lowerNeedle + "s";
        String path = stat.typeId().getPath().toLowerCase(Locale.ROOT);
        return path.equals(lowerNeedle) || path.equals(pluralNeedle);
    }

    private static int typeAccentColor(NEStorageTypeStat stat, int index) {
        String path = stat.typeId().getPath().toLowerCase(Locale.ROOT);
        String name = stat.displayName().getString().toLowerCase(Locale.ROOT);
        if (containsAny(path, name, "chemical", "chem", "gas", "infuse", "infusion", "pigment", "slurry")) {
            return 0xFF9A6AE8;
        }
        if (containsAny(path, name, "flux", "fe", "energy")) {
            return 0xFFE8A84A;
        }
        if (containsAny(path, name, "mana")) {
            return 0xFF33B6D8;
        }
        if (containsAny(path, name, "source")) {
            return 0xFFB66AE8;
        }
        int[] palette = {0xFFE06C75, 0xFF61AFEF, 0xFF98C379, 0xFFD19A66, 0xFFC678DD};
        return palette[Math.floorMod(index, palette.length)];
    }

    private static boolean containsAny(String path, String name, String... needles) {
        for (String needle : needles) {
            if (path.contains(needle) || name.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static int darken(int color, double factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >>> 16) & 0xFF) * factor);
        int g = (int) (((color >>> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpColor(int start, int end, double t) {
        double safeT = Mth.clamp(t, 0.0D, 1.0D);
        int a = (int) Mth.lerp(safeT, (start >>> 24) & 0xFF, (end >>> 24) & 0xFF);
        int r = (int) Mth.lerp(safeT, (start >>> 16) & 0xFF, (end >>> 16) & 0xFF);
        int g = (int) Mth.lerp(safeT, (start >>> 8) & 0xFF, (end >>> 8) & 0xFF);
        int b = (int) Mth.lerp(safeT, start & 0xFF, end & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    record Metric(String key, Component label, long usedBytes, long totalBytes, long usedTypes, long totalTypes, int accentColor) {
    }
}
