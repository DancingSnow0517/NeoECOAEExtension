package cn.dancingsnow.neoecoae.core.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphAdjacencyTest {
    private record Edge(int from, int to) {}

    private static GraphAdjacency sample() {
        return GraphAdjacency.of(List.of(
            new Edge(0, 2), new Edge(0, 1), new Edge(3, 1), new Edge(0, 2)), Edge::from, Edge::to);
    }

    @Test
    void neighboursKeepFirstOccurrenceOrderAndCollapseDuplicatePairs() {
        var adjacency = sample();
        assertEquals(List.of(2, 1), List.copyOf(adjacency.downstream(0)));
        assertEquals(List.of(0, 3), List.copyOf(adjacency.upstream(1)));
        assertEquals(List.of(0), List.copyOf(adjacency.upstream(2)));
    }

    @Test
    void unknownNodesHaveNoNeighboursAndSetsAreImmutable() {
        var adjacency = sample();
        assertTrue(adjacency.downstream(2).isEmpty());
        assertTrue(adjacency.upstream(0).isEmpty());
        assertTrue(adjacency.downstream(99).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> adjacency.downstream(0).add(7));
        assertThrows(UnsupportedOperationException.class, () -> adjacency.upstream(1).remove(0));
    }

    @Test
    void neighborhoodListsUpstreamBeforeDownstreamWithoutRepeats() {
        var adjacency = GraphAdjacency.of(List.of(new Edge(0, 1), new Edge(1, 0), new Edge(1, 2)),
            Edge::from, Edge::to);
        assertEquals(List.of(0, 2), List.copyOf(adjacency.neighborhood(1)));
        assertEquals(List.of(1), List.copyOf(adjacency.neighborhood(0)));
        assertEquals(List.of(2, 1), List.copyOf(sample().neighborhood(0)));
    }

    @Test
    void factoryMapsAnyEdgeTypeAndCanReverseDirection() {
        var reversed = GraphAdjacency.of(List.of(new Edge(5, 6)), Edge::to, Edge::from);
        assertEquals(List.of(5), List.copyOf(reversed.downstream(6)));
        assertEquals(List.of(6), List.copyOf(reversed.upstream(5)));
    }

    @Test
    void emptyEdgeListYieldsEmptyAdjacency() {
        var adjacency = GraphAdjacency.of(List.of(), Edge::from, Edge::to);
        assertTrue(adjacency.downstream(0).isEmpty());
        assertTrue(adjacency.upstream(0).isEmpty());
        assertTrue(adjacency.neighborhood(0).isEmpty());
    }
}
