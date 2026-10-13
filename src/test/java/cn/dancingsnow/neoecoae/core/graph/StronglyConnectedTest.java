package cn.dancingsnow.neoecoae.core.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class StronglyConnectedTest {
    /** a and b form a cycle, b also reaches the self-loop c, and d is isolated. */
    private static final Map<String, List<String>> GRAPH = Map.of(
        "a", List.of("b"),
        "b", List.of("a", "c"),
        "c", List.of("c"),
        "d", List.of()
    );

    @Test
    void componentsAreSinkFirstAndMembersAreInStackPopOrder() {
        var components = StronglyConnected.components(List.of("a", "b", "c", "d"), successorsOf(GRAPH), () -> {});

        assertEquals(List.of(List.of("c"), List.of("b", "a"), List.of("d")), components);
    }

    @Test
    void rootOrderDecidesComponentOrderAmongIndependentComponents() {
        var components = StronglyConnected.components(List.of("d", "a"), successorsOf(GRAPH), () -> {});

        assertEquals(List.of(List.of("d"), List.of("c"), List.of("b", "a")), components);
    }

    @Test
    void emptyRootsProduceNoComponents() {
        assertEquals(List.of(), StronglyConnected.components(List.of(), successorsOf(GRAPH), () -> {}));
    }

    @Test
    void checkpointFailureAbortsTheSearch() {
        assertThrows(IOException.class, () -> StronglyConnected.components(List.of("a"), successorsOf(GRAPH),
            () -> {
                throw new IOException("cancelled");
            }));
    }

    private static Function<String, List<String>> successorsOf(Map<String, List<String>> graph) {
        return node -> graph.getOrDefault(node, List.of());
    }
}
