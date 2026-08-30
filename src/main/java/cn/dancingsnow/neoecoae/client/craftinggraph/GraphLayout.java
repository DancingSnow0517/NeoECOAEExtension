package cn.dancingsnow.neoecoae.client.craftinggraph;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Selects the main-graph backend and keeps cycle focus independent from the tree projection. */
public final class GraphLayout implements GraphLayoutEngine {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE_PROPERTY = "neoecoae.graph.layout";

    public enum Mode {
        COMPACT_TREE,
        LEGACY;

        static Mode fromSystemProperty() {
            String value;
            try {
                value = System.getProperty(MODE_PROPERTY, "COMPACT_TREE");
            } catch (SecurityException ignored) {
                value = "COMPACT_TREE";
            }
            try {
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Unknown {} value '{}'; using COMPACT_TREE", MODE_PROPERTY, value);
                return COMPACT_TREE;
            }
        }
    }

    private final Mode mode;
    private final GraphLayoutEngine legacy;
    private final GraphLayoutEngine circular;
    private final GraphLayoutEngine compactTree;
    private boolean reportedCompactTreeFailure;

    public GraphLayout() {
        this(Mode.fromSystemProperty());
    }

    public GraphLayout(Mode mode) {
        this(mode, new LayeredGraphLayout(), new CircularCycleLayout(), new CompactTreeLayout());
    }

    // Package-visible for backend selection tests without touching the screen.
    GraphLayout(Mode mode, GraphLayoutEngine legacy, GraphLayoutEngine circular,
            GraphLayoutEngine compactTree) {
        this.mode = mode;
        this.legacy = legacy;
        this.circular = circular;
        this.compactTree = compactTree;
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
            return circular.layout(graph, version);
        }
        if (mode == Mode.COMPACT_TREE) {
            try {
                return compactTree.layout(graph, version);
            } catch (RuntimeException | LinkageError | StackOverflowError exception) {
                reportCompactTreeFailure(graph, exception);
                return legacy.layout(graph, version);
            }
        }
        return legacy.layout(graph, version);
    }

    private void reportCompactTreeFailure(ClientCraftingGraph graph, Throwable exception) {
        if (!reportedCompactTreeFailure) {
            reportedCompactTreeFailure = true;
            LOGGER.warn("Compact tree layout failed for {} nodes and {} edges; falling back to legacy layout",
                graph.nodes().size(), graph.links().size(), exception);
        }
    }

}
