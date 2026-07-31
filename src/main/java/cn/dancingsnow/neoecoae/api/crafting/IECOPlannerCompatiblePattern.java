package cn.dancingsnow.neoecoae.api.crafting;

/**
 * Explicit opt-in contract for third-party pattern details that ECO can snapshot safely.
 *
 * <p>The pattern must expose stable inputs, outputs, multipliers and remaining items for the
 * lifetime of a crafting calculation. Input validity must not depend on the provider that will
 * execute the pattern. Provider-scoped or mutable semantics must use AE2's native planner.</p>
 */
public interface IECOPlannerCompatiblePattern {
    /**
     * Describes how ECO may turn an input slot into immutable planning operations.
     */
    default InputSemantics getECOPlannerInputSemantics() {
        return InputSemantics.CANONICAL_ONLY;
    }

    enum InputSemantics {
        /**
         * ECO uses only the first entry returned by {@code getPossibleInputs()}.
         * This is the conservative choice for AE2-compatible substitute inputs.
         */
        CANONICAL_ONLY,

        /**
         * Every listed or policy-added alternative may be planned and recursively crafted as a
         * uniform input for a complete pattern execution. Implementors must not require mixing
         * alternatives inside one slot multiplier.
         */
        UNIFORM_ALTERNATIVES
    }
}
