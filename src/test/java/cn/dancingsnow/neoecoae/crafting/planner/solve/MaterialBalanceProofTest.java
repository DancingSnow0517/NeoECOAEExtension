package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialBalanceProofTest {
    private final AEKey goal = key("G"), main = key("M"), side = key("S"), sideVariant = key("S'"), raw = key("R");

    /**
     * Q makes M and a component variant S' of S. Compiled as a producer of the component-insensitive key S, its
     * view credits S instead. The balance must not prove the order impossible from the M view alone.
     */
    @Test
    void differingViewsOfOnePatternWithholdTheProof() throws Exception {
        var q = mock(IPatternDetails.class);
        var top = view(mock(IPatternDetails.class), goal, List.of(new GenericStack(goal, 1)), side);
        var asMain = view(q, main, List.of(new GenericStack(main, 1), new GenericStack(sideVariant, 1)), raw);
        var asSide = view(q, side, List.of(new GenericStack(main, 1), new GenericStack(side, 1)), raw);
        var stock = new KeyCounter();
        stock.add(raw, 4L);
        var snapshot = PlannerInventorySnapshot.of(stock);

        // Without the aliased view nothing makes S: a sound proof.
        assertTrue(MaterialBalanceProof.infeasible(network(top, asMain, null), snapshot, 1L, Map.of(), false,
            ECOCancellation.NONE));
        // The aliased view makes S from R, so the order is feasible and no proof may be reported.
        assertFalse(MaterialBalanceProof.infeasible(network(top, asMain, asSide), snapshot, 1L, Map.of(), false,
            ECOCancellation.NONE));
        assertFalse(MaterialBalanceProof.infeasible(network(top, null, asSide), snapshot, 1L, Map.of(), false,
            ECOCancellation.NONE));
    }

    private CompiledNetwork network(CompiledPattern top, CompiledPattern asMain, CompiledPattern asSide) {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(goal, List.of(top));
        // The M view comes first, so a first-view-wins model would never see the S credit.
        if (asMain != null) producers.put(main, List.of(asMain));
        producers.put(side, asSide == null ? List.of() : List.of(asSide));
        producers.put(raw, List.of());
        return new CompiledNetwork(goal, producers, Set.of(), 3, 3);
    }

    private static CompiledPattern view(IPatternDetails details, AEKey produced, List<GenericStack> outputs,
            AEKey input) {
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        long perPattern = outputs.stream().filter(stack -> stack.what().equals(produced))
            .mapToLong(GenericStack::amount).sum();
        return new CompiledPattern(0, details, produced, PlannerAmount.of(perPattern),
            List.of(new CompiledInput(null, input, 1L, true, null)), outputs, true, null, false, semantics);
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }
}
