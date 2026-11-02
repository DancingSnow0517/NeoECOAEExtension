package cn.dancingsnow.neoecoae.client.gui.graph;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;

/** Camera calculations shared by fitting and wheel zoom, including very wide dependency trees. */
final class GraphViewTransform {
    static final float MIN_ZOOM = 0.000001f;
    static final float MAX_ZOOM = 2.5f;

    private GraphViewTransform() {}

    static float fitZoom(GraphLayoutSnapshot.Bounds bounds, float width, float height) {
        return Math.max(Float.MIN_NORMAL, Math.min(1.5f,
            Math.min(Math.max(1, width) / bounds.width(), Math.max(1, height) / bounds.height())));
    }

    static float scrollZoom(float current, double scroll) {
        // A fitted view below the ordinary wheel floor must not jump inward on the next wheel event.
        float minimum = Math.min(MIN_ZOOM, current);
        return Math.max(minimum, Math.min(MAX_ZOOM, (float) (current * Math.pow(1.12, scroll))));
    }
}
