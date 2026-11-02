package cn.dancingsnow.neoecoae.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Explicit ECO item-ID matching for processing patterns; crafting recipe predicates stay authoritative. */
public final class ECOProcessingComponentMatching {
    private static final ThreadLocal<Set<ResourceLocation>> DISPATCH_ITEMS = new ThreadLocal<>();
    private static final Scope EMPTY_SCOPE = () -> {};

    private ECOProcessingComponentMatching() {}

    public static boolean sameItem(AEKey expected, AEKey actual) {
        return expected instanceof AEItemKey wanted && actual instanceof AEItemKey candidate
            && wanted.getItem() == candidate.getItem();
    }

    public static boolean enabled(IPatternDetails pattern, AEKey expected, Set<ResourceLocation> items) {
        // Subclasses may impose their own machine/recipe constraints. Only AE2's plain processing contract is widened.
        return pattern != null && pattern.getClass() == AEProcessingPattern.class
            && expected instanceof AEItemKey item
            && items.contains(BuiltInRegistries.ITEM.getKey(item.getItem()));
    }

    public static boolean isValid(IPatternDetails pattern, IPatternDetails.IInput input,
            AEKey expected, AEKey actual, Level level) {
        // Callers enter this method only for an input already marked component-insensitive by the compiler.
        return pattern != null && pattern.getClass() == AEProcessingPattern.class && sameItem(expected, actual)
            || input == null || input.isValid(actual, level);
    }

    public static Scope openDispatch(Set<ResourceLocation> items) {
        var previous = DISPATCH_ITEMS.get();
        if (previous == null && items.isEmpty()) return EMPTY_SCOPE;
        if (items.isEmpty()) DISPATCH_ITEMS.remove();
        else DISPATCH_ITEMS.set(items);
        return () -> {
            if (previous == null) DISPATCH_ITEMS.remove();
            else DISPATCH_ITEMS.set(previous);
        };
    }

    public interface Scope extends AutoCloseable {
        @Override void close();
    }

    /** Preserves sparse processing-slot order while sending the actual reserved component variants. */
    public static boolean pushInputs(AEProcessingPattern pattern, KeyCounter[] inputs,
            IPatternDetails.PatternInputSink sink) {
        var items = DISPATCH_ITEMS.get();
        if (items == null || pattern.getClass() != AEProcessingPattern.class) return false;
        var definitions = pattern.getInputs();
        if (inputs.length != definitions.length) throw new IllegalArgumentException("Processing input slot mismatch");
        boolean changed = false;
        for (int slot = 0; slot < definitions.length; slot++) {
            var expected = definitions[slot].getPossibleInputs()[0].what();
            if (!enabled(pattern, expected, items)) continue;
            for (var entry : inputs[slot]) {
                if (!expected.equals(entry.getKey())) changed = true;
            }
        }
        if (!changed) return false;
        var remaining = new KeyCounter[inputs.length];
        for (int slot = 0; slot < inputs.length; slot++) {
            remaining[slot] = new KeyCounter();
            remaining[slot].addAll(inputs[slot]);
        }
        var emissions = new ArrayList<GenericStack>();
        for (var sparse : pattern.getSparseInputs()) {
            if (sparse == null) continue;
            int slot = -1;
            for (int i = 0; i < definitions.length; i++) {
                if (sparse.what().equals(definitions[i].getPossibleInputs()[0].what())) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) throw new IllegalArgumentException("Unknown processing input");
            long needed = sparse.amount();
            // Prefer the exact key, then consume other explicitly allowed variants from this condensed slot only.
            var keys = new ArrayList<AEKey>();
            keys.add(sparse.what());
            for (var entry : remaining[slot]) {
                if (!entry.getKey().equals(sparse.what())) keys.add(entry.getKey());
            }
            for (var key : keys) {
                if (needed == 0) break;
                if (!sparse.what().equals(key)
                        && !(enabled(pattern, sparse.what(), items) && sameItem(sparse.what(), key))) continue;
                long take = Math.min(needed, remaining[slot].get(key));
                if (take <= 0) continue;
                emissions.add(new GenericStack(key, take));
                remaining[slot].remove(key, take);
                needed -= take;
            }
            if (needed != 0) throw new IllegalArgumentException("Incomplete processing input");
        }
        for (var counter : remaining) {
            counter.removeZeros();
            if (!counter.isEmpty()) throw new IllegalArgumentException("Excess processing input");
        }
        // Validate the entire table before the first physical insertion.
        emissions.forEach(stack -> sink.pushInput(stack.what(), stack.amount()));
        return true;
    }
}
