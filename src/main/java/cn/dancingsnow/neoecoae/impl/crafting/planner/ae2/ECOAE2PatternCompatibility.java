package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import cn.dancingsnow.neoecoae.api.crafting.IECOPlannerCompatiblePattern;
import cn.dancingsnow.neoecoae.compat.ae2.AE2LTOverloadPatternCompatibility;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Defines the PatternDetails semantics that can be represented by ECO's immutable operation model. */
final class ECOAE2PatternCompatibility {
    private ECOAE2PatternCompatibility() {
    }

    static Assessment assess(
        IPatternDetails details,
        ICraftingService craftingService,
        Level level
    ) {
        return assess(details, craftingService, level, Set.of());
    }

    static Assessment assess(
        IPatternDetails details,
        ICraftingService craftingService,
        Level level,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        return assess(details, craftingService, level, fuzzyItemIds, false);
    }

    static Assessment assess(
        IPatternDetails details,
        ICraftingService craftingService,
        Level level,
        Set<ResourceLocation> fuzzyItemIds,
        boolean ignoreSubstitutionPatterns
    ) {
        IPatternDetails.IInput[] inputs;
        try {
            inputs = details.getInputs();
        } catch (RuntimeException | LinkageError failure) {
            return Assessment.rejected("pattern input metadata could not be read");
        }
        return assess(details, inputs, craftingService, level, fuzzyItemIds, ignoreSubstitutionPatterns);
    }

    static Assessment assess(
        IPatternDetails details,
        IPatternDetails.IInput[] inputs,
        ICraftingService craftingService,
        Level level,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        return assess(details, inputs, craftingService, level, fuzzyItemIds, false);
    }

    static Assessment assess(
        IPatternDetails details,
        IPatternDetails.IInput[] inputs,
        ICraftingService craftingService,
        Level level,
        Set<ResourceLocation> fuzzyItemIds,
        boolean ignoreSubstitutionPatterns
    ) {
        Objects.requireNonNull(details, "details");
        if (inputs == null) {
            return Assessment.rejected("pattern returned null inputs");
        }
        boolean configuredFuzzyInput = hasConfiguredFuzzyInput(inputs, fuzzyItemIds);
        boolean ignoredSubstitution = ignoreSubstitutionPatterns && isSubstitutionPattern(details);
        if (ECOAE2NbtTearCompatibility.isProviderScoped(details, craftingService)
            && !configuredFuzzyInput) {
            return Assessment.rejected("provider_scoped_nbt");
        }

        try {
            if (details instanceof AECraftingPattern
                && AE2PatternIntrospection.classifyPatternEligibility(details)
                    == AE2PatternIntrospection.PatternEligibility.SPECIAL_RECIPE
                && (!ignoredSubstitution || !(details instanceof AESmithingTablePattern))) {
                return Assessment.rejected("special_recipe");
            }
        } catch (RuntimeException | LinkageError failure) {
            return Assessment.rejected("pattern_compatibility_exception");
        }

        if (ignoredSubstitution) {
            return Assessment.accepted(
                IECOPlannerCompatiblePattern.InputSemantics.CANONICAL_ONLY,
                false,
                true,
                true
            );
        }

        IECOPlannerCompatiblePattern.InputSemantics semantics;
        if (details instanceof IECOPlannerCompatiblePattern compatible) {
            try {
                semantics = compatible.getECOPlannerInputSemantics();
            } catch (RuntimeException | LinkageError failure) {
                return Assessment.rejected("ECO input semantics could not be read");
            }
            if (semantics == null) {
                return Assessment.rejected("ECO input semantics returned null");
            }
            return Assessment.accepted(semantics, false, true);
        } else if (details.getClass() == AEProcessingPattern.class) {
            return Assessment.accepted(
                IECOPlannerCompatiblePattern.InputSemantics.CANONICAL_ONLY, false, true
            );
        } else if (isKnownBuiltIn(details)) {
            return assessBuiltInAlternatives(details, inputs);
        } else if (hasOnlyCanonicalOrConfiguredFuzzyInputs(
            details, inputs, level, fuzzyItemIds
        )) {
            // A marked input explicitly opts this third-party pattern into component-insensitive
            // planning; every other input must remain a fixed strict input.
            return Assessment.accepted(
                configuredFuzzyInput
                    ? IECOPlannerCompatiblePattern.InputSemantics.MIXABLE_ALTERNATIVES
                    : IECOPlannerCompatiblePattern.InputSemantics.CANONICAL_ONLY,
                false,
                false
            );
        } else {
            return Assessment.rejected(
                "third-party pattern with dynamic or alternative inputs must implement "
                + IECOPlannerCompatiblePattern.class.getName()
            );
        }
    }

