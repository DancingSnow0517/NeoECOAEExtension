package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Output-key index and bounded material-contract memoization for one available recipe list. */
final class LargeWorkstationRecipeIndex {
    private static final int CACHE_LIMIT = 512;
    private static final CachedMatch NO_MATCH = new CachedMatch(null);
    private final Map<AEKey, List<LargeWorkstationRecipe.Prepared>> byOutput = new HashMap<>();
    private final List<LargeWorkstationRecipe.Prepared> withoutOutputs = new ArrayList<>();
    private final Map<OutputQuery, Candidates> candidates = boundedCache();
    private final Map<MatchQuery, CachedMatch> matches = boundedCache();

    LargeWorkstationRecipeIndex(List<LargeWorkstationRecipe> recipes) {
        this(recipes, new IdentityHashMap<>());
    }

    LargeWorkstationRecipeIndex(List<LargeWorkstationRecipe> recipes,
            Map<LargeWorkstationRecipe, LargeWorkstationRecipe.Prepared> prepared) {
        for (var recipe : recipes) {
            var contract = prepared.computeIfAbsent(recipe, LargeWorkstationRecipe::prepare);
            boolean hasOutput = false;
            for (var output : contract.outputs) {
                if (output.getLongValue() == 0) continue;
                byOutput.computeIfAbsent(output.getKey(), ignored -> new ArrayList<>()).add(contract);
                hasOutput = true;
            }
            if (!hasOutput) withoutOutputs.add(contract);
        }
    }

    @Nullable
    synchronized LargeWorkstationRecipes.PatternMatch find(KeyCounter inputs, KeyCounter outputs, boolean scaled) {
        var output = new OutputQuery(snapshot(outputs), scaled);
        var available = candidates.get(output);
        if (available == null) {
            available = candidates(outputs, scaled);
            candidates.put(output, available);
        }
        if (available.matches.isEmpty()) return null;

        MatchQuery query = null;
        if (available.memoizable) {
            query = new MatchQuery(snapshot(inputs), output);
            var cached = matches.get(query);
            if (cached != null) return cached.match;
        }
        for (var candidate : available.matches) {
            boolean accepted;
            try {
                accepted = candidate.contract.matchesInputs(inputs, candidate.match.multiplier());
            } catch (ArithmeticException overflow) {
                if (!scaled) throw overflow;
                accepted = false;
            }
            if (accepted) {
                if (query != null) matches.put(query, new CachedMatch(candidate.match));
                return candidate.match;
            }
        }
        if (query != null) matches.put(query, NO_MATCH);
        return null;
    }

    private Candidates candidates(KeyCounter outputs, boolean scaled) {
        List<LargeWorkstationRecipe.Prepared> possible = null;
        for (var output : outputs) {
            if (output.getLongValue() == 0) continue;
            var bucket = byOutput.get(output.getKey());
            if (bucket == null) return Candidates.EMPTY;
            if (possible == null || bucket.size() < possible.size()) possible = bucket;
        }
        if (possible == null) {
            if (scaled) return Candidates.EMPTY;
            possible = withoutOutputs;
        }
        var result = new ArrayList<Candidate>();
        boolean memoizable = true;
        for (var contract : possible) {
            long multiplier;
            if (scaled) {
                multiplier = contract.outputMultiplier(outputs);
                if (multiplier == 0 || !validScale(contract.recipe, multiplier)) continue;
            } else {
                if (!contract.matchesOutputs(outputs)) continue;
                multiplier = 1;
            }
            result.add(new Candidate(contract, new LargeWorkstationRecipes.PatternMatch(contract.recipe, multiplier)));
            memoizable &= contract.memoizable;
        }
        result.sort((first, second) -> LargeWorkstationRecipes.compareMatches(first.match, second.match));
        return new Candidates(List.copyOf(result), memoizable);
    }

    private static boolean validScale(LargeWorkstationRecipe recipe, long multiplier) {
        try {
            Math.multiplyExact(recipe.energy(), multiplier);
            Math.multiplyExact(recipe.feEnergy(), multiplier);
            Math.toIntExact(Math.multiplyExact((long) recipe.processingTicks(), multiplier));
            for (var extra : recipe.extraInputs()) Math.multiplyExact(extra.amount(), multiplier);
            return true;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    private static Map<AEKey, Long> snapshot(KeyCounter counter) {
        var result = new HashMap<AEKey, Long>();
        for (var entry : counter) {
            if (entry.getLongValue() != 0) result.put(entry.getKey(), entry.getLongValue());
        }
        // Never retain a caller's mutable counter or iterator entries. AEKey equality includes components.
        return Map.copyOf(result);
    }

    private static <K, V> Map<K, V> boundedCache() {
        return new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CACHE_LIMIT;
            }
        };
    }

    private record OutputQuery(Map<AEKey, Long> outputs, boolean scaled) {}
    private record MatchQuery(Map<AEKey, Long> inputs, OutputQuery output) {}
    private record CachedMatch(@Nullable LargeWorkstationRecipes.PatternMatch match) {}
    private record Candidate(LargeWorkstationRecipe.Prepared contract, LargeWorkstationRecipes.PatternMatch match) {}
    private record Candidates(List<Candidate> matches, boolean memoizable) {
        private static final Candidates EMPTY = new Candidates(List.of(), true);
    }
}
