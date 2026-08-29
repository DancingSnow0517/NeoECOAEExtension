package cn.dancingsnow.neoecoae.impl.crafting.planner;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.Level;

final class PlannerFixtures {
    private PlannerFixtures() {}
    static Pattern pattern(String name, AEKey output, long outputAmount, Object... inputPairs) {
        return multiOutput(name, List.of(new GenericStack(output, outputAmount)), inputPairs);
    }
    /** Multi-output pattern fixture: byproducts and shared producers both need more than one output stack. */
    static Pattern multiOutput(String name, List<GenericStack> outputs, Object... inputPairs) {
        List<IPatternDetails.IInput> inputs = new ArrayList<>();
        for (int i = 0; i < inputPairs.length; i += 2) {
            inputs.add(new Input((AEKey) inputPairs[i], ((Number) inputPairs[i + 1]).longValue(), false));
        }
        return new Pattern(name, inputs.toArray(IPatternDetails.IInput[]::new), List.copyOf(outputs));
    }
    static CompiledPattern compiled(int id, Pattern pattern, AEKey output, boolean fast, String reason) {
        List<CompiledInput> inputs = new ArrayList<>();
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs()[0];
            inputs.add(new CompiledInput(input, possible.what(), possible.amount() * input.getMultiplier(), fast, reason));
        }
        long count = pattern.getOutputs().stream().filter(s -> s.what().equals(output)).mapToLong(GenericStack::amount).sum();
        return new CompiledPattern(id, pattern, output, count, inputs, pattern.getOutputs(), fast, reason);
    }
    static CompiledNetwork network(AEKey goal, Map<AEKey, List<CompiledPattern>> patterns) {
        Map<AEKey, List<CompiledPattern>> copy = new LinkedHashMap<>(patterns);
        int pc = copy.values().stream().mapToInt(List::size).sum();
        int edges = copy.values().stream().flatMap(List::stream).mapToInt(p -> p.inputs().size()).sum();
        return new CompiledNetwork(goal, copy, Set.of(), pc, edges);
    }
    static final class Pattern implements IPatternDetails {
        private final String name;
        private final IInput[] inputs;
        private final List<GenericStack> outputs;
        Pattern(String name, IInput[] inputs, List<GenericStack> outputs) { this.name = name; this.inputs = inputs; this.outputs = outputs; }
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return outputs; }
        @Override public String toString() { return name; }
    }
    record Input(AEKey key, long amount, boolean remainder) implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs() { return new GenericStack[] { new GenericStack(key, amount) }; }
        @Override public long getMultiplier() { return 1; }
        @Override public boolean isValid(AEKey input, Level level) { return key.equals(input); }
        @Override public AEKey getRemainingKey(AEKey template) { return remainder ? key : null; }
    }
}
