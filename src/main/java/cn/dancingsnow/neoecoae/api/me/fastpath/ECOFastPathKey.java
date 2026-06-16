package cn.dancingsnow.neoecoae.api.me.fastpath;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ECOFastPathKey {
    private final Object patternIdentity;

    @Nullable private final ResourceLocation dimension;

    private final long reloadGeneration;
    private final List<SlotSignature> slots;
    private final int hash;

    private ECOFastPathKey(
            Object patternIdentity,
            @Nullable ResourceLocation dimension,
            long reloadGeneration,
            List<SlotSignature> slots) {
        this.patternIdentity = patternIdentity;
        this.dimension = dimension;
        this.reloadGeneration = reloadGeneration;
        this.slots = List.copyOf(slots);
        this.hash = Objects.hash(patternIdentity, dimension, reloadGeneration, this.slots);
    }

    public static Optional<ECOFastPathKey> of(
            Object patternIdentity, KeyCounter[] craftingContainer, @Nullable Level level, long reloadGeneration) {
        if (patternIdentity == null || craftingContainer == null) {
            return Optional.empty();
        }
        try {
            ResourceLocation dimension =
                    level == null ? null : level.dimension().location();
            List<SlotSignature> slots = new ArrayList<>(craftingContainer.length);
            for (KeyCounter counter : craftingContainer) {
                List<EntrySignature> entries = new ArrayList<>();
                if (counter != null) {
                    for (Object2LongMap.Entry<AEKey> entry : counter) {
                        if (entry.getLongValue() > 0) {
                            AEKey key = entry.getKey();
                            entries.add(
                                    new EntrySignature(key, entry.getLongValue(), ECOFastPathStacks.keySortId(key)));
                        }
                    }
                }
                entries.sort(Comparator.comparing(EntrySignature::sortId).thenComparingLong(EntrySignature::amount));
                slots.add(new SlotSignature(entries));
            }
            return Optional.of(new ECOFastPathKey(patternIdentity, dimension, reloadGeneration, slots));
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
        return reloadGeneration == other.reloadGeneration
                && Objects.equals(patternIdentity, other.patternIdentity)
                && Objects.equals(dimension, other.dimension)
                && slots.equals(other.slots);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    private record SlotSignature(List<EntrySignature> entries) {
        private SlotSignature {
            entries = List.copyOf(entries);
        }
    }

    private record EntrySignature(AEKey key, long amount, String sortId) {}
}
