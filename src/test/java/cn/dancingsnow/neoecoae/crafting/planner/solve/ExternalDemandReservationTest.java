package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.GenericStack;
import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalDemandReservationTest {
    private final AEKey material = mock(AEKey.class);
    private final CycleComponent cycle = new CycleComponent(0, List.of(material), List.of(), List.of(), List.of(), List.of());
    private final CompiledNetwork network = new CompiledNetwork(material, Map.of(material, List.of()), Set.of(), 0, 0);

    @Test
    void reservationShortageSubtractsTheRemainingStock() throws Exception {
        var inventory = new KeyCounter();
        inventory.add(material, 3L);
        var result = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(network, cycle,
            Map.of(), inventory, new SolveState(inventory), Map.of(material, 5L), Set.of(), ECOCancellation.NONE);
        assertEquals(CycleExternalDemandStatus.MISSING, result.status());
        assertEquals(Map.of(material, 2L), result.missingLeaves());
        assertEquals(3L, inventory.get(material));
    }

    @Test
    void unboundedSnapshotSupplyDoesNotRequireAFiniteInventoryEntry() throws Exception {
        when(material.getAmountPerByte()).thenReturn(8);
        var inventory = new KeyCounter();
        var base = new SolveState(PlannerInventorySnapshot.of(inventory, Set.of(material)));
        var result = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(network, cycle,
            Map.of(material, 100L), inventory, base, Map.of(), Set.of(), ECOCancellation.NONE);
        assertEquals(CycleExternalDemandStatus.SOLVED, result.status());
        assertEquals(100L, result.directReservations().get(material));
        assertTrue(result.states().isEmpty());
    }

    @Test void boundaryRootsShareAnUnadvertisedByproductWithoutRepeatingThePhysicalPattern() throws Exception {
        AEKey raw = key(), a = key(), b = key();
        var joint = pattern(a, List.of(new GenericStack(raw, 1)), new GenericStack(a, 1), new GenericStack(b, 1));
        var graph = new CompiledNetwork(a, Map.of(a, List.of(joint), b, List.of(), raw, List.of()), Set.of(), 1, 1);
        var stock = new KeyCounter(); stock.add(raw, 1);
        Map<AEKey, Long> roots = new LinkedHashMap<>(); roots.put(b, 1L); roots.put(a, 1L);
        var result = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(graph, cycle,
            roots, stock, new SolveState(stock), Map.of(), Set.of(), ECOCancellation.NONE);
        assertTrue(result.solved(), result.diagnostic());
        assertEquals(1, result.states().size());
        assertEquals(Map.of(joint.details(), 1L), result.states().getFirst().patternTimes());
        assertEquals(1L, result.states().getFirst().usedItems().get(raw));
        result.states().getFirst().executionProvenance().requireComplete();
        assertEquals(1L, stock.get(raw));
    }

    @Test void boundaryRootsCannotBothSpendTheSameFiniteRawMaterial() throws Exception {
        AEKey raw = key(), a = key(), b = key();
        var pa = pattern(a, List.of(new GenericStack(raw, 1)), new GenericStack(a, 1));
        var pb = pattern(b, List.of(new GenericStack(raw, 1)), new GenericStack(b, 1));
        var graph = new CompiledNetwork(a, Map.of(a, List.of(pa), b, List.of(pb), raw, List.of()), Set.of(), 2, 2);
        var stock = new KeyCounter(); stock.add(raw, 1);
        var base = new SolveState(stock);
        var result = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(graph, cycle,
            Map.of(a, 1L, b, 1L), stock, base, Map.of(), Set.of(), ECOCancellation.NONE);
        assertEquals(CycleExternalDemandStatus.MISSING, result.status());
        assertEquals(1L, result.missingLeaves().get(raw));
        assertTrue(base.patternTimes().isEmpty());
        assertTrue(base.usedItems().isEmpty());
    }

    private static AEKey key() {
        AEKey key = mock(AEKey.class); when(key.getAmountPerByte()).thenReturn(8); return key;
    }

    private static CompiledPattern pattern(AEKey primary, List<GenericStack> inputs, GenericStack... outputs) {
        var details = mock(IPatternDetails.class);
        var outputList = List.of(outputs); when(details.getOutputs()).thenReturn(outputList);
        var semantics = new PatternSemantics(details, null, List.of(), outputList, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(0, details, primary, PlannerAmount.of(outputs[0].amount()),
            inputs.stream().map(input -> new CompiledInput(null, input.what(), input.amount(), true, null)).toList(),
            outputList, true, null, false, semantics);
    }
}
