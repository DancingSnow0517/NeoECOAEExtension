package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class PlayerCycleReviewProbeTest {
    private final AEKey a = mock(AEKey.class, "A");
    private final AEKey b = mock(AEKey.class, "B");
    private final AEKey fuel = mock(AEKey.class, "fuel");
    private final AEKey product = mock(AEKey.class, "product");

    @Test void neutralTwoPatternRingUsesExactPlanForLargePlayerRequest() throws Exception {
        var produceIntermediate = pattern(0, Map.of(a, 1L, fuel, 1L), Map.of(b, 1L));
        var finish = pattern(1, Map.of(b, 1L), Map.of(a, 1L, product, 1L));
        var result = solve(List.of(a, b), List.of(produceIntermediate, finish), Map.of(product, 50_000L), Map.of(a, 1L));
        org.junit.jupiter.api.Assertions.assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.diagnostics().toString());
        org.junit.jupiter.api.Assertions.assertEquals(50_000L, result.patternTimes().get(produceIntermediate.details()));
        org.junit.jupiter.api.Assertions.assertEquals(50_000L, result.patternTimes().get(finish.details()));
        org.junit.jupiter.api.Assertions.assertEquals(50_000L, result.externalDemand().get(fuel));
    }

    private CycleSolveResult solve(List<AEKey> members, List<CompiledPattern> patterns,
            Map<AEKey,Long> targets, Map<AEKey,Long> stock) throws Exception {
        var boundary = List.of(new ComponentDependency(0,1,List.of(new CraftingGraphEdge(a,fuel,patterns.getFirst(),null))));
        var cycle = new CycleComponent(0,members,patterns,List.of(),List.of(),boundary);
        return new BoundedCycleSolver().solve(new CycleSolveRequest(cycle,targets,stock,boundary,
            new CycleSolveRequest.PlannerOptions()), ECOCancellation.NONE);
    }

    private static CompiledPattern pattern(int id, Map<AEKey,Long> inputs, Map<AEKey,Long> outputs) {
        var details = mock(IPatternDetails.class,"pattern"+id);
        var stacks = outputs.entrySet().stream().map(e -> new GenericStack(e.getKey(),e.getValue())).toList();
        when(details.getOutputs()).thenReturn(stacks);
        var semantics = new PatternSemantics(details,null,List.of(),stacks,List.of(),List.of(),
            PatternSemantics.MatchingMode.EXACT,PatternSemantics.ExecutionRestriction.NONE,true,true,null);
        return new CompiledPattern(id,details,stacks.getFirst().what(),PlannerAmount.of(stacks.getFirst().amount()),
            inputs.entrySet().stream().map(e -> new CompiledInput(null,e.getKey(),e.getValue(),true,null)).toList(),
            stacks,true,null,false,semantics);
    }
}
