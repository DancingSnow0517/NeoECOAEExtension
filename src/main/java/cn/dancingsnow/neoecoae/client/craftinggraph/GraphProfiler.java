package cn.dancingsnow.neoecoae.client.craftinggraph;

/** Mutable frame counters owned by the screen; not part of graph or layout state. */
public final class GraphProfiler {
    private int visibleNodes;
    private int visibleEdges;
    private long renderNanos;

    public void update(int visibleNodes, int visibleEdges, long renderNanos) {
        this.visibleNodes = visibleNodes;
        this.visibleEdges = visibleEdges;
        this.renderNanos = Math.max(0, renderNanos);
    }

    public int visibleNodes() { return visibleNodes; }
    public int visibleEdges() { return visibleEdges; }
    public long renderNanos() { return renderNanos; }
}
