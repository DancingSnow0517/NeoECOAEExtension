package cn.dancingsnow.neoecoae.client.gui.graph;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.CycleCluster;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.crafting.graph.InterCycleFlow;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import java.math.BigInteger;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Live rendering for sparse views; bounded progressive raster tiles for dense overviews. */
public final class GraphRenderer implements AutoCloseable {
    private static final int TEXT = 0xffe8edf2;
    private static final int MUTED = 0xffaab3bd;

    public record Frame(@Nullable ClientCraftingGraph.Node hovered, List<Component> tooltip) {}
    public record VisiblePlan(List<GraphLayoutSnapshot.Box> nodes, List<ClientCraftingGraph.Link> edges,
            int nodeCount, int edgeCount) {
        public VisiblePlan(List<GraphLayoutSnapshot.Box> nodes, List<ClientCraftingGraph.Link> edges) {
            this(nodes, edges, nodes.size(), edges.size());
        }
    }
    private final CompactTreeRenderer compactTreeRenderer = new CompactTreeRenderer();
    private final GraphVisibilityCache visibility = new GraphVisibilityCache();
    private final GraphViewportCache canvas = new GraphViewportCache();
    private boolean cachedView;
    private ClientCraftingGraph tooltipGraph;
    private ClientCraftingGraph.Node tooltipNode;
    private Language tooltipLanguage;
    private List<Component> tooltipLines = List.of();

    public static void resourcesReloaded() { GraphViewportCache.resourcesReloaded(); }

    static VisiblePlan collectVisible(GraphLayoutSnapshot layout, float cameraX, float cameraY, float zoom,
            int screenWidth, int screenHeight) {
        var area = viewport(cameraX, cameraY, zoom, screenWidth, screenHeight).padded(24 / zoom);
        return new VisiblePlan(layout.query(area, Integer.MAX_VALUE), layout.queryLinks(area));
    }

    static GraphLayoutSnapshot.Bounds viewport(float cameraX, float cameraY, float zoom,
            int screenWidth, int screenHeight) {
        return new GraphLayoutSnapshot.Bounds(-cameraX / zoom, (30 - cameraY) / zoom,
            (screenWidth - cameraX) / zoom, (screenHeight - cameraY) / zoom);
    }

    static boolean useCache(float zoom, int visibleNodes, boolean previous) {
        return zoom < 0.45f || visibleNodes > (previous ? 192 : GraphVisibilityCache.LIVE_LIMIT);
    }

    public Frame render(GuiGraphics graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            float cameraX, float cameraY, float zoom, int screenWidth, int screenHeight, int mouseX, int mouseY,
            @Nullable Integer selectedId, GraphProfiler profiler) {
        long started = System.nanoTime();
        var area = viewport(cameraX, cameraY, zoom, screenWidth, screenHeight);
        VisiblePlan visible = visibility.get(layout, area.padded(24 / zoom));
        cachedView = useCache(zoom, visible.nodeCount(), cachedView);
        Frame frame;
        if (!cachedView) {
            frame = renderImmediate(graphics, graph, layout, cameraX, cameraY, zoom, screenWidth, screenHeight,
                mouseX, mouseY, selectedId, profiler, visible);
        } else {
            canvas.prepare(graphics, layout, area, layout.bounds(), zoom,
                (tile, pixelsPerUnit) -> new TilePainter(graph, layout, tile, pixelsPerUnit));
            float worldMouseX = (mouseX - cameraX) / zoom;
            float worldMouseY = (mouseY - cameraY) / zoom;
            var hit = mouseY < 30 ? List.<GraphLayoutSnapshot.Box>of()
                : layout.query(new GraphLayoutSnapshot.Bounds(worldMouseX, worldMouseY, worldMouseX, worldMouseY), 1);
            var hovered = hit.isEmpty() ? null : graph.nodes().get(hit.get(0).nodeId());
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(cameraX, cameraY, 0);
                graphics.pose().scale(zoom, zoom, 1);
                canvas.draw(graphics);
                drawCachedOverlay(graphics, graph, layout, area, selectedId, hovered, zoom);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
            frame = new Frame(hovered, List.of());
        }
        // Hover text contains exact amount formatting; keep it off the stationary-frame hot path.
        if (tooltipGraph != graph || tooltipNode != frame.hovered() || tooltipLanguage != Language.getInstance()) {
            tooltipGraph = graph;
            tooltipNode = frame.hovered();
            tooltipLanguage = Language.getInstance();
            tooltipLines = graph.isCompactTree() ? CompactTreeRenderer.tooltip(graph, tooltipNode)
                : tooltip(graph, tooltipNode);
        }
        profiler.update(visible.nodeCount(), visible.edgeCount(), System.nanoTime() - started);
        return new Frame(frame.hovered(), tooltipLines);
    }

    @Override
    public void close() {
        canvas.close();
        visibility.clear();
        tooltipGraph = null;
        tooltipNode = null;
        tooltipLanguage = null;
        tooltipLines = List.of();
        cachedView = false;
    }

