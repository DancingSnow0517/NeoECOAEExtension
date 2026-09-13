package cn.dancingsnow.neoecoae.api.me.progress;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKeyType;

/** Immutable progress snapshot detached from ECO's mutable elapsed-time tracker. */
public record ECOCraftingProgressSnapshot(
        float progress,
        long elapsedTimeNanos,
        Map<AEKeyType, Long> startedWorkByType,
        Map<AEKeyType, Long> completedWorkByType) implements ECOCraftingProgressView {

    public ECOCraftingProgressSnapshot {
        progress = Float.isFinite(progress) ? Math.clamp(progress, 0.0F, 1.0F) : 0.0F;
        elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        startedWorkByType = immutableNonNegativeMap(startedWorkByType);
        completedWorkByType = immutableNonNegativeMap(completedWorkByType);
    }

    public ECOCraftingProgressSnapshot(float progress, long elapsedTimeNanos) {
        this(progress, elapsedTimeNanos, Map.of(), Map.of());
    }

    public static ECOCraftingProgressSnapshot empty() {
        return new ECOCraftingProgressSnapshot(0.0F, 0L, Map.of(), Map.of());
    }

    @Override
    public long startedWork(@Nullable AEKeyType keyType) {
        return keyType == null ? 0L : startedWorkByType.getOrDefault(keyType, 0L);
    }

    @Override
    public long completedWork(@Nullable AEKeyType keyType) {
        return keyType == null ? 0L : completedWorkByType.getOrDefault(keyType, 0L);
    }

    private static Map<AEKeyType, Long> immutableNonNegativeMap(Map<AEKeyType, Long> source) {
        Objects.requireNonNull(source, "source");
        Map<AEKeyType, Long> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null && value >= 0L) result.put(key, value);
        });
        return Map.copyOf(result);
    }
}
