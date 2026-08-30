package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.List;

/**
 * One compiled view of one {@link IPatternDetails}, together with the validation evidence recorded for it.
 *
 * @param fastSupported        smart-pattern-bus / fast-path verdict recorded at compile time
 * @param unsupportedReason    the recorded reason code behind {@code fastSupported == false}
 * @param netGrowthValidated   capability evidence issued by the smart pattern bus when it publishes this
 *                             decoded pattern. The compiler carries the verdict and never guesses it by
 *                             re-reading {@link IPatternDetails}.
 */
public record CompiledPattern(
    int id,
    IPatternDetails details,
    AEKey producedKey,
    long outputPerPattern,
    List<CompiledInput> inputs,
    List<GenericStack> outputs,
    boolean fastSupported,
    String unsupportedReason,
    boolean netGrowthValidated
) {
    public CompiledPattern {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    /**
     * Legacy shape for callers that carry no smart-bus capability evidence. The contract is then treated as
     * <em>unproven</em>, which is the safe direction: capabilities that need a stable static contract are
     * withheld rather than assumed.
     */
    public CompiledPattern(int id, IPatternDetails details, AEKey producedKey, long outputPerPattern,
            List<CompiledInput> inputs, List<GenericStack> outputs, boolean fastSupported,
            String unsupportedReason) {
        this(id, details, producedKey, outputPerPattern, inputs, outputs, fastSupported, unsupportedReason,
            false);
    }
}
