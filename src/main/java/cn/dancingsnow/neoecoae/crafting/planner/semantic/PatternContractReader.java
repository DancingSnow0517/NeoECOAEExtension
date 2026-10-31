package cn.dancingsnow.neoecoae.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single defensive read of the public {@link IPatternDetails} input/output contract.
 *
 * <p>Patterns may come from third-party addons that return {@code null} entries or throw. Those patterns are
 * reported once per implementation class and exposed as a {@link View#wellFormed() malformed} view, so consumers do
 * not repeat null checks and exception handling at every level.</p>
 */
public final class PatternContractReader {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.pattern");
    private static final Set<Class<?>> REPORTED = ConcurrentHashMap.newKeySet();
    private static final View EMPTY = new View(List.of(), List.of(), false);

    private PatternContractReader() {
    }

    /** One AE2 input slot with a non-null, null-free candidate array. */
    public record Input(GenericStack[] possible, long multiplier) {
        public GenericStack primary() {
            return possible[0];
        }
    }

    /**
     * Readable outputs and inputs. Null outputs and inputs with null/empty candidate arrays or null candidates are
     * omitted; {@code wellFormed} is false when anything was omitted or the pattern threw.
     */
    public record View(List<GenericStack> outputs, List<Input> inputs, boolean wellFormed) {
    }

    public static View read(IPatternDetails pattern) {
        Objects.requireNonNull(pattern, "pattern");
        try {
            String problem = null;
            List<GenericStack> rawOutputs = pattern.getOutputs();
            List<GenericStack> outputs;
            if (rawOutputs == null) {
                outputs = List.of();
                problem = "null outputs";
            } else {
                outputs = new ArrayList<>(rawOutputs.size());
                for (GenericStack output : rawOutputs) {
                    if (output == null) problem = "null output";
                    else outputs.add(output);
                }
            }
            IPatternDetails.IInput[] rawInputs = pattern.getInputs();
            List<Input> inputs;
            if (rawInputs == null) {
                inputs = List.of();
                problem = "null inputs";
            } else {
                inputs = new ArrayList<>(rawInputs.length);
                for (IPatternDetails.IInput input : rawInputs) {
                    String inputProblem = input == null ? "null input" : null;
                    GenericStack[] possible = input == null ? null : input.getPossibleInputs();
                    if (inputProblem == null && (possible == null || possible.length == 0)) {
                        inputProblem = "empty possible inputs";
                    }
                    if (inputProblem == null) {
                        for (GenericStack stack : possible) {
                            if (stack == null) {
                                inputProblem = "null possible input";
                                break;
                            }
                        }
                    }
                    if (inputProblem == null) inputs.add(new Input(possible, input.getMultiplier()));
                    else problem = inputProblem;
                }
            }
            if (problem != null) report(pattern, problem, null);
            return new View(outputs, inputs, problem == null);
        } catch (RuntimeException failure) {
            report(pattern, "contract call failed", failure);
            return EMPTY;
        }
    }

    private static void report(IPatternDetails pattern, String problem, RuntimeException failure) {
        if (!REPORTED.add(pattern.getClass())) return;
        LOGGER.warn("Pattern implementation {} violates the IPatternDetails input/output contract ({}); "
            + "affected data is ignored. Further reports for this class are suppressed.",
            pattern.getClass().getName(), problem, failure);
    }
}
