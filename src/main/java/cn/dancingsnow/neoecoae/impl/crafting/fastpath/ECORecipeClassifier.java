package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.Locale;
import net.minecraft.world.item.ItemStack;

/**
 * Deterministic, recipe-only classification used when a Smart Pattern Bus publishes a pattern.
 *
 * <p>This is intentionally not a proof that the recipe is executable. The proof is still produced by the
 * slow assembler verification and stored in {@link ECOFastPathResult}. The classifier only answers which
 * runtime contract must be used when that proof is available.</p>
 */
public final class ECORecipeClassifier {
    private ECORecipeClassifier() {
    }

    public enum Type {
        DURABILITY_MUTATION,
        REUSABLE_COMPONENT,
        NORMAL
    }

    public record Classification(Type type, boolean supported, String reason) {
        public Classification {
            type = type == null ? Type.NORMAL : type;
            reason = reason == null ? "" : reason;
        }
    }

    public static Classification classify(IPatternDetails pattern) {
        if (pattern == null) return unsupported("PATTERN_NULL");

        try {
            var inputs = pattern.getInputs();
            var outputs = pattern.getOutputs();
            if (inputs == null || inputs.length == 0) return unsupported("NO_INPUTS");
            if (outputs == null || outputs.isEmpty()) return unsupported("NO_OUTPUTS");
            for (GenericStack output : outputs) {
                if (output == null || output.what() == null || output.amount() <= 0L
                        || !(output.what() instanceof AEItemKey)) {
                    return unsupported("NON_ITEM_OUTPUT");
                }
            }

            boolean durabilityMutation = false;
            boolean reusableComponent = false;
            boolean hasRemainder = false;
            for (IPatternDetails.IInput input : inputs) {
                if (input == null || input.getMultiplier() <= 0L) return unsupported("INVALID_INPUT");
                GenericStack[] possible = input.getPossibleInputs();
                if (possible == null || possible.length == 0) return unsupported("INVALID_INPUT");

                // Mirror AE2PatternSemanticAdapter: prefer a concrete one-to-one reusable candidate over a
                // damageable substitution such as an ordinary infusion crystal.
                GenericStack selected = selectReusableCandidate(input, possible);
                if (selected == null || selected.what() == null || selected.amount() <= 0L) {
                    return unsupported("NON_ITEM_INPUT");
                }
                // AE2 may expose a bucket recipe as its contained fluid when fluid substitution is enabled.
                // The fluid is the actual consumed key. AE2 deliberately suppresses the empty container
                // remainder in this mode because no container was consumed.
                if (selected.what() instanceof AEFluidKey) {
                    continue;
                }
                if (!(selected.what() instanceof AEItemKey selectedKey)) {
                    return unsupported("NON_ITEM_INPUT");
                }
                ItemStack inputStack = selectedKey.toStack(1);
                if (inputStack.isEmpty()) return unsupported("INVALID_ITEM_INPUT");
                AEItemKey remainingKey = asItemKey(input.getRemainingKey(selected.what()));
                if (remainingKey == null) {
                    if (input.getRemainingKey(selected.what()) != null) hasRemainder = true;
                    continue;
                }
                hasRemainder = true;
                ItemStack remainingStack = remainingKey.toStack(1);
                if (remainingStack.isEmpty()) return unsupported("INVALID_REMAINDER");
                if (ItemStack.isSameItem(inputStack, remainingStack)) {
                    reusableComponent = true;
                    if (!selected.what().equals(remainingKey)
                            && inputStack.isDamageableItem() && remainingStack.isDamageableItem()) {
                        durabilityMutation = true;
                    }
                }
            }

            if (durabilityMutation) {
                return new Classification(Type.DURABILITY_MUTATION, true,
                    "RUNTIME_SIMULATION_REQUIRED");
            }
            if (reusableComponent) {
                return new Classification(Type.REUSABLE_COMPONENT, true,
                    "ONE_TO_ONE_REUSABLE_ITEM_OR_COMPONENT");
            }
            if (hasRemainder) {
                return new Classification(Type.NORMAL, false, "REMAINDER_IS_NOT_REUSABLE_ITEM");
            }
            return new Classification(Type.NORMAL, true, "STATIC_ITEM_CONTRACT");
        } catch (RuntimeException failure) {
            return unsupported("CLASSIFIER_FAILED:" + failure.getClass().getSimpleName().toUpperCase(Locale.ROOT));
        }
    }

    private static GenericStack selectReusableCandidate(IPatternDetails.IInput input, GenericStack[] possible) {
        GenericStack primary = possible[0];
        if (primary != null && primary.what() instanceof AEFluidKey) {
            return primary;
        }
        GenericStack mutatingCandidate = null;
        for (GenericStack candidate : possible) {
            if (candidate == null || !(candidate.what() instanceof AEItemKey)) continue;
            try {
                AEItemKey remaining = asItemKey(input.getRemainingKey(candidate.what()));
                if (remaining == null || candidate.amount() != 1L) continue;
                if (remaining.equals(candidate.what())) return candidate;
                ItemStack source = ((AEItemKey) candidate.what()).toStack(1);
                ItemStack returned = remaining.toStack(1);
                if (!source.isEmpty() && !returned.isEmpty() && ItemStack.isSameItem(source, returned)) {
                    if (mutatingCandidate == null) mutatingCandidate = candidate;
                }
            } catch (RuntimeException ignored) {
                // The primary candidate is validated below and will produce a stable rejection reason.
            }
        }
        return mutatingCandidate == null ? primary : mutatingCandidate;
    }

    private static AEItemKey asItemKey(Object key) {
        return key instanceof AEItemKey itemKey ? itemKey : null;
    }

    private static Classification unsupported(String reason) {
        return new Classification(Type.NORMAL, false, reason);
    }
}
