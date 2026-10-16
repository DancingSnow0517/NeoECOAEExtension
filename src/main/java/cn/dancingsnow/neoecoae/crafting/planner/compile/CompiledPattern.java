package cn.dancingsnow.neoecoae.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
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
    PatternSemantics semantics,
    SpecialPatternAnalysis specialAnalysis
) implements cn.dancingsnow.neoecoae.core.planner.PatternView<AEKey> {
    public CompiledPattern {


        if (semantics == null) semantics = new AE2PatternSemanticAdapter().analyze(details);
        if (specialAnalysis == null) specialAnalysis = SpecialPatternAnalysis.NONE;
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
            false, null, SpecialPatternAnalysis.NONE);
    }

    public CompiledPattern(int id, IPatternDetails details, AEKey producedKey, long outputPerPattern,
            List<CompiledInput> inputs, List<GenericStack> outputs, boolean fastSupported,
            String unsupportedReason, boolean netGrowthValidated) {
        this(id, details, producedKey, PlannerAmount.of(outputPerPattern), inputs, outputs, fastSupported,
            unsupportedReason, netGrowthValidated, null, SpecialPatternAnalysis.NONE);
    }

    public CompiledPattern(int id, IPatternDetails details, AEKey producedKey, PlannerAmount outputPerPattern,
            List<CompiledInput> inputs, List<GenericStack> outputs, boolean fastSupported,
            String unsupportedReason, boolean netGrowthValidated, PatternSemantics semantics) {
        this(id, details, producedKey, outputPerPattern, inputs, outputs, fastSupported, unsupportedReason,
            netGrowthValidated, semantics, SpecialPatternAnalysis.NONE);
    }

    /** Gross per-firing outputs used by cycle algebra: normal products plus normalized returned/reusable stock. */
    public List<GenericStack> grossOutputs() {
        if (semantics.returnedOutputs().isEmpty()) return outputs;
        List<GenericStack> result = new ArrayList<>(outputs);
        result.addAll(semantics.returnedOutputs());
        return result;
    }
    @Override public Object identity() { return details; }
    @Override public int inputCount() { return inputs.size(); }
    @Override public AEKey inputKey(int index) { return inputs.get(index).key(); }
    @Override public PlannerAmount inputAmount(int index) { return inputs.get(index).amountPerPattern(); }
    @Override public boolean inputFastSupported(int index) { return inputs.get(index).fastSupported(); }
    @Override public boolean inputIgnoresComponents(int index) { return inputs.get(index).ignoresComponents(); }
    @Override public int outputCount() { return outputs.size(); }
    @Override public AEKey outputKey(int index) { return outputs.get(index).what(); }
    @Override public long outputAmount(int index) { return outputs.get(index).amount(); }
    @Override public int grossOutputCount() { return outputs.size() + semantics.returnedOutputs().size(); }
    @Override public AEKey grossOutputKey(int index) {
        return index < outputs.size() ? outputs.get(index).what()
            : semantics.returnedOutputs().get(index - outputs.size()).what();
    }
    @Override public long grossOutputAmount(int index) {
        return index < outputs.size() ? outputs.get(index).amount()
            : semantics.returnedOutputs().get(index - outputs.size()).amount();
    }
    @Override public boolean completeForStaticPlanning() { return semantics.completeForStaticPlanning(); }
    @Override public boolean special() { return specialAnalysis.special(); }

}
