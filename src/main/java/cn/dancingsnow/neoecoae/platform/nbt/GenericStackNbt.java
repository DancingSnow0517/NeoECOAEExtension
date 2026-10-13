package cn.dancingsnow.neoecoae.platform.nbt;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;

/**
 * Persisted GenericStack lists: a TAG_COMPOUND ListTag with one {@link GenericStack#writeTag} compound per entry, in
 * list order. Save data depends on this exact layout, so writers and readers here must not change it.
 */
public final class GenericStackNbt {
    private GenericStackNbt() {}

    /** Writes the non-null entries whose amount is positive, in iteration order. Other entries are dropped. */
    public static ListTag writePositive(HolderLookup.Provider registries, Iterable<GenericStack> stacks) {
        ListTag tag = new ListTag();
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0) {
                tag.add(GenericStack.writeTag(registries, stack));
            }
        }
        return tag;
    }

    /** Map form of {@link #writePositive}: each key/amount pair becomes one entry, and non-positive amounts are dropped. */
    public static ListTag writePositiveAmounts(HolderLookup.Provider registries, Map<AEKey, Long> amounts) {
        ListTag tag = new ListTag();
        amounts.forEach((key, amount) -> {
            if (amount > 0L) {
                tag.add(GenericStack.writeTag(registries, new GenericStack(key, amount)));
            }
        });
        return tag;
    }

    /**
     * Reads a persisted list. Returns empty when the list holds more than {@code maxSize} entries, when
     * {@code requireNonEmpty} is set and the list is empty, when any entry decodes to null, when {@code validator}
     * rejects the decoded stacks, or when decoding or validation throws.
     */
    public static Optional<List<GenericStack>> readBounded(
        HolderLookup.Provider registries,
        ListTag tag,
        int maxSize,
        boolean requireNonEmpty,
        Predicate<List<GenericStack>> validator
    ) {
        if (tag.size() > maxSize || requireNonEmpty && tag.isEmpty()) {
            return Optional.empty();
        }
        try {
            List<GenericStack> stacks = new ArrayList<>(tag.size());
            for (int i = 0; i < tag.size(); i++) {
                GenericStack stack = GenericStack.readTag(registries, tag.getCompound(i));
                if (stack == null) {
                    return Optional.empty();
                }
                stacks.add(stack);
            }
            return validator.test(stacks) ? Optional.of(stacks) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
