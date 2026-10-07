package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import org.jetbrains.annotations.Nullable;

/**
 * Static-planning contract for Useless Mod's dynamic-component and omniversal patterns.
 *
 * <p>Useless deliberately permits selected input slots to match by item id or tag. The encoded primary stack is
 * still a valid concrete choice, so ECO may commit the plan to it and record substitution semantics. Dynamic output
 * quantities are also fixed: Useless's CPU output bridge claims actual component variants against the encoded
 * template. Fixed-output recipes without returned inputs can also form a verified cycle using the selected
 * concrete ingredients. Dynamic output components and relaxed reusable returns still need a separate proof.
 */
public final class UselessPatternSemanticAdapter implements PatternSemanticAdapter {
    private final AE2PatternSemanticAdapter delegate = new AE2PatternSemanticAdapter();

    @Override
    public boolean supports(IPatternDetails pattern) {
        return UselessPatternApi.dynamicView(pattern) != null;
    }

    @Override
    public PatternSemantics analyze(IPatternDetails pattern) {
        UselessDynamicPatternView dynamic = UselessPatternApi.dynamicView(pattern);
        if (dynamic == null) {
            return PatternSemantics.unsupported(pattern, safeDefinition(pattern), "USELESS_DYNAMIC_CONTRACT_MISSING");
        }
        PatternSemantics base = delegate.analyze(pattern);
        if (!base.supported()) return base;
        try {
            boolean dynamicOutputs = dynamic.neoecoae$usesDynamicOutputs();
            boolean relaxedInput = false;
            IPatternDetails.IInput[] inputs = pattern.getInputs();
            for (int slot = 0; slot < inputs.length; slot++) {
                if (dynamic.neoecoae$isItemIdInput(slot) || dynamic.neoecoae$isTagInput(slot)
                        || dynamic.neoecoae$isFluidTagInput(slot)) {
                    relaxedInput = true;
                    break;
                }
            }
            return new PatternSemantics(pattern, base.physicalDefinition(), base.consumedInputs(),
                base.producedOutputs(), base.returnedOutputs(), base.feedbackEdges(),
                relaxedInput || dynamicOutputs ? PatternSemantics.MatchingMode.SUBSTITUTION : base.matchingMode(),
                PatternSemantics.ExecutionRestriction.NONE, true,
                // Input alternatives broaden what the machine accepts; they do not change a fixed output.
                // Commit ordinary consumed inputs to concrete keys, while excluding relaxed returned stock
                // whose component identity could change during a lap.
                base.cycleSafe() && !dynamicOutputs
                    && (!relaxedInput || base.returnedOutputs().isEmpty()), null);
        } catch (RuntimeException rejected) {
            return PatternSemantics.unsupported(pattern, base.physicalDefinition(),
                "USELESS_SEMANTIC_ANALYSIS_FAILED:" + rejected.getClass().getSimpleName());
        }
    }

    @Override
    public boolean ignoresComponents(IPatternDetails pattern, int inputSlot) {
        UselessDynamicPatternView dynamic = UselessPatternApi.dynamicView(pattern);
        return dynamic != null && inputSlot >= 0 && inputSlot < pattern.getInputs().length
            && dynamic.neoecoae$isItemIdInput(inputSlot);
    }

    /** Extra recipe predicates still apply to an item-ID slot (for example EnderIO soul inputs). */
    public static boolean acceptsComponentVariant(IPatternDetails pattern, IPatternDetails.IInput input,
            AEKey candidate) {
        return UselessPatternApi.dynamicView(pattern) == null || input == null || input.isValid(candidate, null);
    }

    @Override
    public AEKey preferredInputKey(IPatternDetails pattern, int inputSlot, AEKey encodedKey,
            ICraftingService craftingService) {
        if (!ignoresComponents(pattern, inputSlot)) return encodedKey;
        IPatternDetails.IInput input = pattern.getInputs()[inputSlot];
        // Remainder keys describe a specific state transition and must retain their compiled template.
        if (input.getRemainingKey(encodedKey) != null) return encodedKey;
        GenericStack[] possible = input.getPossibleInputs();
        long amount = possible[0].amount();
        // Match Useless's DynamicPatternPlanning: declared craftables precede same-item component variants.
        for (GenericStack candidate : possible) {
            if (candidate != null && candidate.amount() == amount && candidate.what() != null
                    && input.isValid(candidate.what(), null)
                    && !craftingService.getCraftingFor(candidate.what()).isEmpty()) return candidate.what();
        }
        for (GenericStack candidate : possible) {
            if (candidate == null || candidate.amount() != amount
                    || !(candidate.what() instanceof AEItemKey item)) continue;
            AEKey preferred = craftingService.getFuzzyCraftable(candidate.what(), key ->
                key instanceof AEItemKey other && other.getItem() == item.getItem() && input.isValid(key, null));
            if (preferred != null) return preferred;
        }
        return encodedKey;
    }

    @Override
    public String name() {
        return "UselessMod";
    }

    @Nullable
    private static Object safeDefinition(IPatternDetails pattern) {
        try {
            return pattern.getDefinition();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

}
