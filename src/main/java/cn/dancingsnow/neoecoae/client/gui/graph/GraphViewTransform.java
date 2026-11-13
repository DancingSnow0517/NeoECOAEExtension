package cn.dancingsnow.neoecoae.client.gui.graph;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;

/** Graph camera with frame-rate independent, cursor-anchored wheel zoom. */
final class GraphViewTransform {
    static final float MIN_ZOOM = 0.05f;
    static final float MAX_ZOOM = 2.5f;
    private static final double ZOOM_RESPONSE = 18;

    private float x;
    private float y = 30;
    private float zoom = 1;
    private float targetZoom = 1;
    private float anchorX, anchorY;
    private float anchorWorldX, anchorWorldY;

    float x() { return x; }
    float y() { return y; }
    float zoom() { return zoom; }

    void set(float nextX, float nextY, float nextZoom) {
        x = nextX;
        y = nextY;
        zoom = targetZoom = nextZoom;
    }

    void stop() { targetZoom = zoom; }

    void pan(float dx, float dy) {
        x += dx;
        y += dy;
        anchorX += dx;
        anchorY += dy;
    }

    void zoomAt(double screenX, double screenY, double scroll) {
        float next = scrollZoom(targetZoom, scroll);
        if (next == targetZoom) return;
        anchorX = (float) screenX;
        anchorY = (float) screenY;
        anchorWorldX = (anchorX - x) / zoom;
        anchorWorldY = (anchorY - y) / zoom;
        targetZoom = next;
    }

    void advance(double seconds) {
        if (zoom == targetZoom || seconds <= 0 || !Double.isFinite(seconds)) return;
        // GTO's PlanarView eases in log space: equal ratios feel alike at every scale.
        double from = Math.log(zoom), to = Math.log(targetZoom);
        double blend = -Math.expm1(-ZOOM_RESPONSE * Math.min(0.1, seconds));
        zoom = Math.abs(to - from) < 0.002 ? targetZoom : (float) Math.exp(from + (to - from) * blend);
        x = anchorX - anchorWorldX * zoom;
        y = anchorY - anchorWorldY * zoom;
    }

    static float fitZoom(GraphLayoutSnapshot.Bounds bounds, float width, float height) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM,
            Math.min(Math.max(1, width) / Math.max(1, bounds.width()), Math.max(1, height) / Math.max(1, bounds.height()))));
    }

    static float scrollZoom(float current, double scroll) {
        // A fitted view below the ordinary wheel floor must not jump inward on the next wheel event.
        float minimum = Math.min(MIN_ZOOM, current);
        return Math.max(minimum, Math.min(MAX_ZOOM, (float) (current * Math.pow(1.15, scroll))));
    }
}
