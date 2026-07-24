package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOStrongComponents;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ECOPlanningGraphTest {
    @Test
    void keepsOnlyOperationsThatCanReachTheRequest() {
        var smelt = operation("smelt", Map.of("ore", 1L), Map.of("ingot", 1L));
        var assemble = operation("assemble", Map.of("ingot", 2L), Map.of("machine", 1L));
        var unrelated = operation("unrelated", Map.of("sand", 1L), Map.of("glass", 1L));

        var graph = new ECOPlanningGraph<>(List.of(smelt, assemble, unrelated));
        var pruned = ECOGraphPruner.targetReachable(graph, Set.of("machine"));

        assertEquals(Set.of(smelt, assemble), Set.copyOf(pruned.operations()));
        assertFalse(pruned.materials().contains("glass"));
    }

    @Test
    void detectsStronglyConnectedCraftingMaterials() {
        var graph = new ECOPlanningGraph<>(List.of(
                operation("a-to-b", Map.of("a", 1L), Map.of("b", 1L)),
                operation("b-to-a", Map.of("b", 1L), Map.of("a", 1L)),
                operation("b-to-c", Map.of("b", 1L), Map.of("c", 1L))));

        List<Set<String>> components = ECOStrongComponents.find(graph);

        assertTrue(components.contains(Set.of("a", "b")));
        assertTrue(components.contains(Set.of("c")));
    }

    private static ECOPlanningOperation<String, String> operation(
            String name, Map<String, Long> inputs, Map<String, Long> outputs) {
        return new ECOPlanningOperation<>(name, inputs, outputs);
    }
}
