package cn.dancingsnow.neoecoae.api.me.pattern;

import appeng.api.crafting.IPatternDetails;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Explicit provider-identity delegation; ordinary patterns require no registration. */
public final class ECOPatternWrappers {
    private static final int MAX_DEPTH = 128;
    private static volatile List<Entry> entries = List.of();

    private ECOPatternWrappers() {}

    /** Implement directly when a task wrapper is registered under another physical pattern. */
    public interface Wrapper {
        IPatternDetails ecoProviderPattern();
    }

    /**
     * Register a known optional-mod contract during common initialization. Higher priority wins;
     * equal priorities use ID order. Accessors must be pure and return a non-null physical delegate.
     */
    public static synchronized <T> void register(ResourceLocation id, int priority, Class<T> contract,
            Function<? super T, ? extends IPatternDetails> accessor) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(accessor, "accessor");
        if (entries.stream().anyMatch(entry -> entry.id().equals(id))) {
            throw new IllegalArgumentException("Duplicate pattern wrapper adapter: " + id);
        }
        var updated = new ArrayList<>(entries);
        updated.add(new Entry(id, priority, contract, target -> accessor.apply(contract.cast(target))));
        updated.sort(Comparator.comparingInt(Entry::priority).reversed().thenComparing(entry -> entry.id().toString()));
        entries = List.copyOf(updated);
    }

    /** Returns null for a malformed, cyclic or failing registered wrapper. */
    @Nullable
    public static IPatternDetails unwrap(@Nullable IPatternDetails original) {
        List<Object> chain = trace(original);
        if (chain.isEmpty()) return null;
        Object last = chain.getLast();
        return last instanceof IPatternDetails pattern ? pattern : null;
    }

    /** Includes each identity once, stopping at malformed delegation. */
    public static List<Object> wrapperChain(@Nullable Object original) {
        var chain = trace(original);
        if (!chain.isEmpty() && chain.getLast() == null) chain.removeLast();
        return List.copyOf(chain);
    }

    private static List<Object> trace(@Nullable Object original) {
        List<Entry> snapshot = entries;
        List<Object> result = new ArrayList<>();
        ReferenceSet<Object> seen = new ReferenceOpenHashSet<>();
        Object current = original;
        while (current != null && seen.add(current) && result.size() < MAX_DEPTH) {
            result.add(current);
            try {
                if (current instanceof Wrapper wrapper) {
                    current = wrapper.ecoProviderPattern();
                } else {
                    Entry match = null;
                    for (Entry entry : snapshot) {
                        if (entry.contract().isInstance(current)) { match = entry; break; }
                    }
                    if (match == null) return result;
                    current = match.accessor().apply(current);
                }
            } catch (RuntimeException | LinkageError unavailable) {
                break;
            }
        }
        // Failure never authorizes optimized execution of an uncertain physical pattern.
        result.add(null);
        return result;
    }

    private record Entry(ResourceLocation id, int priority, Class<?> contract,
            Function<Object, IPatternDetails> accessor) {}
}
