package cn.dancingsnow.neoecoae.gui.ldlib.storage;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Maps synchronized storage state to presentation-ready metrics. */
public final class NEStorageMetricsModel {
    public static StorageMetrics from(NEStorageUiState state) {
        List<NEStorageUiTypeState> types = state.typeStates();
        NEStorageUiTypeState itemState = findTypeState(types, "item");
        NEStorageUiTypeState fluidState = findTypeState(types, "fluid");
        Metric energy = new Metric(
                "energy",
                Component.translatable("gui.neoecoae.common.energy"),
                state.storedEnergy(),
                state.maxEnergy(),
                0,
                0,
                Long.toString(Math.max(0L, state.storedEnergy())),
                NELDLibStyle.DARK_TEXT_VALUE);
        List<Metric> typeMetrics = new ArrayList<>();
        typeMetrics.add(createTypeMetric(
                "neoecoae:items", itemState, Component.translatable("gui.neoecoae.storage.items"), 0xFF43B678));
        typeMetrics.add(createTypeMetric(
                "neoecoae:fluids", fluidState, Component.translatable("gui.neoecoae.storage.fluids"), 0xFF3A8FD6));
        for (NEStorageUiTypeState type : types) {
            if (matchesTypeState(type, "item") || matchesTypeState(type, "fluid")) {
                continue;
            }
            typeMetrics.add(createTypeMetric(
                    type.typeId().toString(),
                    type,
                    type.displayComponent(),
                    typeAccentColor(type, typeMetrics.size())));
        }
        return new StorageMetrics(energy, List.copyOf(typeMetrics));
    }

    public static List<Metric> activeMetrics(StorageMetrics metrics) {
        List<Metric> active = new ArrayList<>();
        for (Metric metric : metrics.types()) {
            if (metric.max() > 0 || metric.totalTypes() > 0) {
                active.add(metric);
            }
        }
        return List.copyOf(active);
    }

    private static Metric createTypeMetric(
            String key, NEStorageUiTypeState state, Component fallbackLabel, int accentColor) {
        if (state == null) {
            return new Metric(key, fallbackLabel, 0, 0, 0, 0, "0", accentColor);
        }
        return new Metric(
                key,
                fallbackLabel,
                state.usedBytes(),
                state.totalBytes(),
                state.usedTypes(),
                state.totalTypes(),
                state.safeUsedAmount(),
                accentColor);
    }

    private static NEStorageUiTypeState findTypeState(List<NEStorageUiTypeState> types, String needle) {
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        String pluralNeedle = lowerNeedle + "s";
        for (NEStorageUiTypeState state : types) {
            String path = state.typeId().getPath().toLowerCase(Locale.ROOT);
            if (path.equals(lowerNeedle) || path.equals(pluralNeedle)) {
                return state;
            }
        }
        for (NEStorageUiTypeState state : types) {
            String path = state.typeId().getPath().toLowerCase(Locale.ROOT);
            String name = state.displayName().toLowerCase(Locale.ROOT);
            if (path.contains(lowerNeedle) || name.contains(lowerNeedle)) {
                return state;
            }
        }
        return null;
    }

    private static boolean matchesTypeState(NEStorageUiTypeState state, String needle) {
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        String pluralNeedle = lowerNeedle + "s";
        String path = state.typeId().getPath().toLowerCase(Locale.ROOT);
        return path.equals(lowerNeedle) || path.equals(pluralNeedle);
    }

    private static int typeAccentColor(NEStorageUiTypeState state, int index) {
        String path = state.typeId().getPath().toLowerCase(Locale.ROOT);
        String name = state.displayName().toLowerCase(Locale.ROOT);
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

    public record StorageMetrics(Metric energy, List<Metric> types) {}

    public record Metric(
            String key,
            Component label,
            long used,
            long max,
            long usedTypes,
            long totalTypes,
            String usedAmount,
            int accentColor) {
        public double percent() {
            return NEStorageUsageModel.percent(used, max);
        }
    }

    private NEStorageMetricsModel() {}
}
