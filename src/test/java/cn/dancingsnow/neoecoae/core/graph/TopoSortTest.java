package cn.dancingsnow.neoecoae.core.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TopoSortTest {
    private static final Map<String, List<String>> DAG = Map.of(
        "a", List.of("c", "b"),
        "b", List.of("d"),
        "c", List.of("d"),
        "e", List.of()
    );

    @Test
    void fifoOrderIsExactForSmallDag() {
        var result = TopoSort.kahn(List.of("a", "b", "c", "d", "e"), successorsOf(DAG), null);

        assertEquals(List.of("a", "e", "c", "b", "d"), result.order());
        assertEquals(List.of(), result.remaining());
        assertTrue(result.acyclic());
    }

    @Test
    void comparatorOrdersReadyNodes() {
        var result = TopoSort.kahn(List.of("a", "b", "c", "d", "e"), successorsOf(DAG), Comparator.naturalOrder());

        assertEquals(List.of("a", "b", "c", "d", "e"), result.order());
        assertTrue(result.acyclic());
    }

    @Test
    void cycleAndDownstreamNodesRemainInNodesOrder() {
        Map<String, List<String>> graph = Map.of(
            "x", List.of("a"),
            "a", List.of("b"),
            "b", List.of("c"),
            "c", List.of("b", "d")
        );

        var result = TopoSort.kahn(List.of("d", "c", "b", "a", "x"), successorsOf(graph), null);

        assertEquals(List.of("x", "a"), result.order());
        assertEquals(List.of("d", "c", "b"), result.remaining());
        assertFalse(result.acyclic());
    }

    @Test
    void edgesToNodesOutsideNodesAreIgnored() {
        Map<String, List<String>> graph = Map.of(
            "a", List.of("b", "z"),
            "b", List.of("q")
        );

        var result = TopoSort.kahn(List.of("a", "b"), successorsOf(graph), null);

        assertEquals(List.of("a", "b"), result.order());
        assertEquals(List.of(), result.remaining());
        assertTrue(result.acyclic());
    }

    @Test
    void duplicateEdgesCountTowardIndegree() {
        Map<String, List<String>> graph = Map.of("a", List.of("b", "b"));

        var result = TopoSort.kahn(List.of("a", "b"), successorsOf(graph), null);

        assertEquals(List.of("a", "b"), result.order());
        assertEquals(List.of(), result.remaining());
    }

    @Test
    void duplicateNodesCollapseToFirstOccurrence() {
        var result = TopoSort.kahn(List.of("c", "a", "c", "b"), successorsOf(Map.of()), null);

        assertEquals(List.of("c", "a", "b"), result.order());
        assertEquals(List.of(), result.remaining());
    }

    @Test
    void duplicateNodesInCycleAreReportedOnce() {
        Map<String, List<String>> graph = Map.of(
            "a", List.of("b"),
            "b", List.of("a")
        );

        var result = TopoSort.kahn(List.of("a", "b", "a"), successorsOf(graph), null);

        assertEquals(List.of(), result.order());
        assertEquals(List.of("a", "b"), result.remaining());
    }

    @Test
    void resultListsAreUnmodifiable() {
        var result = TopoSort.kahn(List.of("a"), successorsOf(Map.of()), null);

        assertThrows(UnsupportedOperationException.class, () -> result.order().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> result.remaining().add("x"));
    }

    @Test
    void checkpointVariantKeepsFifoOrderAndPollsEachEmittedNode() {
        List<String> polls = new ArrayList<>();

        var result = TopoSort.kahn(List.of("a", "b", "c", "d", "e"), successorsOf(DAG), null,
            () -> polls.add("."));

        assertEquals(List.of("a", "e", "c", "b", "d"), result.order());
        assertEquals(result.order().size(), polls.size());
    }

    @Test
    void checkpointFailureAbortsTheSort() {
        assertThrows(IOException.class, () -> TopoSort.kahn(List.of("a", "b"), successorsOf(DAG), null,
            () -> {
                throw new IOException("cancelled");
            }));
    }

    private static java.util.function.Function<String, List<String>> successorsOf(Map<String, List<String>> graph) {
        return node -> graph.getOrDefault(node, List.of());
    }
}
