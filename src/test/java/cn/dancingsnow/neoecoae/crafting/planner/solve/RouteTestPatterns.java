package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exact processing patterns shared by the route-search regression scenarios. */
final class RouteTestPatterns {
    private RouteTestPatterns() {}

    static CompiledPattern pattern(int id, AEKey primary, Map<AEKey, Long> inputs,
            Map<AEKey, Long> outputs) {
        var details = mock(IPatternDetails.class, "pattern" + id);
        var stacks = new ArrayList<GenericStack>();
        stacks.add(new GenericStack(primary, outputs.get(primary)));
        outputs.forEach((key, amount) -> { if (!key.equals(primary)) stacks.add(new GenericStack(key, amount)); });
        var rawInputs = inputs.entrySet().stream().map(entry -> {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(entry.getKey(), entry.getValue())});
            when(input.getMultiplier()).thenReturn(1L);
            return input;
        }).toArray(IPatternDetails.IInput[]::new);
        when(details.getInputs()).thenReturn(rawInputs);
        when(details.getOutputs()).thenReturn(stacks);
        when(details.getPrimaryOutput()).thenReturn(stacks.getFirst());
        var compiled = inputs.entrySet().stream().map(entry ->
            new CompiledInput(null, entry.getKey(), entry.getValue(), true, null)).toList();
        var consumed = inputs.entrySet().stream().map(entry -> new PatternSemantics.Input(null,
            entry.getKey(), PlannerAmount.of(entry.getValue()), null, PlannerAmount.ZERO)).toList();
        var semantics = new PatternSemantics(details, null, consumed, stacks, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, primary, PlannerAmount.of(outputs.get(primary)),
            compiled, stacks, true, null, false, semantics);
    }
}
