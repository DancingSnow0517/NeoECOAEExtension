package cn.dancingsnow.neoecoae.client.gui.graph;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import java.util.List;

/** Camera changes trigger queries; stationary frames reuse the exact same result. */
final class GraphVisibilityCache {
    static final int LIVE_LIMIT = 256;
    private GraphLayoutSnapshot layout;
    private GraphLayoutSnapshot.Bounds area;
    private GraphRenderer.VisiblePlan visible;

    GraphRenderer.VisiblePlan get(GraphLayoutSnapshot next, GraphLayoutSnapshot.Bounds nextArea) {
        if (layout != next || !nextArea.equals(area)) {
            layout = next;
            area = nextArea;
            var nodes = next.query(nextArea, LIVE_LIMIT + 1);
            visible = new GraphRenderer.VisiblePlan(nodes,
                nodes.size() > LIVE_LIMIT ? List.of() : next.queryLinks(nextArea),
                next.nodeCount(nextArea), next.edgeCount(nextArea));
        }
        return visible;
    }

    void clear() {
        layout = null;
        area = null;
        visible = null;
    }
}
