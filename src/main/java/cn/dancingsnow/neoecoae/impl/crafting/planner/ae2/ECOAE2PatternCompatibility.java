package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import cn.dancingsnow.neoecoae.api.crafting.IECOPlannerCompatiblePattern;
import java.util.Objects;
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
        IPatternDetails.IInput[] inputs;
        try {
            inputs = details.getInputs();
        } catch (RuntimeException | LinkageError failure) {
            return Assessment.rejected("pattern input metadata could not be read");
        }
        return assess(details, inputs, craftingService, level);
    }

    static Assessment assess(
        IPatternDetails details,
        IPatternDetails.IInput[] inputs,
        ICraftingService craftingService,
        Level level
    ) {
        Objects.requireNonNull(details, "details");
        if (inputs == null) {
            return Assessment.rejected("pattern returned null inputs");
        }
        if (ECOAE2NbtTearCompatibility.isProviderScoped(details, craftingService)) {
            return Assessment.rejected("provider-scoped NBT Tear input matching");
        }

        IECOPlannerCompatiblePattern.InputSemantics semantics;
        if (details instanceof IECOPlannerCompatiblePattern compatible) {
            semantics = compatible.getECOPlannerInputSemantics();
            return Assessment.accepted(
                Objects.requireNonNull(semantics, "ECO input semantics"), false, false
            );
        } else if (details.getClass() == AEProcessingPattern.class) {
            return Assessment.accepted(
                IECOPlannerCompatiblePattern.InputSemantics.CANONICAL_ONLY, false, false
            );
        } else if (details.getClass() == AECraftingPattern.class
            || details.getClass() == AESmithingTablePattern.class
            || details.getClass() == AEStonecuttingPattern.class) {
            return assessBuiltInAlternatives(inputs);
        } else if (hasOnlyCanonicalInputs(inputs, level)) {
            // The base IPatternDetails contract is sufficient for a fixed one-template slot.
            return Assessment.accepted(
                IECOPlannerCompatiblePattern.InputSemantics.UNIFORM_ALTERNATIVES, true, true
            );
        } else {
            return Assessment.rejected(
                "third-party pattern with dynamic or alternative inputs must implement "
                + IECOPlannerCompatiblePattern.class.getName()
            );
        }
    }

    static Assessment assessBuiltInAlternatives(IPatternDetails details) {
        return assessBuiltInAlternatives(details.getInputs());
    }

    private static Assessment assessBuiltInAlternatives(IPatternDetails.IInput[] inputs) {
        boolean hasSubstitutes = false;
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
        return Assessment.accepted(
            hasSubstitutes
                ? IECOPlannerCompatiblePattern.InputSemantics.MIXABLE_ALTERNATIVES
                : IECOPlannerCompatiblePattern.InputSemantics.CANONICAL_ONLY,
            hasSubstitutes,
            false
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
        String rejection
    ) {
        private static Assessment accepted(
            IECOPlannerCompatiblePattern.InputSemantics semantics,
            boolean includeFuzzyInventory,
            boolean requireUnitMultiplierForAlternatives
        ) {
            return new Assessment(
                true, semantics, includeFuzzyInventory, requireUnitMultiplierForAlternatives, ""
            );
        }

        private static Assessment rejected(String rejection) {
            return new Assessment(false, null, false, false, rejection);
        }
    }
}
