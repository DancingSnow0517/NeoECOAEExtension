package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.ArrayList;
import java.util.List;

/**
 * One compiled view of one {@link IPatternDetails}, together with the validation evidence recorded for it.
 *
 * @param fastSupported        smart-pattern-bus / fast-path verdict recorded at compile time
 * @param unsupportedReason    compile-time contract evidence. It is the rejection reason when
 *                             {@code fastSupported == false}; a fast-supported DAG pattern may retain
 *                             {@code UNSUPPORTED_SUBSTITUTION} to keep it out of exact cycle algebra.
 * @param netGrowthValidated   capability evidence issued by the smart pattern bus when it publishes this
 *                             decoded pattern. The compiler carries the verdict and never guesses it by
 *                             re-reading {@link IPatternDetails}.
 */
public record CompiledPattern(
    int id,
    IPatternDetails details,
    AEKey producedKey,
    PlannerAmount outputPerPattern,
    List<CompiledInput> inputs,
    List<GenericStack> outputs,
    boolean fastSupported,
    String unsupportedReason,
    boolean netGrowthValidated,
    PatternSemantics semantics
) {
    public CompiledPattern {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (semantics == null) semantics = new AE2PatternSemanticAdapter().analyze(details);
    }

    /**
     * Legacy shape for callers that carry no smart-bus capability evidence. The contract is then treated as
     * <em>unproven</em>, which is the safe direction: capabilities that need a stable static contract are
     * withheld rather than assumed.
     */
    public CompiledPattern(int id, IPatternDetails details, AEKey producedKey, long outputPerPattern,
            List<CompiledInput> inputs, List<GenericStack> outputs, boolean fastSupported,
            String unsupportedReason) {
        this(id, details, producedKey, PlannerAmount.of(outputPerPattern), inputs, outputs, fastSupported, unsupportedReason,
            false, null);
    }

    public CompiledPattern(int id, IPatternDetails details, AEKey producedKey, long outputPerPattern,
            List<CompiledInput> inputs, List<GenericStack> outputs, boolean fastSupported,
            String unsupportedReason, boolean netGrowthValidated) {
        this(id, details, producedKey, PlannerAmount.of(outputPerPattern), inputs, outputs, fastSupported,
            unsupportedReason, netGrowthValidated, null);
    }

    /** Gross per-firing outputs used by cycle algebra: normal products plus normalized returned/reusable stock. */
    public List<GenericStack> grossOutputs() {
        if (semantics.returnedOutputs().isEmpty()) return outputs;
        List<GenericStack> result = new ArrayList<>(outputs);
        result.addAll(semantics.returnedOutputs());
        return List.copyOf(result);
    }
}
