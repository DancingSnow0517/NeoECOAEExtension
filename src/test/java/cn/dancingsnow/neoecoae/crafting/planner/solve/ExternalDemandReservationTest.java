package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
}
