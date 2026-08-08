package cn.dancingsnow.neoecoae.api.crafting;

/**
 * Explicit opt-in contract for third-party pattern details that ECO can snapshot safely.
 *
 * <p>The pattern definition, inputs, outputs, multipliers, input validity and remaining items
 * must stay semantically stable from snapshot capture through CPU execution and job reload.
 * Outputs must be deterministic and completely described by {@code getOutputs()}; input validity
 * must not depend on the provider that executes the pattern. Hidden cross-slot constraints,
 * provider-scoped behavior and mutable runtime semantics must use AE2's native planner. A
 * different non-null remaining key is supported only as a finite, deterministic state transition
 * and must be valid for the same input slot.</p>
 *
 * <p>Opting in does not guarantee ECO planning. Finite expansion, graph and solver limits remain
 * compatibility boundaries and cause an explicit AE2 fallback when exceeded.</p>
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
         * ECO uses only the first entry returned by {@code getPossibleInputs()}. The selected key,
         * template amount, multiplier and remaining item must fully describe the slot.
         */
        CANONICAL_ONLY,

        /**
         * Every listed or policy-added alternative may be planned as a uniform input for one
         * complete pattern execution. One alternative must be valid for every unit represented by
         * the slot multiplier; a valid execution must never require mixing alternatives.
         */
        UNIFORM_ALTERNATIVES,

        /**
         * Every unit represented by an input multiplier may independently use any advertised or
         * policy-added alternative. All finite compositions must be semantically interchangeable
         * except for their declared input and remaining-item keys. ECO enumerates those
         * compositions and replays the selected one exactly during CPU input extraction.
         */
        MIXABLE_ALTERNATIVES
    }
}