    static Assessment assessBuiltInAlternatives(IPatternDetails details) {
        try {
            return assessBuiltInAlternatives(details, details.getInputs());
        } catch (RuntimeException | LinkageError failure) {
            return Assessment.rejected("built-in input metadata could not be read");
        }
    }

    private static boolean hasConfiguredFuzzyInput(
        IPatternDetails.IInput[] inputs,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        if (fuzzyItemIds == null || fuzzyItemIds.isEmpty()) {
            return false;
        }
        try {
            for (IPatternDetails.IInput input : inputs) {
                if (input == null) {
                    continue;
                }
                GenericStack[] possible = input.getPossibleInputs();
                if (possible == null) {
                    continue;
                }
                for (GenericStack candidate : possible) {
                    if (candidate != null
                        && candidate.what().getType().equals(appeng.api.stacks.AEKeyType.items())
                        && fuzzyItemIds.contains(candidate.what().getId())) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        return false;
    }

    private static boolean hasOnlyCanonicalOrConfiguredFuzzyInputs(
        IPatternDetails details,
        IPatternDetails.IInput[] inputs,
        Level level,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        try {
            for (int slot = 0; slot < inputs.length; slot++) {
                IPatternDetails.IInput input = inputs[slot];
                if (input == null) {
                    return false;
                }
                if (hasConfiguredFuzzyTemplate(input, fuzzyItemIds)
                    || AE2LTOverloadPatternCompatibility.ignoresComponents(details, slot)) {
                    continue;
                }
                GenericStack[] possible = input.getPossibleInputs();
                if (possible == null
                    || possible.length != 1
                    || possible[0] == null
                    || possible[0].amount() <= 0L
                    || !input.isValid(possible[0].what(), level)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean hasConfiguredFuzzyTemplate(
        IPatternDetails.IInput input,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        if (fuzzyItemIds == null || fuzzyItemIds.isEmpty()) {
            return false;
        }
        GenericStack[] possible = input.getPossibleInputs();
        if (possible == null) {
            return false;
        }
        for (GenericStack candidate : possible) {
            if (candidate != null
                && candidate.what().getType().equals(appeng.api.stacks.AEKeyType.items())
                && fuzzyItemIds.contains(candidate.what().getId())) {
                return true;
            }
        }
        return false;
    }

    static boolean isKnownBuiltIn(IPatternDetails details) {
        return details.getClass() == AEProcessingPattern.class
            || details.getClass() == AECraftingPattern.class
            || details.getClass() == AESmithingTablePattern.class
            || details.getClass() == AEStonecuttingPattern.class;
    }

    static boolean isSubstitutionPattern(IPatternDetails details) {
        return details instanceof AECraftingPattern crafting
                && (crafting.canSubstitute() || crafting.canSubstituteFluids())
            || details instanceof AESmithingTablePattern smithing && smithing.canSubstitute()
            || details instanceof AEStonecuttingPattern stonecutting && stonecutting.canSubstitute();
    }

    static IPatternDetails.IInput normalizeSubstitutionInput(
        IPatternDetails details,
        int slot,
        IPatternDetails.IInput input
    ) {
        GenericStack canonical = canonicalInput(details, slot, input);
        if (canonical == null) {
            return input;
        }
        return new IPatternDetails.IInput() {
            @Override
            public GenericStack[] getPossibleInputs() {
                return new GenericStack[] { canonical };
            }

            @Override
            public long getMultiplier() {
                return input.getMultiplier();
            }

            @Override
            public boolean isValid(appeng.api.stacks.AEKey key, Level level) {
                return key.matches(canonical);
            }

            @Override
            public appeng.api.stacks.AEKey getRemainingKey(appeng.api.stacks.AEKey template) {
                return input.getRemainingKey(template);
            }
        };
    }

    private static GenericStack canonicalInput(
        IPatternDetails details,
        int slot,
        IPatternDetails.IInput input
    ) {
        GenericStack[] possible = input.getPossibleInputs();
        if (possible != null && possible.length > 0 && possible[0] != null
            && !possible[0].what().getType().equals(appeng.api.stacks.AEKeyType.fluids())) {
            return possible[0];
        }
        if (!(details instanceof AECraftingPattern crafting)) {
            return null;
        }
        List<GenericStack> sparseInputs = crafting.getSparseInputs();
        Map<AEKey, Long> condensedAmounts = new LinkedHashMap<>();
        for (GenericStack sparse : sparseInputs) {
            if (sparse != null) {
                condensedAmounts.merge(sparse.what(), sparse.amount(), Long::sum);
            }
        }
        List<GenericStack> condensedInputs = new ArrayList<>(condensedAmounts.size());
        condensedAmounts.forEach((key, amount) -> condensedInputs.add(new GenericStack(key, amount)));
        if (slot < 0 || slot >= condensedInputs.size()) {
            return null;
        }
        GenericStack condensed = condensedInputs.get(slot);
        for (GenericStack sparse : sparseInputs) {
            if (sparse != null && sparse.what().equals(condensed.what())) {
                return new GenericStack(sparse.what(), sparse.amount());
            }
        }
        return new GenericStack(condensed.what(), 1L);
    }

    private static Assessment assessBuiltInAlternatives(IPatternDetails.IInput[] inputs) {
        return assessBuiltInAlternatives(null, inputs);
    }

    private static Assessment assessBuiltInAlternatives(
        IPatternDetails details,
        IPatternDetails.IInput[] inputs
    ) {
        boolean hasSubstitutes = false;
        try {
            for (IPatternDetails.IInput input : inputs) {
                if (input == null) {
                    return Assessment.rejected("pattern has a null input slot");
                }
                GenericStack[] possible = input.getPossibleInputs();
                if (possible == null) {
                    return Assessment.rejected("pattern input returned null alternatives");
                }
                if (possible.length <= 1) {
                    continue;
                }
                hasSubstitutes = true;
            }
            if (details instanceof AECraftingPattern crafting && crafting.canSubstitute()) {
                hasSubstitutes = true;
            } else if (details instanceof AEStonecuttingPattern stonecutting && stonecutting.canSubstitute()) {
                hasSubstitutes = true;
            }
        } catch (RuntimeException | LinkageError failure) {
            return Assessment.rejected("built-in input alternatives could not be read");
        }
        return Assessment.accepted(
            hasSubstitutes
                ? IECOPlannerCompatiblePattern.InputSemantics.MIXABLE_ALTERNATIVES
                : IECOPlannerCompatiblePattern.InputSemantics.CANONICAL_ONLY,
            hasSubstitutes,
            true
        );
    }

    private static boolean hasOnlyCanonicalInputs(IPatternDetails.IInput[] inputs, Level level) {
        try {
            for (IPatternDetails.IInput input : inputs) {
                GenericStack[] possible = input.getPossibleInputs();
                if (possible.length != 1
                    || possible[0] == null
                    || possible[0].amount() <= 0L
                    || !input.isValid(possible[0].what(), level)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    record Assessment(
        boolean compatible,
        IECOPlannerCompatiblePattern.InputSemantics inputSemantics,
        boolean includeFuzzyInventory,
        boolean requireUnitMultiplierForAlternatives,
        boolean stateExpansionAllowed,
        boolean normalizeSubstitutionInputs,
        String rejection
    ) {
        private static Assessment accepted(
            IECOPlannerCompatiblePattern.InputSemantics semantics,
            boolean includeFuzzyInventory,
            boolean stateExpansionAllowed
        ) {
            return accepted(semantics, includeFuzzyInventory, stateExpansionAllowed, false);
        }

        private static Assessment accepted(
            IECOPlannerCompatiblePattern.InputSemantics semantics,
            boolean includeFuzzyInventory,
            boolean stateExpansionAllowed,
            boolean normalizeSubstitutionInputs
        ) {
            return new Assessment(
                true,
                semantics,
                includeFuzzyInventory,
                false,
                stateExpansionAllowed,
                normalizeSubstitutionInputs,
                ""
            );
        }

        private static Assessment rejected(String rejection) {
            return new Assessment(false, null, false, false, false, false, rejection);
        }
    }
}
