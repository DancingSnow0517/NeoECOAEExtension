package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOFastPathKey {
    private final ECOFastPathPatternKey patternKey;

    @Nullable
    private final ResourceLocation dimension;

    private final long reloadGeneration;
    private final List<SlotSignature> slots;
    private final int hash;

    private ECOFastPathKey(
        Object patternIdentity,
        @Nullable ResourceLocation dimension,
        long reloadGeneration,
        List<SlotSignature> slots
    ) {
        this.patternKey = new ECOFastPathPatternKey(patternIdentity, reloadGeneration);
        this.dimension = dimension;
        this.reloadGeneration = reloadGeneration;
        this.slots = List.copyOf(slots);
        this.hash = Objects.hash(patternKey, dimension, this.slots);
    }

    public static Optional<ECOFastPathKey> of(
        Object patternIdentity,
        KeyCounter[] craftingContainer,
        @Nullable Level level,
        long reloadGeneration
    ) {
        if (patternIdentity == null || craftingContainer == null) {
            return Optional.empty();
        }
        try {
            ResourceLocation dimension = level == null ? null : level.dimension().location();
            List<SlotSignature> slots = new ArrayList<>(craftingContainer.length);
            for (KeyCounter counter : craftingContainer) {
                List<EntrySignature> entries = new ArrayList<>();
                if (counter != null) {
                    for (Object2LongMap.Entry<AEKey> entry : counter) {
                        if (entry.getLongValue() > 0) {
                            entries.add(new EntrySignature(entry.getKey(), entry.getLongValue()));
                        }
                    }
                }
                entries.sort(EntrySignature::compareTo);
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
            && patternKey.equals(other.patternKey)
            && Objects.equals(dimension, other.dimension)
            && slots.equals(other.slots);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    boolean isForReloadGeneration(long candidate) {
        return reloadGeneration == candidate;
    }

    long reloadGeneration() {
        return reloadGeneration;
    }

    /**
     * Scope shared by concrete-state entries that may describe the same deterministic pattern. Concrete slot
     * contents remain deliberately excluded here; durability-aware lookup rebases those contents only after a
     * proved linear transition has matched every ordinary input and remainder.
     */
    boolean hasSamePatternScope(ECOFastPathKey other) {
        return other != null
            && reloadGeneration == other.reloadGeneration
            && patternKey.equals(other.patternKey)
            && Objects.equals(dimension, other.dimension);
    }

    /**
     * Preserves the original slot/layout discrimination while permitting only damage changes on an item key.
     * This is deliberately stricter than the pattern scope check because structurally equivalent provider
     * patterns can still arrange their inputs differently.
     */
    boolean hasSameSlotShapeIgnoringDamage(ECOFastPathKey other) {
        try {
            if (other == null || slots.size() != other.slots.size()) return false;
            for (int slot = 0; slot < slots.size(); slot++) {
                List<EntrySignature> left = slots.get(slot).entries;
                List<EntrySignature> right = other.slots.get(slot).entries;
                if (left.size() != right.size()) return false;
                boolean[] matched = new boolean[right.size()];
                for (EntrySignature candidate : left) {
                    boolean found = false;
                    for (int index = 0; index < right.size(); index++) {
                        if (matched[index] || candidate.amount != right.get(index).amount) continue;
                        if (sameKeyOrDamageState(candidate.key, right.get(index).key)) {
                            matched[index] = true;
                            found = true;
                            break;
                        }
                    }
                    if (!found) return false;
                }
            }
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    private static boolean sameKeyOrDamageState(AEKey left, AEKey right) {
        if (left.equals(right)) return true;
        if (!(left instanceof appeng.api.stacks.AEItemKey leftItem)
                || !(right instanceof appeng.api.stacks.AEItemKey rightItem)) return false;
        ItemStack leftStack = leftItem.toStack(1);
        ItemStack rightStack = rightItem.toStack(1);
        if (leftStack.isEmpty() || rightStack.isEmpty()
                || !leftStack.isDamageableItem() || !rightStack.isDamageableItem()) return false;
        leftStack.setDamageValue(0);
        rightStack.setDamageValue(0);
        return ItemStack.isSameItemSameComponents(leftStack, rightStack);
    }

    public ECOFastPathPatternKey patternKey() {
        return patternKey;
    }

    private record SlotSignature(List<EntrySignature> entries) {
        private SlotSignature {
            entries = List.copyOf(entries);
        }
    }

    private static final class EntrySignature implements Comparable<EntrySignature> {
        private final AEKey key;
        private final long amount;

        @Nullable
        private String sortId;

        private EntrySignature(AEKey key, long amount) {
            this.key = key;
            this.amount = amount;
        }

        @Override
        public int compareTo(EntrySignature other) {
            int keyCompare = sortId().compareTo(other.sortId());
            if (keyCompare != 0) {
                return keyCompare;
            }
            return Long.compare(this.amount, other.amount);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof EntrySignature other
                && amount == other.amount && key.equals(other.key);
        }

        @Override
        public int hashCode() {
            return 31 * key.hashCode() + Long.hashCode(amount);
        }

        private String sortId() {
            if (sortId == null) {
                sortId = ECOFastPathStacks.keySortId(key);
            }
            return sortId;
        }
    }
}
