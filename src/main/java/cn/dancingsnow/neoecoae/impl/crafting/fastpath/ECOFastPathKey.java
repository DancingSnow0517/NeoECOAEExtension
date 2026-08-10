package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ECOFastPathKey {
    @Nullable private final ResourceLocation dimension;

    private final long reloadGeneration;
    private final long patternFingerprint;
    private final long slotsFingerprint;
    private final int hash;

    private ECOFastPathKey(
            @Nullable ResourceLocation dimension,
            long reloadGeneration,
            long patternFingerprint,
            long slotsFingerprint) {
        this.dimension = dimension;
        this.reloadGeneration = reloadGeneration;
        this.patternFingerprint = patternFingerprint;
        this.slotsFingerprint = slotsFingerprint;
        this.hash = Objects.hash(dimension, reloadGeneration, patternFingerprint, slotsFingerprint);
    }

    public static Optional<ECOFastPathKey> of(
            Object patternIdentity, KeyCounter[] craftingContainer, @Nullable Level level, long reloadGeneration) {
        if (patternIdentity == null || craftingContainer == null) {
            return Optional.empty();
        }
        try {
            ResourceLocation dimension =
                    level == null ? null : level.dimension().location();
            long slotsFingerprint = mix64(craftingContainer.length);
            for (KeyCounter counter : craftingContainer) {
                long entrySum = 0L;
                long entryXor = 0L;
                int entryCount = 0;
                if (counter != null) {
                    for (Object2LongMap.Entry<AEKey> entry : counter) {
                        if (entry.getLongValue() > 0) {
                            long entryFingerprint = fingerprintEntry(entry.getKey(), entry.getLongValue());
                            entrySum += entryFingerprint;
                            entryXor ^= Long.rotateLeft(entryFingerprint, (int) entryFingerprint & 63);
                            entryCount++;
                        }
                    }
                }
                long slotFingerprint = mix64(entrySum) ^ Long.rotateLeft(mix64(entryXor), 21) ^ mix64(entryCount);
                slotsFingerprint = mix64(slotsFingerprint ^ slotFingerprint);
            }
            return Optional.of(new ECOFastPathKey(
                    dimension, reloadGeneration, fingerprintKeyLike(patternIdentity), slotsFingerprint));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ECOFastPathKey other)) {
            return false;
        }
        // This is a cache lookup signature, not the final correctness check. Every positive
        // lookup is verified against the extracted execution or batch request before use. Keeping
        // equality primitive-only avoids repeatedly comparing the encoded pattern's component map.
        return reloadGeneration == other.reloadGeneration
                && patternFingerprint == other.patternFingerprint
                && slotsFingerprint == other.slotsFingerprint
                && Objects.equals(dimension, other.dimension);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    private static long fingerprintEntry(AEKey key, long amount) {
        long fingerprint = fingerprintKeyLike(key);
        return mix64(fingerprint ^ Long.rotateLeft(mix64(amount), 29));
    }

    private static long fingerprintKeyLike(Object value) {
        long fingerprint = mix64(value.getClass().hashCode());
        fingerprint ^= Long.rotateLeft(mix64(value.hashCode()), 17);
        if (value instanceof AEKey key) {
            fingerprint ^= Long.rotateLeft(mix64(key.getType().getId().hashCode()), 31);
            fingerprint ^= Long.rotateLeft(mix64(key.getId().hashCode()), 47);
        }
        return mix64(fingerprint);
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }
}