    private Frame renderImmediate(GuiGraphics graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            float cameraX, float cameraY, float zoom, int screenWidth, int screenHeight, int mouseX, int mouseY,
            @Nullable Integer selectedId, GraphProfiler profiler, VisiblePlan visible) {
        if (graph.isCompactTree()) {
            return compactTreeRenderer.render(graphics, graph, layout, cameraX, cameraY, zoom, screenWidth,
                screenHeight, mouseX, mouseY, selectedId, profiler, visible);
        }
        long started = System.nanoTime();
        List<GraphLayoutSnapshot.Box> boxes = visible.nodes();
        List<ClientCraftingGraph.Link> links = visible.edges();
        Set<Integer> neighborhood = selectedId == null ? Set.of() : graph.neighborhood(selectedId);

        if (graph.isRecursiveCycle()) {
            var area = viewport(cameraX, cameraY, zoom, screenWidth, screenHeight);
            for (var region : layout.recursionRegions()) {
                if (region.bounds().padded(180).intersects(area)) {
                    RecursiveCycleRenderer.drawRegion(graphics, region, cameraX, cameraY, zoom);
                }
            }
        }
        for (var link : links) {
            drawLink(graphics, graph, layout, link, selectedId, neighborhood, cameraX, cameraY, zoom);
        }
        // Amount labels go on top of every line so a later edge never paints over an earlier label.
        if (graph.view() != ClientCraftingGraph.View.MAIN && !graph.isRecursiveCycle() && zoom >= 0.55f) {
            for (var link : links) {
                drawEdgeAmount(graphics, graph, link, layout.edgePoints(link), cameraX, cameraY, zoom);
            }
        }
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && !graph.isRecursiveCycle()) {
            drawCycleOutputArrow(graphics, graph, layout, cameraX, cameraY, zoom);
        }
        ClientCraftingGraph.Node hovered = null;
        float worldMouseX = (mouseX - cameraX) / zoom;
        float worldMouseY = (mouseY - cameraY) / zoom;
        for (var box : boxes) {
            ClientCraftingGraph.Node node = graph.nodes().get(box.nodeId());
            if (node == null) continue;
            boolean selected = selectedId != null && selectedId == node.id();
            boolean adjacent = neighborhood.contains(node.id());
            drawNode(graphics, graph, node, box, selected, adjacent, cameraX, cameraY, zoom);
            if (box.contains(worldMouseX, worldMouseY)) hovered = node;
        }
        profiler.update(boxes.size(), links.size(), System.nanoTime() - started);
        return new Frame(hovered, List.of());
    }

    private static void drawCachedOverlay(GuiGraphics graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            GraphLayoutSnapshot.Bounds area, @Nullable Integer selectedId, @Nullable ClientCraftingGraph.Node hovered,
            float zoom) {
        if (selectedId != null && zoom >= 0.5f) {
            for (var link : layout.linksAt(selectedId)) {
                if (layout.linkBounds(link).intersects(area)) {
                    drawCachedLink(graphics, graph, layout, link, zoom, 0xfff6c453);
                    int neighbor = link.fromId() == selectedId ? link.toId() : link.fromId();
                    drawOutline(graphics, graph, layout.box(neighbor), area, 0xff83c5be, 1 / zoom);
                }
            }
        }
        if (selectedId != null) drawOutline(graphics, graph, layout.box(selectedId), area, 0xfff6c453, 2 / zoom);
        if (hovered != null && (selectedId == null || selectedId != hovered.id())) {
            drawOutline(graphics, graph, layout.box(hovered.id()), area, 0xff83c5be, 1 / zoom);
        }
    }

    private static void drawOutline(GuiGraphics graphics, ClientCraftingGraph graph, @Nullable GraphLayoutSnapshot.Box box,
            GraphLayoutSnapshot.Bounds area, int color, float thickness) {
        if (box == null || !box.intersects(area.left(), area.top(), area.right(), area.bottom())) return;
        var node = graph.nodes().get(box.nodeId());
        if (node != null && node.recursion() != null
                && node.recursion().role() == cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode.Role.STEP) return;
        float x = box.x(), y = box.y(), right = x + box.width(), bottom = y + box.height();
        GraphDraw.line(graphics, x, y, right, y, color, thickness / 2);
        GraphDraw.line(graphics, right, y, right, bottom, color, thickness / 2);
        GraphDraw.line(graphics, right, bottom, x, bottom, color, thickness / 2);
        GraphDraw.line(graphics, x, bottom, x, y, color, thickness / 2);
    }

    private static void drawCachedLink(GuiGraphics graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            ClientCraftingGraph.Link link, double pixelsPerUnit, int color) {
        if (graph.isRecursiveCycle()) {
            RecursiveCycleRenderer.drawLink(graphics, graph, layout, link, 0, 0, 1,
                color == 0xfff6c453 ? color : null);
            return;
        }
        var route = layout.edgePoints(link);
        if (route.size() < 2) {
            var from = layout.box(link.fromId());
            var to = layout.box(link.toId());
            if (from == null || to == null) return;
            if (graph.isCompactTree()) {
                float middle = (from.y() + from.height() + to.y()) / 2;
                route = List.of(new GraphLayoutSnapshot.Point(from.centerX(), from.y() + from.height()),
                    new GraphLayoutSnapshot.Point(from.centerX(), middle),
                    new GraphLayoutSnapshot.Point(to.centerX(), middle),
                    new GraphLayoutSnapshot.Point(to.centerX(), to.y()));
            } else {
                float middle = (from.x() + from.width() + to.x()) / 2;
                route = List.of(new GraphLayoutSnapshot.Point(from.x() + from.width(), from.centerY()),
                    new GraphLayoutSnapshot.Point(middle, from.centerY()),
                    new GraphLayoutSnapshot.Point(middle, to.centerY()),
                    new GraphLayoutSnapshot.Point(to.x(), to.centerY()));
            }
        }
        float half = (float) Math.max(0.35, 0.5 / pixelsPerUnit);
        for (int i = 1; i < route.size(); i++) {
            var from = route.get(i - 1);
            var to = route.get(i);
            GraphDraw.line(graphics, from.x(), from.y(), to.x(), to.y(), color, half);
        }
        if (graph.isCompactTree() || graph.isRecursiveCycle()) {
            if (layout.box(link.toId()) == null) return;
            var end = route.get(route.size() - 1);
            GraphDraw.fill(graphics, end.x() - 3, end.y() - 2, end.x() + 1, end.y() + 3, color);
        } else {
            drawArrowHead(graphics, route, color, 0, 0, 1);
            if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) {
                drawEdgeAmount(graphics, graph, link, route, 0, 0, 1);
            }
        }
    }

    private static final class TilePainter implements GraphViewportCache.Painter {
        private final ClientCraftingGraph graph;
        private final GraphLayoutSnapshot layout;
        private final double pixelsPerUnit;
        private final List<ClientCraftingGraph.Link> links;
        private final List<GraphLayoutSnapshot.Box> nodes;
        private final List<GraphLayoutSnapshot.Bounds> rows;
        private final List<GraphLayoutSnapshot.RecursionRegion> regions;
        private int linkCursor, nodeCursor, rowCursor, missingCursor, regionCursor;
        private boolean finished;

        TilePainter(ClientCraftingGraph graph, GraphLayoutSnapshot layout, GraphLayoutSnapshot.Bounds area,
                double pixelsPerUnit) {
            this.graph = graph;
            this.layout = layout;
            this.pixelsPerUnit = pixelsPerUnit;
            links = layout.queryLinks(area);
            nodes = layout.query(area, Integer.MAX_VALUE);
            rows = graph.isCompactTree() ? layout.queryRowFrames(area) : List.of();
            regions = layout.recursionRegions().stream().filter(region -> region.bounds().padded(180).intersects(area)).toList();
        }

        @Override
        public boolean advance(GuiGraphics graphics, long deadline) {
            int work = 0;
            do {
                if (regionCursor < regions.size()) {
                    RecursiveCycleRenderer.drawRegion(graphics, regions.get(regionCursor++), 0, 0, 1);
                } else if (linkCursor < links.size()) {
                    var link = links.get(linkCursor++);
                    drawCachedLink(graphics, graph, layout, link, pixelsPerUnit,
                        link.selected() ? 0xff8293a4 : 0xff66505a);
                } else if (nodeCursor < nodes.size()) {
                    var box = nodes.get(nodeCursor++);
                    var node = graph.nodes().get(box.nodeId());
                    if (node != null) {
                        if (graph.isCompactTree()) {
                            CompactTreeRenderer.drawNode(graphics, graph, node, box, false, false, 0, 0, 1);
                        } else drawNode(graphics, graph, node, box, false, false, 0, 0, 1);
                    }
                } else if (rowCursor < rows.size()) {
                    var row = rows.get(rowCursor++);
                    float half = (float) (0.5 / pixelsPerUnit);
                    GraphDraw.line(graphics, row.left(), row.top(), row.right(), row.top(), 0xff8293a4, half);
                    GraphDraw.line(graphics, row.right(), row.top(), row.right(), row.bottom(), 0xff8293a4, half);
                    GraphDraw.line(graphics, row.right(), row.bottom(), row.left(), row.bottom(), 0xff8293a4, half);
                    GraphDraw.line(graphics, row.left(), row.bottom(), row.left(), row.top(), 0xff8293a4, half);
                } else if (graph.isCompactTree() && missingCursor < nodes.size()) {
                    var box = nodes.get(missingCursor++);
                    var node = graph.nodes().get(box.nodeId());
                    if (node != null && node.material() != null
                            && node.material().status() == CraftingGraphSnapshot.MaterialStatus.MISSING) {
                        CompactTreeRenderer.drawNodeFrame(graphics, box, 0xffcf5e5e, 0, 0, 1);
                    }
                } else if (!finished) {
                    if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && !graph.isRecursiveCycle()) {
                        drawCycleOutputArrow(graphics, graph, layout, 0, 0, 1);
                    }
                    finished = true;
                    return true;
                } else return true;
            } while (++work < 128 && System.nanoTime() < deadline);
            return false;
        }
    }

    private static void drawLink(GuiGraphics graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            ClientCraftingGraph.Link link,
            @Nullable Integer selected, Set<Integer> neighborhood, float cameraX, float cameraY, float zoom) {
        if (graph.isRecursiveCycle()) {
            RecursiveCycleRenderer.drawLink(graphics, graph, layout, link, cameraX, cameraY, zoom,
                selected != null && (link.fromId() == selected || link.toId() == selected) ? 0xfff6c453 : null);
            return;
        }
        var from = layout.box(link.fromId());
        var to = layout.box(link.toId());
        if (from == null || to == null) return;
        boolean highlighted = selected != null && (link.fromId() == selected || link.toId() == selected)
            && (neighborhood.contains(link.fromId()) || neighborhood.contains(link.toId()));
        int color = highlighted ? 0xfff6c453 : link.selected() ? 0xff8293a4 : 0xff66505a;
        List<GraphLayoutSnapshot.Point> route = layout.edgePoints(link);
        if (route.size() >= 2 && graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
            if (!highlighted) color = cycleLinkColor(graph, link, route);
            int thickness = cycleLineThickness(zoom);
            drawRoutedLink(graphics, route, color, cameraX, cameraY, zoom, thickness);
            drawArrowHead(graphics, route, color, cameraX, cameraY, zoom, thickness);
            return;
        }
        if (route.size() >= 2) {
            drawRoutedLink(graphics, route, color, cameraX, cameraY, zoom);
            drawArrowHead(graphics, route, color, cameraX, cameraY, zoom);
            return;
        }
        int x1 = screen(cameraX, from.x() + from.width(), zoom);
        int y1 = screen(cameraY, from.centerY(), zoom);
        int x2 = screen(cameraX, to.x(), zoom);
        int y2 = screen(cameraY, to.centerY(), zoom);
        int middle = (x1 + x2) / 2;
        GraphDraw.hLine(graphics, Math.min(x1, middle), Math.max(x1, middle), y1, color);
        GraphDraw.vLine(graphics, middle, Math.min(y1, y2), Math.max(y1, y2), color);
        GraphDraw.hLine(graphics, Math.min(middle, x2), Math.max(middle, x2), y2, color);
        GraphDraw.fill(graphics, x2 - 4, y2 - 2, x2, y2 + 3, color);
    }

    private static void drawCycleOutputArrow(GuiGraphics graphics, ClientCraftingGraph graph,
            GraphLayoutSnapshot layout, float cameraX, float cameraY, float zoom) {
        var cycle = graph.source().cycleGroups().stream()
            .filter(value -> value.componentId() == graph.focusedCycleId()).findFirst().orElse(null);
        // Unsolved cycles are diagnostic structure, not executable output flow.
        if (cycle == null || (cycle.patternTimes().stream().noneMatch(value -> value.amount() > 0)
                && cycle.executionWitness().isEmpty())) return;
        Integer outputId = cycleOutputNodeId(graph);
        var output = outputId == null ? null : layout.box(outputId);
        if (output == null) return;
        var ring = layout.boxes().values().stream().filter(box -> !graph.isBoundaryMaterial(box.nodeId())).toList();
        float centerX = (float) ring.stream().mapToDouble(GraphLayoutSnapshot.Box::centerX).average()
            .orElse(output.centerX());
        float centerY = (float) ring.stream().mapToDouble(GraphLayoutSnapshot.Box::centerY).average()
            .orElse(output.centerY());
        float dx = output.centerX() - centerX;
        float dy = output.centerY() - centerY;
        float length = Math.max(0.001f, (float) Math.hypot(dx, dy));
        dx /= length;
        dy /= length;
        var start = new GraphLayoutSnapshot.Point(output.centerX() + dx * output.width() / 2,
            output.centerY() + dy * output.height() / 2);
        var end = new GraphLayoutSnapshot.Point(start.x() + dx * 30, start.y() + dy * 30);
        var route = List.of(start, end);
        int color = 0xff75c48b;
        int thickness = cycleLineThickness(zoom);
        drawRoutedLink(graphics, route, color, cameraX, cameraY, zoom, thickness);
        drawArrowHead(graphics, route, color, cameraX, cameraY, zoom, thickness);
    }

    /** Cycle focus lines widen with zoom so the loop reads as the primary structure on screen. */
    private static int cycleLineThickness(float zoom) {
        return zoom >= 1.25f ? 2 : 1;
    }

    /**
     * Colors encode the role of each strip edge: loop-closing lanes use the cycle accent, short external inputs
     * show red while still missing, and ordinary forward steps stay neutral.
     */
    private static int cycleLinkColor(ClientCraftingGraph graph, ClientCraftingGraph.Link link,
            List<GraphLayoutSnapshot.Point> route) {
        if (!link.selected()) return 0xff66505a;
        if (graph.isExternalInput(link.fromId())) {
            var node = graph.nodes().get(link.fromId());
            boolean missing = node != null && node.material() != null && node.material().missing() > 0;
            return missing ? 0xffcf5e5e : 0xff7d8c99;
        }
        if (graph.isBoundaryOutput(link.toId())) return 0xff6f9c80;
        // Strip layout runs forward steps left to right; anything ending further left closes the loop.
        if (route.getLast().x() < route.getFirst().x() - 0.5f) return 0xffd59b45;
        return 0xffa7b6c4;
    }

    static @Nullable Integer cycleOutputNodeId(ClientCraftingGraph graph) {
        var cycle = graph.source().cycleGroups().stream()
            .filter(value -> value.componentId() == graph.focusedCycleId()).findFirst().orElse(null);
        if (cycle == null) return null;
        for (var output : cycle.requiredOutputs()) {
            Integer id = materialId(graph, output.key());
            if (id != null && !graph.isBoundaryMaterial(id)) return id;
        }
        for (var output : cycle.singleNetOutputs()) {
            if (output.amount() <= 0) continue;
            Integer id = materialId(graph, output.key());
            if (id != null && !graph.isBoundaryMaterial(id)) return id;
        }
        var root = graph.nodes().get(graph.rootId());
        return root != null && root.kind() == ClientCraftingGraph.Kind.MATERIAL
            && !graph.isBoundaryMaterial(root.id()) ? root.id() : null;
    }

    private static @Nullable Integer materialId(ClientCraftingGraph graph, AEKey key) {
        return graph.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.MATERIAL && key.equals(node.key()))
            .map(ClientCraftingGraph.Node::id).findFirst().orElse(null);
    }

    private static void drawEdgeAmount(GuiGraphics graphics, ClientCraftingGraph graph,
            ClientCraftingGraph.Link link, List<GraphLayoutSnapshot.Point> route, float cameraX, float cameraY,
            float zoom) {
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && Math.abs(link.amount()) == 1) return;
        String text = edgeAmount(graph, link);
        if (text.isEmpty() || route.size() < 2) return;
        // Orthogonal routes have their middle point on a corner; label the longest run instead.
        int longest = 1;
        float longestLength = -1;
        for (int i = 1; i < route.size(); i++) {
            float length = Math.abs(route.get(i).x() - route.get(i - 1).x())
                + Math.abs(route.get(i).y() - route.get(i - 1).y());
            if (length > longestLength) {
                longestLength = length;
                longest = i;
            }
        }
        var a = route.get(longest - 1);
        var b = route.get(longest);
        int x = screen(cameraX, (a.x() + b.x()) / 2, zoom);
        int y = screen(cameraY, (a.y() + b.y()) / 2, zoom) + (a.x() == b.x() ? 4 : 0);
        var font = Minecraft.getInstance().font;
        int width = font.width(text) + 6;
        GraphDraw.fill(graphics, x - width / 2, y - 8, x + (width + 1) / 2, y + 3, 0xcc171b20);
        graphics.drawCenteredString(font, text, x, y - 7, MUTED);
    }

    private static String edgeAmount(ClientCraftingGraph graph, ClientCraftingGraph.Link link) {
        if (link.amount() == 0) return "";
        ClientCraftingGraph.Node material = graph.nodes().get(link.fromId());
        if (material == null || material.kind() != ClientCraftingGraph.Kind.MATERIAL) {
            material = graph.nodes().get(link.toId());
        }
        AEKey key = material == null || material.material() == null ? null : material.material().key();
        if (key == null && link.materialNodeId() >= 0) {
            key = graph.source().nodes().stream().filter(node -> node.nodeId() == link.materialNodeId())
                .map(CraftingGraphSnapshot.MaterialNode::key).findFirst().orElse(null);
        }
        if (key == null) return "×" + compactAmount(link.plannerAmount().toString());
        return "×" + formatMagnitude(key, link.plannerAmount());
    }

    private static void drawClusterLabels(GuiGraphics graphics, ClientCraftingGraph graph,
            GraphLayoutSnapshot layout, float cameraX, float cameraY, float zoom) {
        CycleCluster cluster = graph.focusedCluster();
        if (cluster == null) return;
        Font font = Minecraft.getInstance().font;
        for (int componentId : cluster.componentIds()) {
            Set<Integer> ids = new HashSet<>();
            graph.source().cycleGroups().stream().filter(cycle -> cycle.componentId() == componentId).findFirst()
                .ifPresent(cycle -> ids.addAll(cycle.memberNodeIds()));
            graph.nodes().values().stream().filter(node -> node.pattern() != null
                    && node.pattern().componentId() == componentId).map(ClientCraftingGraph.Node::id).forEach(ids::add);
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            for (int id : ids) {
                var box = layout.box(id);
                if (box == null) continue;
                minX = Math.min(minX, box.x());
                minY = Math.min(minY, box.y());
            }
            if (minX == Float.MAX_VALUE) continue;
            graphics.drawString(font, "循环 #" + componentId, screen(cameraX, minX, zoom),
                screen(cameraY, minY - 14, zoom), 0xffe2b766, false);
        }
    }

    private static void drawRoutedLink(GuiGraphics graphics, List<GraphLayoutSnapshot.Point> route, int color,
            float cameraX, float cameraY, float zoom) {
        for (int i = 1; i < route.size(); i++) {
            var from = route.get(i - 1);
            var to = route.get(i);
            int x1 = screen(cameraX, from.x(), zoom);
            int y1 = screen(cameraY, from.y(), zoom);
            int x2 = screen(cameraX, to.x(), zoom);
            int y2 = screen(cameraY, to.y(), zoom);
            if (x1 == x2) {
                GraphDraw.vLine(graphics, x1, Math.min(y1, y2), Math.max(y1, y2), color);
            } else if (y1 == y2) {
                GraphDraw.hLine(graphics, Math.min(x1, x2), Math.max(x1, x2), y1, color);
            } else {
                drawDiagonal(graphics, x1, y1, x2, y2, color);
            }
        }
    }

    /** Orthogonal segments become solid pixel rectangles; square caps fill each corner without gaps. */
    private static void drawRoutedLink(GuiGraphics graphics, List<GraphLayoutSnapshot.Point> route, int color,
            float cameraX, float cameraY, float zoom, int thickness) {
        if (thickness <= 1) {
            drawRoutedLink(graphics, route, color, cameraX, cameraY, zoom);
            return;
        }
        int before = thickness / 2;
        int after = thickness - before;
        for (int i = 1; i < route.size(); i++) {
            var from = route.get(i - 1);
            var to = route.get(i);
            int x1 = screen(cameraX, from.x(), zoom);
            int y1 = screen(cameraY, from.y(), zoom);
            int x2 = screen(cameraX, to.x(), zoom);
            int y2 = screen(cameraY, to.y(), zoom);
            if (x1 == x2 || y1 == y2) {
                GraphDraw.fill(graphics, Math.min(x1, x2) - before, Math.min(y1, y2) - before,
                    Math.max(x1, x2) + after, Math.max(y1, y2) + after, color);
            } else {
                GraphDraw.line(graphics, x1, y1, x2, y2, color, thickness / 2f);
            }
        }
    }

    private static void drawDiagonal(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        GraphDraw.line(graphics, x1, y1, x2, y2, color, 0.5f);
    }

    private static void drawNode(GuiGraphics graphics, ClientCraftingGraph graph, ClientCraftingGraph.Node node,
            GraphLayoutSnapshot.Box box,
            boolean selected, boolean adjacent, float cameraX, float cameraY, float zoom) {
        int x = screen(cameraX, box.x(), zoom);
        int y = screen(cameraY, box.y(), zoom);
        int right = screen(cameraX, box.x() + box.width(), zoom);
        int bottom = screen(cameraY, box.y() + box.height(), zoom);
        int pixelWidth = right - x;
        int pixelHeight = bottom - y;
        if (node.recursion() != null) {
            RecursiveCycleRenderer.drawNode(graphics, graph, node, box, selected, adjacent, cameraX, cameraY, zoom);
            return;
        }
        int border = selected ? 0xfff6c453 : adjacent ? 0xff83c5be : border(graph, node);
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
            drawCompactCycleNode(graphics, graph, node, box, border, selected, x, y, right, bottom,
                pixelWidth, pixelHeight, cameraY, zoom);
            return;
        }
        GraphDraw.fill(graphics, x, y, right, bottom, 0xee171b20);
        int borderWidth = selected ? 3 : 2;
        GraphDraw.fill(graphics, x, y, right, Math.min(bottom, y + borderWidth), border);
        GraphDraw.fill(graphics, x, Math.max(y, bottom - 2), right, bottom, border);
        GraphDraw.fill(graphics, x, y, Math.min(right, x + 2), bottom, border);
        GraphDraw.fill(graphics, Math.max(x, right - 2), y, right, bottom, border);
        if (pixelWidth < 18 || pixelHeight < 12) return;

        var font = Minecraft.getInstance().font;
        switch (node.kind()) {
            case MATERIAL -> {
                var material = node.material();
                if (zoom >= 0.7f && pixelWidth >= 82 && pixelHeight >= 48) {
                    AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x + 7, y + 8, material.key());
                    drawFitted(graphics, font, node.label(), x + 29, y + 7, pixelWidth - 35, TEXT);
                    drawFitted(graphics, font, statusSymbol(material.status()) + " " + material.status().name(),
                        x + 29, y + 18, pixelWidth - 35, border);
                    drawFitted(graphics, font,
                        "需求 " + HostText.ae2Amount(material.exactRequested()) + "  库存 "
                            + HostText.ae2Amount(material.exactFromInventory()),
                        x + 7, y + 38, pixelWidth - 14, MUTED);
                    if (pixelHeight >= 64) {
                        drawFitted(graphics, font,
                            "变化 " + signedTaskChange(material),
                            x + 7, y + 53, pixelWidth - 14, MUTED);
                    }
                } else if (zoom >= 0.42f && pixelWidth >= 48 && pixelHeight >= 22) {
                    AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x + 4, y + 4, material.key());
                    drawFitted(graphics, font, node.label(), x + 24, y + 4, pixelWidth - 28, TEXT);
                    if (pixelHeight >= 31) {
                        drawFitted(graphics, font, statusSymbol(material.status()) + " " + material.status().name(),
                            x + 24, y + 15, pixelWidth - 28, border);
                    }
                } else {
                    graphics.drawCenteredString(font, statusSymbol(material.status()), (x + right) / 2,
                        y + Math.max(2, (pixelHeight - font.lineHeight) / 2), border);
                }
            }
            case PATTERN -> {
                var pattern = node.pattern();
                String symbol = pattern.status() == CraftingGraphSnapshot.CandidateStatus.SELECTED ? "⚒" : "×";
                graphics.drawCenteredString(font, symbol, (x + right) / 2,
                    y + Math.max(3, pixelHeight >= 28 ? 6 : 2), border);
                if (pixelHeight >= 28 && pixelWidth >= 32) {
                    graphics.drawCenteredString(font,
                        fit(font, "× " + HostText.ae2Amount(pattern.plannerFiringCount().toBigInteger()), pixelWidth - 6),
                        (x + right) / 2, y + 18, TEXT);
                }
            }
            case CYCLE_GROUP -> {
                var cycle = node.cycle();
                drawFitted(graphics, font, recursionText("module", cycle.componentId()).getString(), x + 7, y + 7,
                    pixelWidth - 14, border);
                if (zoom >= 0.6f && pixelHeight >= 38) {
                    drawFitted(graphics, font, recursiveModuleSummary(cycle).getString(),
                        x + 7, y + 23, pixelWidth - 14, TEXT);
                    String required = cycle.requiredOutputs().isEmpty() ? "变化：-"
                        : "所需产物：" + cycle.requiredOutputs().size();
                    if (pixelHeight >= 55) drawFitted(graphics, font, required, x + 7, y + 40,
                        pixelWidth - 14, MUTED);
                    if (pixelHeight >= 68) drawFitted(graphics, font, recursionText("open").getString(), x + 7, y + 54,
                        pixelWidth - 14, MUTED);
                }
            }
            case CYCLE_CLUSTER -> {
                var cluster = node.cluster();
                drawFitted(graphics, font, recursionText("modules", cluster.clusterId()).getString(), x + 7, y + 7,
                    pixelWidth - 14, border);
                if (zoom >= 0.6f && pixelHeight >= 38) {
                    drawFitted(graphics, font, cluster.componentIds().size() + " 个独立循环 · "
                        + cluster.flows().size() + " 条跨环流", x + 7, y + 23, pixelWidth - 14, TEXT);
                    if (pixelHeight >= 55) drawFitted(graphics, font, recursionText("open_modules").getString(), x + 7, y + 40,
                        pixelWidth - 14, MUTED);
                }
            }
            case FOLDER -> { }
            case REFERENCE -> { }
        }
    }


    static Component recursionText(String suffix, Object... args) {
        return Component.translatable("gui.neoecoae.crafting_graph.recursion." + suffix, args);
    }

    static Component recursiveModuleSummary(CraftingGraphSnapshot.CycleGroup cycle) {
        if (cycle.requiredOutputs().isEmpty()) return recursionText("no_output");
        if (cycle.requiredOutputs().size() > 1) return recursionText("output_types", cycle.requiredOutputs().size());
        var output = cycle.requiredOutputs().get(0);
        return recursionText("module_target", formatMagnitude(output.key(), output.plannerAmount()));
    }


    private static void drawCompactCycleNode(GuiGraphics graphics, ClientCraftingGraph graph,
            ClientCraftingGraph.Node node, GraphLayoutSnapshot.Box box, int border, boolean selected, int x, int y,
            int right, int bottom, int pixelWidth, int pixelHeight, float cameraY, float zoom) {
        boolean strip = graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS;
        boolean pattern = node.kind() == ClientCraftingGraph.Kind.PATTERN;
        int borderWidth = selected ? 2 : 1;
        if (strip && pattern) {
            // Recipes read as process steps: tinted body, notched corners and a solid header bar.
            GraphDraw.fill(graphics, x + 1, y + 1, right - 1, bottom - 1, 0xee17221f);
            GraphDraw.fill(graphics, x + 2, y, right - 2, Math.min(bottom, y + Math.max(2, borderWidth)), border);
            GraphDraw.fill(graphics, x + 2, Math.max(y, bottom - borderWidth), right - 2, bottom, border);
            GraphDraw.fill(graphics, x, y + 2, Math.min(right, x + borderWidth), bottom - 2, border);
            GraphDraw.fill(graphics, Math.max(x, right - borderWidth), y + 2, right, bottom - 2, border);
            GraphDraw.fill(graphics, x + 1, y + 1, x + 2, y + 2, border);
            GraphDraw.fill(graphics, right - 2, y + 1, right - 1, y + 2, border);
            GraphDraw.fill(graphics, x + 1, bottom - 2, x + 2, bottom - 1, border);
            GraphDraw.fill(graphics, right - 2, bottom - 2, right - 1, bottom - 1, border);
        } else {
            GraphDraw.fill(graphics, x, y, right, bottom, 0xee171b20);
            GraphDraw.fill(graphics, x, y, right, Math.min(bottom, y + borderWidth), border);
            GraphDraw.fill(graphics, x, Math.max(y, bottom - borderWidth), right, bottom, border);
            GraphDraw.fill(graphics, x, y, Math.min(right, x + borderWidth), bottom, border);
            GraphDraw.fill(graphics, Math.max(x, right - borderWidth), y, right, bottom, border);
        }
        if (pixelWidth < 12 || pixelHeight < 18) return;

        Font font = Minecraft.getInstance().font;
        int numberTop = screen(cameraY, box.y() + 20, zoom);
        int iconBottom = Math.min(bottom, numberTop);
        if (node.kind() == ClientCraftingGraph.Kind.MATERIAL && node.material() != null) {
            int iconX = x + Math.max(0, (pixelWidth - 16) / 2);
            int iconY = y + Math.max(0, (iconBottom - y - 16) / 2);
            // Fluid and other non-item keys blit immediately; submit the buffered node body first, otherwise it
            // is drawn afterwards on top of the icon and hides it.
            if (!(node.material().key() instanceof AEItemKey)) graphics.flush();
            AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, iconX, iconY, node.material().key());
        } else if (pattern) {
            graphics.drawCenteredString(font, "⚒", (x + right) / 2,
                y + Math.max(1, (iconBottom - y - font.lineHeight) / 2), border);
        }

        CompactCycleValue value = compactCycleValue(graph, node);
        String text = strip && pattern ? "×" + value.text() : value.text();
        drawScaledAmount(graphics, font, text, x, numberTop, right, bottom, value.color(), strip ? 1.0f : 0.5f);
    }

    private record CompactCycleValue(String text, int color) {}

    private static CompactCycleValue compactCycleValue(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN && node.pattern() != null) {
            return new CompactCycleValue(HostText.ae2Amount(node.pattern().plannerFiringCount().toBigInteger()), TEXT);
        }
        if (node.material() == null) return new CompactCycleValue("-", MUTED);
        var cycle = graph.source().cycleGroups().stream()
            .filter(value -> graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS
                ? value.componentId() == graph.focusedCycleId()
                : value.memberNodeIds().contains(node.material().nodeId()))
            .findFirst().orElse(null);
        if (cycle == null) return new CompactCycleValue("-", MUTED);
        PlannerAmount delta = PlannerAmount.of(node.material().producedBigInteger())
            .subtract(PlannerAmount.of(node.material().consumedBigInteger()));
        String text = signedTaskChange(node.material());
        int color = delta.signum() > 0 ? 0xff75c48b : delta.signum() < 0 ? 0xffe07a7a : MUTED;
        return new CompactCycleValue(text, color);
    }

    private static void drawScaledAmount(GuiGraphics graphics, Font font, String amount, int left, int top,
            int right, int bottom, int color) {
        drawScaledAmount(graphics, font, amount, left, top, right, bottom, color, 0.5f);
    }

    private static void drawScaledAmount(GuiGraphics graphics, Font font, String amount, int left, int top,
            int right, int bottom, int color, float maxScale) {
        int availableWidth = Math.max(1, right - left - 2);
        int availableHeight = Math.max(1, bottom - top);
        float scale = Math.min(maxScale, Math.min((float) availableWidth / Math.max(1, font.width(amount)),
            (float) availableHeight / font.lineHeight));
        float centerX = (left + right) / 2.0f;
        float textY = top + (availableHeight - font.lineHeight * scale) / 2.0f;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, textY, 0);
        pose.scale(scale, scale, 1.0f);
        graphics.drawString(font, amount, -font.width(amount) / 2, 0, color, false);
        pose.popPose();
    }

    private static String signedTaskChange(CraftingGraphSnapshot.MaterialNode material) {
        PlannerAmount delta = PlannerAmount.of(material.producedBigInteger()).subtract(PlannerAmount.of(material.consumedBigInteger()));
        String magnitude = HostText.ae2Amount(delta.abs().toBigInteger());
        return delta.signum() > 0 ? "+" + magnitude : delta.signum() < 0 ? "-" + magnitude : "0";
    }

    private static int screen(float camera, float world, float zoom) {
        return Math.round(camera + world * zoom);
    }

    private static void drawFitted(GuiGraphics graphics, Font font, String text, int x, int y, int maxWidth,
            int color) {
        if (maxWidth > 0) graphics.drawString(font, fit(font, text, maxWidth), x, y, color, false);
    }

    private static String fit(Font font, String value, int maxWidth) {
        if (maxWidth <= 0 || font.width(value) <= maxWidth) return maxWidth <= 0 ? "" : value;
        String ellipsis = "…";
        int available = Math.max(0, maxWidth - font.width(ellipsis));
        return font.plainSubstrByWidth(value, available) + ellipsis;
    }

    private static List<Component> tooltip(ClientCraftingGraph graph, @Nullable ClientCraftingGraph.Node node) {
        if (node == null) return List.of();
        if (node.recursion() != null) return RecursiveCycleRenderer.tooltip(graph, node);
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN) {
            var pattern = node.pattern();
            var lines = new ArrayList<Component>();
            lines.add(Component.literal(pattern.displayIdentity()));
            lines.add(Component.literal("执行次数：" + HostText.ae2Amount(pattern.plannerFiringCount().toBigInteger())));
            lines.add(Component.literal("状态：" + pattern.status().name()));
            lines.add(Component.literal("输入：" + relationshipText(graph, pattern.inputs())));
            lines.add(Component.literal("输出：" + relationshipText(graph, pattern.outputs())));
            if (pattern.rejectionReason() != null) lines.add(Component.literal(pattern.rejectionReason()));
            return lines;
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) {
            var cycle = node.cycle();
            var lines = new ArrayList<Component>();
            lines.add(recursionText("module", cycle.componentId()));
            for (var output : cycle.requiredOutputs()) {
                lines.add(output.key().getDisplayName().copy().append(" × "
                    + formatMagnitude(output.key(), output.plannerAmount())));
            }
            lines.add(recursionText("open"));
            return lines;
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER) {
            var cluster = node.cluster();
            return List.of(recursionText("modules", cluster.clusterId()), recursionText("open_modules"),
                Component.literal("独立 SCC：" + cluster.componentIds().size()),
                Component.literal("跨环物料流：" + cluster.flows().size()),
                Component.literal("循环：" + cluster.componentIds()));
        }
        if (node.key() == null) return List.of();
        var lines = new ArrayList<>(AEKeyRendering.getTooltip(node.key()));
        lines.add(Component.literal("registry id: " + node.key().getId()));
        if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER && graph.focusedCluster() != null
                && node.material() != null) {
            for (InterCycleFlow flow : graph.focusedCluster().flows()) {
                if (flow.materialNodeId() != node.material().nodeId()) continue;
                lines.add(Component.literal("跨环流：循环 #" + flow.fromComponentId() + " → #"
                    + flow.toComponentId() + "（输出 ×" + HostText.ae2Amount(flow.outputAmount())
                    + " / 输入 ×" + HostText.ae2Amount(flow.inputAmount()) + "）"));
            }
        }
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && node.material() != null) {
            var cycle = graph.source().cycleGroups().stream()
                .filter(value -> value.componentId() == graph.focusedCycleId()).findFirst().orElse(null);
            if (cycle != null) {
                lines.add(Component.literal("单次变化：" + signedExact(
                    exactAmountFor(cycle.exactSingleNetOutputs(), cycle.singleNetOutputs(), node.key()))));
                lines.add(Component.literal("总变化：" + signedExact(
                    exactAmountFor(cycle.exactTotalNetOutputs(), cycle.totalNetOutputs(), node.key()))));
                lines.add(Component.literal("所需种子：" + amountText(cycle.requiredSeed(), node.key())));
                lines.add(Component.literal("所需产物：" + amountText(cycle.requiredOutputs(), node.key())));
                lines.add(Component.literal("外部输入：" + amountText(cycle.externalInputs(), node.key())));
                lines.add(Component.literal("任务消耗：" + HostText.ae2Amount(node.material().exactConsumed())));
                lines.add(Component.literal("任务产出：" + HostText.ae2Amount(node.material().exactProduced())));
            }
        }
        return lines;
    }


    private static int border(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) return 0xffd59b45;
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER) return 0xffcf8f45;
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN) {
            return node.pattern().status() == CraftingGraphSnapshot.CandidateStatus.SELECTED ? 0xff66a182 : 0xff9a5964;
        }
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && graph.isBoundaryMaterial(node.id())) {
            return graph.isExternalInput(node.id()) && node.material() != null && node.material().missing() > 0
                ? 0xffcf5e5e : 0xff71808a;
        }
        return switch (node.material().status()) {
            case SATISFIED -> 0xff68a878;
            case CRAFTING -> 0xff5f9fc5;
            case MISSING -> 0xffcf5e5e;
            case UNSUPPORTED -> 0xffa36c9a;
            case CYCLE -> 0xffd59b45;
        };
    }

    private static String statusSymbol(CraftingGraphSnapshot.MaterialStatus status) {
        return switch (status) {
            case SATISFIED -> "✓";
            case CRAFTING -> "⚒";
            case MISSING -> "!";
            case UNSUPPORTED -> "?";
            case CYCLE -> "↻";
        };
    }

    /** Draws a tangent-aligned triangle so curved cycle arrows do not snap to four directions. */
    static void drawArrowHead(GuiGraphics graphics, List<GraphLayoutSnapshot.Point> route, int color,
            float cameraX, float cameraY, float zoom) {
        drawArrowHead(graphics, route, color, cameraX, cameraY, zoom, 1);
    }

    private static void drawArrowHead(GuiGraphics graphics, List<GraphLayoutSnapshot.Point> route, int color,
            float cameraX, float cameraY, float zoom, int thickness) {
        float scale = 1 + (thickness - 1) * 0.5f;
        var end = route.getLast();
        GraphLayoutSnapshot.Point previous = route.get(route.size() - 2);
        for (int i = route.size() - 2; i >= 0 && previous.equals(end); i--) previous = route.get(i);
        float tipX = cameraX + end.x() * zoom;
        float tipY = cameraY + end.y() * zoom;
        float dx = (end.x() - previous.x()) * zoom;
        float dy = (end.y() - previous.y()) * zoom;
        float length = Math.max(0.001f, (float) Math.hypot(dx, dy));
        float unitX = dx / length;
        float unitY = dy / length;
        float baseX = tipX - unitX * 6f * scale;
        float baseY = tipY - unitY * 6f * scale;
        float normalX = -unitY * 3.5f * scale;
        float normalY = unitX * 3.5f * scale;
        fillTriangle(graphics, tipX, tipY, baseX + normalX, baseY + normalY,
            baseX - normalX, baseY - normalY, color);
    }

    private static void fillTriangle(GuiGraphics graphics, float ax, float ay, float bx, float by,
            float cx, float cy, int color) {
        GraphDraw.triangle(graphics, ax, ay, bx, by, cx, cy, color);
    }

    private static long amountFor(List<CraftingGraphSnapshot.KeyAmount> values, AEKey key) {
        return values.stream().filter(value -> value.key().equals(key)).mapToLong(
            CraftingGraphSnapshot.KeyAmount::amount).findFirst().orElse(0L);
    }

    private static String amountText(List<CraftingGraphSnapshot.KeyAmount> values, AEKey key) {
        return values.stream().filter(value -> value.key().equals(key)).findFirst()
            .map(value -> formatMagnitude(key, value.plannerAmount())).orElse("-");
    }

    private static String relationshipText(ClientCraftingGraph graph,
            List<CraftingGraphSnapshot.Relationship> relationships) {
        if (relationships.isEmpty()) return "-";
        return relationships.stream().map(relationship -> {
            var node = graph.nodes().get(relationship.materialNodeId());
            String label = node == null || node.key() == null ? "#" + relationship.materialNodeId()
                : node.key().getDisplayName().getString();
            return label + " ×" + HostText.ae2Amount(relationship.plannerAmount().toBigInteger());
        }).reduce((left, right) -> left + ", " + right).orElse("-");
    }

    private static String signed(AEKey key, long amount) {
        String value = formatMagnitude(key, amount);
        return amount > 0 ? "+" + value : amount < 0 ? "-" + value : "0";
    }

    private static BigInteger exactAmountFor(
            List<cn.dancingsnow.neoecoae.crafting.planner.snapshot.ExactKeyAmount> exactValues,
            List<CraftingGraphSnapshot.KeyAmount> fallback, AEKey key) {
        return exactValues.stream().filter(value -> value.key().equals(key)).findFirst()
            .map(value -> value.amount().value()).orElseGet(() -> BigInteger.valueOf(amountFor(fallback, key)));
    }

    private static String signedExact(BigInteger amount) {
        String magnitude = HostText.expandedStorageBytes(amount.abs());
        return amount.signum() > 0 ? "+" + magnitude : amount.signum() < 0 ? "-" + magnitude : "0";
    }

    private static String formatMagnitude(AEKey key, long amount) {
        long magnitude = amount == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(amount);
        return cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers.grouped(key.formatAmount(magnitude, AmountFormat.SLOT));
    }

    static String formatMagnitude(AEKey key, cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount amount) {
        return amount.fitsLong() ? formatMagnitude(key, amount.longValueExact())
            : cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter.format(
                amount.abs().toBigInteger(), key.getAmountPerUnit(), false);
    }

}
