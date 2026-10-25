package cn.dancingsnow.neoecoae.client.gui.graph;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.CycleCluster;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.crafting.graph.InterCycleFlow;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
        if (tooltipGraph != graph || tooltipNode != frame.hovered()) {
            tooltipGraph = graph;
            tooltipNode = frame.hovered();
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

        for (var link : links) {
            drawLink(graphics, graph, layout, link, selectedId, neighborhood, cameraX, cameraY, zoom);
            if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER && zoom >= 0.55f) {
                drawEdgeAmount(graphics, graph, link, layout.edgePoints(link), cameraX, cameraY, zoom);
            }
        }
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
            drawCycleOutputArrow(graphics, graph, layout, cameraX, cameraY, zoom);
        }
        if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) {
            drawClusterLabels(graphics, graph, layout, cameraX, cameraY, zoom);
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
                    drawOutline(graphics, layout.box(neighbor), area, 0xff83c5be, 1 / zoom);
                }
            }
        }
        if (selectedId != null) drawOutline(graphics, layout.box(selectedId), area, 0xfff6c453, 2 / zoom);
        if (hovered != null && (selectedId == null || selectedId != hovered.id())) {
            drawOutline(graphics, layout.box(hovered.id()), area, 0xff83c5be, 1 / zoom);
        }
    }

    private static void drawOutline(GuiGraphics graphics, @Nullable GraphLayoutSnapshot.Box box,
            GraphLayoutSnapshot.Bounds area, int color, float thickness) {
        if (box == null || !box.intersects(area.left(), area.top(), area.right(), area.bottom())) return;
        float x = box.x(), y = box.y(), right = x + box.width(), bottom = y + box.height();
        GraphDraw.line(graphics, x, y, right, y, color, thickness / 2);
        GraphDraw.line(graphics, right, y, right, bottom, color, thickness / 2);
        GraphDraw.line(graphics, right, bottom, x, bottom, color, thickness / 2);
        GraphDraw.line(graphics, x, bottom, x, y, color, thickness / 2);
    }

    private static void drawCachedLink(GuiGraphics graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            ClientCraftingGraph.Link link, double pixelsPerUnit, int color) {
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
        if (graph.isCompactTree()) {
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
        private int linkCursor, nodeCursor, rowCursor, missingCursor;
        private boolean finished;

        TilePainter(ClientCraftingGraph graph, GraphLayoutSnapshot layout, GraphLayoutSnapshot.Bounds area,
                double pixelsPerUnit) {
            this.graph = graph;
            this.layout = layout;
            this.pixelsPerUnit = pixelsPerUnit;
            links = layout.queryLinks(area);
            nodes = layout.query(area, Integer.MAX_VALUE);
            rows = graph.isCompactTree() ? layout.queryRowFrames(area) : List.of();
        }

        @Override
        public boolean advance(GuiGraphics graphics, long deadline) {
            int work = 0;
            do {
                if (linkCursor < links.size()) {
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
                    if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
                        drawCycleOutputArrow(graphics, graph, layout, 0, 0, 1);
                    }
                    if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) drawClusterLabels(graphics, graph, layout, 0, 0, 1);
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
        var from = layout.box(link.fromId());
        var to = layout.box(link.toId());
        if (from == null || to == null) return;
        boolean highlighted = selected != null && (link.fromId() == selected || link.toId() == selected)
            && (neighborhood.contains(link.fromId()) || neighborhood.contains(link.toId()));
        int color = highlighted ? 0xfff6c453 : link.selected() ? 0xff8293a4 : 0xff66505a;
        List<GraphLayoutSnapshot.Point> route = layout.edgePoints(link);
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
        drawRoutedLink(graphics, route, color, cameraX, cameraY, zoom);
        drawArrowHead(graphics, route, color, cameraX, cameraY, zoom);
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
        String text = edgeAmount(graph, link);
        if (text.isEmpty() || route.size() < 2) return;
        var point = route.get(route.size() / 2);
        int x = screen(cameraX, point.x(), zoom);
        int y = screen(cameraY, point.y(), zoom);
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
        if (key == null) return "×" + link.amount();
        long magnitude = link.amount() == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(link.amount());
        return "×" + key.formatAmount(magnitude, AmountFormat.SLOT);
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
        int border = selected ? 0xfff6c453 : adjacent ? 0xff83c5be : border(graph, node);
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS
                || graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) {
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
                        "需求 " + compactAmount(material.exactRequested()) + "  库存 "
                            + compactAmount(material.exactFromInventory()),
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
                        fit(font, "× " + compactAmount(Long.toString(pattern.firingCount())), pixelWidth - 6),
                        (x + right) / 2, y + 18, TEXT);
                }
            }
            case CYCLE_GROUP -> {
                var cycle = node.cycle();
                drawFitted(graphics, font, "↻  循环 #" + cycle.componentId(), x + 7, y + 7,
                    pixelWidth - 14, border);
                if (zoom >= 0.6f && pixelHeight >= 38) {
                    drawFitted(graphics, font, cycle.memberNodeIds().size() + " 个成员 · " + cycle.status(),
                        x + 7, y + 23, pixelWidth - 14, TEXT);
                    String required = cycle.requiredOutputs().isEmpty() ? "变化：-"
                        : "所需产物：" + cycle.requiredOutputs().size();
                    if (pixelHeight >= 55) drawFitted(graphics, font, required, x + 7, y + 40,
                        pixelWidth - 14, MUTED);
                    if (pixelHeight >= 68) drawFitted(graphics, font, "双击查看内部图", x + 7, y + 54,
                        pixelWidth - 14, MUTED);
                }
            }
            case CYCLE_CLUSTER -> {
                var cluster = node.cluster();
                drawFitted(graphics, font, "◎  循环簇 #" + cluster.clusterId(), x + 7, y + 7,
                    pixelWidth - 14, border);
                if (zoom >= 0.6f && pixelHeight >= 38) {
                    drawFitted(graphics, font, cluster.componentIds().size() + " 个独立循环 · "
                        + cluster.flows().size() + " 条跨环流", x + 7, y + 23, pixelWidth - 14, TEXT);
                    if (pixelHeight >= 55) drawFitted(graphics, font, "双击查看循环簇", x + 7, y + 40,
                        pixelWidth - 14, MUTED);
                }
            }
            case FOLDER -> { }
            case REFERENCE -> { }
        }
    }

    private static void drawCompactCycleNode(GuiGraphics graphics, ClientCraftingGraph graph,
            ClientCraftingGraph.Node node, GraphLayoutSnapshot.Box box, int border, boolean selected, int x, int y,
            int right, int bottom, int pixelWidth, int pixelHeight, float cameraY, float zoom) {
        GraphDraw.fill(graphics, x, y, right, bottom, 0xee171b20);
        int borderWidth = selected ? 2 : 1;
        GraphDraw.fill(graphics, x, y, right, Math.min(bottom, y + borderWidth), border);
        GraphDraw.fill(graphics, x, Math.max(y, bottom - borderWidth), right, bottom, border);
        GraphDraw.fill(graphics, x, y, Math.min(right, x + borderWidth), bottom, border);
        GraphDraw.fill(graphics, Math.max(x, right - borderWidth), y, right, bottom, border);
        if (pixelWidth < 12 || pixelHeight < 18) return;

        Font font = Minecraft.getInstance().font;
        int numberTop = screen(cameraY, box.y() + 20, zoom);
        int iconBottom = Math.min(bottom, numberTop);
        if (node.kind() == ClientCraftingGraph.Kind.MATERIAL && node.material() != null) {
            int iconX = x + Math.max(0, (pixelWidth - 16) / 2);
            int iconY = y + Math.max(0, (iconBottom - y - 16) / 2);
            AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, iconX, iconY, node.material().key());
        } else if (node.kind() == ClientCraftingGraph.Kind.PATTERN) {
            graphics.drawCenteredString(font, "⚒", (x + right) / 2,
                y + Math.max(1, (iconBottom - y - font.lineHeight) / 2), border);
        }

        CompactCycleValue value = compactCycleValue(graph, node);
        drawScaledAmount(graphics, font, value.text(), x, numberTop, right, bottom, value.color());
    }

    private record CompactCycleValue(String text, int color) {}

    private static CompactCycleValue compactCycleValue(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN && node.pattern() != null) {
            return new CompactCycleValue(compactAmount(Long.toString(node.pattern().firingCount())), TEXT);
        }
        if (node.material() == null) return new CompactCycleValue("-", MUTED);
        var cycle = graph.source().cycleGroups().stream()
            .filter(value -> graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS
                ? value.componentId() == graph.focusedCycleId()
                : value.memberNodeIds().contains(node.material().nodeId()))
            .findFirst().orElse(null);
        if (cycle == null) return new CompactCycleValue("-", MUTED);
        BigInteger consumed = node.material().consumedBigInteger();
        BigInteger produced = node.material().producedBigInteger();
        BigInteger delta = produced.subtract(consumed);
        String text = signedTaskChange(node.material());
        int color = delta.signum() > 0 ? 0xff75c48b : delta.signum() < 0 ? 0xffe07a7a : MUTED;
        return new CompactCycleValue(text, color);
    }

    private static void drawScaledAmount(GuiGraphics graphics, Font font, String amount, int left, int top,
            int right, int bottom, int color) {
        int availableWidth = Math.max(1, right - left - 2);
        int availableHeight = Math.max(1, bottom - top);
        float scale = Math.min(0.5f, Math.min((float) availableWidth / Math.max(1, font.width(amount)),
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
        BigInteger delta = material.producedBigInteger().subtract(material.consumedBigInteger());
        String magnitude = compactAmount(delta.abs().toString());
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
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN) {
            var pattern = node.pattern();
            var lines = new ArrayList<Component>();
            lines.add(Component.literal(pattern.displayIdentity()));
            lines.add(Component.literal("执行次数：" + compactAmount(Long.toString(pattern.firingCount()))));
            lines.add(Component.literal("状态：" + pattern.status().name()));
            lines.add(Component.literal("输入：" + relationshipText(graph, pattern.inputs())));
            lines.add(Component.literal("输出：" + relationshipText(graph, pattern.outputs())));
            if (pattern.rejectionReason() != null) lines.add(Component.literal(pattern.rejectionReason()));
            return lines;
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) {
            var cycle = node.cycle();
            return List.of(Component.literal("循环 #" + cycle.componentId()), Component.literal(cycle.status()),
                Component.literal("成员：" + cycle.memberNodeIds().size()),
                Component.literal("所需产物：" + cycle.requiredOutputs().size()),
                Component.literal("外部输入：" + cycle.externalInputs().size()),
                Component.literal("所需种子：" + cycle.requiredSeed().size()));
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER) {
            var cluster = node.cluster();
            return List.of(Component.literal("循环簇 #" + cluster.clusterId()),
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
                    + flow.toComponentId() + "（输出 ×" + compactAmount(Long.toString(flow.outputAmount()))
                    + " / 输入 ×" + compactAmount(Long.toString(flow.inputAmount())) + "）"));
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
                lines.add(Component.literal("任务消耗：" + compactAmount(node.material().exactConsumed())));
                lines.add(Component.literal("任务产出：" + compactAmount(node.material().exactProduced())));
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
            return 0xff71808a;
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

    private static String compactAmount(String value) {
        try {
            return HostText.ae2Amount(new BigInteger(value));
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    /** Draws a tangent-aligned triangle so curved cycle arrows do not snap to four directions. */
    private static void drawArrowHead(GuiGraphics graphics, List<GraphLayoutSnapshot.Point> route, int color,
            float cameraX, float cameraY, float zoom) {
        var end = cn.dancingsnow.neoecoae.platform.JdkCompat.last(route);
        GraphLayoutSnapshot.Point previous = route.get(route.size() - 2);
        for (int i = route.size() - 2; i >= 0 && previous.equals(end); i--) previous = route.get(i);
        float tipX = cameraX + end.x() * zoom;
        float tipY = cameraY + end.y() * zoom;
        float dx = (end.x() - previous.x()) * zoom;
        float dy = (end.y() - previous.y()) * zoom;
        float length = Math.max(0.001f, (float) Math.hypot(dx, dy));
        float unitX = dx / length;
        float unitY = dy / length;
        float baseX = tipX - unitX * 6f;
        float baseY = tipY - unitY * 6f;
        float normalX = -unitY * 3.5f;
        float normalY = unitX * 3.5f;
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
            .map(value -> formatMagnitude(key, value.amount())).orElse("-");
    }

    private static String relationshipText(ClientCraftingGraph graph,
            List<CraftingGraphSnapshot.Relationship> relationships) {
        if (relationships.isEmpty()) return "-";
        return relationships.stream().map(relationship -> {
            var node = graph.nodes().get(relationship.materialNodeId());
            String label = node == null || node.key() == null ? "#" + relationship.materialNodeId()
                : node.key().getDisplayName().getString();
            return label + " ×" + compactAmount(Long.toString(relationship.amount()));
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

}
