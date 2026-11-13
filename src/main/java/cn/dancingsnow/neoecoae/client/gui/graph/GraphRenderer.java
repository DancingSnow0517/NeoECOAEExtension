package cn.dancingsnow.neoecoae.client.gui.graph;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.CraftingGraphProgress;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Bounds;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the graph in world units under one camera transform. Sparse views paint live; dense views record and
 * replay the same GUI layers, so both paths show an identical picture at every zoom level.
 */
public final class GraphRenderer implements AutoCloseable {
    private static final Identifier COMPLETED = Identifier.fromNamespaceAndPath("neoecoae",
        "textures/gui/graph/completed.png");
    public record Frame(@Nullable ClientCraftingGraph.Node hovered, List<Component> tooltip) {}
    record VisiblePlan(List<GraphLayoutSnapshot.Box> nodes, List<ClientCraftingGraph.Link> edges, int nodeCount) {}
    /** Screen rectangle reserved for the graph canvas. */
    record Viewport(int left, int top, int right, int bottom) {
        boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
    }

    private final GraphVisibilityCache visibility = new GraphVisibilityCache();
    private final GraphViewportCache canvas = new GraphViewportCache();
    private VisiblePlan livePlan;
    private ScenePainter liveScene;
    private ClientCraftingGraph hitGraph;
    private GraphLayoutSnapshot hitLayout;
    private float hitX, hitY;
    private ClientCraftingGraph.Node hitNode;
    private GraphLayoutSnapshot progressLayout;
    private Bounds progressArea;
    private VisiblePlan progressVisible;
    private boolean cachedView;
    private ClientCraftingGraph tooltipGraph;
    private ClientCraftingGraph.Node tooltipNode;
    private Language tooltipLanguage;
    private List<Component> tooltipLines = List.of();

    public static void resourcesReloaded() { GraphViewportCache.resourcesReloaded(); }

    static Bounds worldArea(float cameraX, float cameraY, float zoom, Viewport viewport) {
        return new Bounds((viewport.left() - cameraX) / zoom, (viewport.top() - cameraY) / zoom,
            (viewport.right() - cameraX) / zoom, (viewport.bottom() - cameraY) / zoom);
    }

    /** Count hysteresis keeps the view from flickering between the live and raster paths. */
    static boolean useCache(int visibleNodes, boolean previous) {
        return visibleNodes > (previous ? 192 : GraphVisibilityCache.LIVE_LIMIT);
    }

    /** Rendering, clicks and EMI share the same pick result at the displayed camera position. */
    @Nullable ClientCraftingGraph.Node hit(ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            float worldX, float worldY) {
        if (hitGraph == graph && hitLayout == layout && hitX == worldX && hitY == worldY) return hitNode;
        hitGraph = graph;
        hitLayout = layout;
        hitX = worldX;
        hitY = worldY;
        hitNode = null;
        for (var box : layout.query(new Bounds(worldX, worldY, worldX, worldY), 1)) {
            if (box.contains(worldX, worldY)) hitNode = graph.nodes().get(box.nodeId());
        }
        return hitNode;
    }

    Frame render(GuiGraphicsExtractor graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            float cameraX, float cameraY, float zoom, Viewport viewport, int mouseX, int mouseY,
            @Nullable Integer selectedId, CraftingGraphProgress progress) {
        // Reserve a GUI stratum for the graph before submitting its layers.
        graphics.nextStratum();

        var area = worldArea(cameraX, cameraY, zoom, viewport);
        VisiblePlan visible = visibility.get(layout, area.padded(24 / zoom));
        cachedView = useCache(visible.nodeCount(), cachedView);
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        if (cachedView) {
            canvas.prepare(graphics, graph, layout, area, zoom,
                (tile, pixelsPerUnit) -> new ScenePainter(graph, layout, tile, pixelsPerUnit,
                    layout.query(tile, Integer.MAX_VALUE), layout.queryLinks(tile)));
        }
        ClientCraftingGraph.Node hovered = null;
        if (viewport.contains(mouseX, mouseY)) {
            float worldX = (mouseX - cameraX) / zoom;
            float worldY = (mouseY - cameraY) / zoom;
            hovered = hit(graph, layout, worldX, worldY);
        }
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(cameraX, cameraY);
            pose.scale(zoom, zoom);
            double pixelsPerUnit = zoom * guiScale;
            if (cachedView) {
                canvas.draw(graphics);
            } else {
                if (livePlan != visible || liveScene == null || liveScene.graph != graph) {
                    livePlan = visible;
                    liveScene = new ScenePainter(graph, layout, area.padded(180), pixelsPerUnit,
                        visible.nodes(), visible.edges());
                }
                liveScene.restart(pixelsPerUnit);
                liveScene.advance(graphics, Long.MAX_VALUE);
            }

            graphics.nextStratum();
            drawHighlights(graphics, graph, layout, area, selectedId, hovered, pixelsPerUnit);
            graphics.nextStratum();
            if (!progress.running().isEmpty() || !progress.completed().isEmpty()) {
                var overlay = visible;
                if (visible.nodeCount() > GraphVisibilityCache.LIVE_LIMIT) {
                    if (progressLayout != layout || !area.equals(progressArea)) {
                        progressLayout = layout;
                        progressArea = area;
                        progressVisible = new VisiblePlan(layout.query(area.padded(24 / zoom), Integer.MAX_VALUE),
                            layout.queryLinks(area), visible.nodeCount());
                    }
                    overlay = progressVisible;
                }
                drawProgress(graphics, graph, layout, overlay, progress, pixelsPerUnit);
            }
        } finally {
            pose.popMatrix();
            graphics.disableScissor();
        }
        // Keep later screen overlays above every graph layer.
        graphics.nextStratum();

        // Hover text contains exact amount formatting; keep it off the stationary-frame hot path.
        if (tooltipGraph != graph || tooltipNode != hovered || tooltipLanguage != Language.getInstance()) {
            tooltipGraph = graph;
            tooltipNode = hovered;
            tooltipLanguage = Language.getInstance();
            tooltipLines = tooltip(graph, hovered);
        }
        return new Frame(hovered, tooltipLines);
    }

    @Override
    public void close() {
        canvas.close();
        progressLayout = null;
        progressArea = null;
        progressVisible = null;
        visibility.clear();
        livePlan = null;
        liveScene = null;
        hitGraph = null;
        hitLayout = null;
        hitNode = null;
        tooltipGraph = null;
        tooltipNode = null;
        tooltipLanguage = null;
        tooltipLines = List.of();
        cachedView = false;
    }

    /** Width in world units that is never thinner than about one physical pixel. */
    static float halfWidth(double pixelsPerUnit, float preferred) {
        return (float) Math.max(preferred, 0.55 / pixelsPerUnit);
    }

    private static void drawHighlights(GuiGraphicsExtractor graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            Bounds area, @Nullable Integer selectedId, @Nullable ClientCraftingGraph.Node hovered,
            double pixelsPerUnit) {
        float thin = halfWidth(pixelsPerUnit, 0.5f) * 2;
        var theme = GraphTheme.current();
        if (selectedId != null) {
            for (var link : layout.linksAt(selectedId)) {
                if (!layout.linkBounds(link).intersects(area)) continue;
                drawLink(graphics, graph, layout, link, theme.accent(), pixelsPerUnit);
                int neighbor = link.fromId() == selectedId ? link.toId() : link.fromId();
                outline(graphics, layout.box(neighbor), area, theme.muted(), thin);
            }
            outline(graphics, layout.box(selectedId), area, theme.accent(), thin * 2);
        }
        if (hovered != null && (selectedId == null || selectedId != hovered.id())) {
            outline(graphics, layout.box(hovered.id()), area, theme.muted(), thin);
        }
    }

    private static void outline(GuiGraphicsExtractor graphics, @Nullable GraphLayoutSnapshot.Box box, Bounds area, int color,
            float thickness) {
        if (box == null || !box.intersects(area.left(), area.top(), area.right(), area.bottom())) return;
        GraphDraw.frame(graphics, box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), color, thickness);
    }

    /** Execution changes never invalidate raster tiles; animate and mark completion above the cached scene. */
    private static void drawProgress(GuiGraphicsExtractor graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            VisiblePlan visible, CraftingGraphProgress progress, double pixelsPerUnit) {
        if (progress.running().isEmpty() && progress.completed().isEmpty()) return;
        var pose = graphics.pose();
        pose.pushMatrix();
        graphics.nextStratum();
        try {
            double phase = (Util.getMillis() % 1_200L) / 1_200.0;
            float half = halfWidth(pixelsPerUnit, 1.35f);
            for (var link : visible.edges()) {
                var source = graph.nodes().get(link.fromId());
                if (!link.selected()) continue;
                if (graph.isRecursiveCycle()) {
                    // Recursive rows are checkpoints, not separate live executions of the whole cycle.
                    var target = graph.nodes().get(link.toId());
                    if (source == null || source.recursion() == null
                            || source.recursion().role() != cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode.Role.STEP
                            || !running(graph, target, progress)) continue;
                } else if (!running(graph, source, progress)) continue;
                var points = route(graph, layout, link);
                if (points.size() < 2) continue;
                double length = 0;
                for (int i = 1; i < points.size(); i++) length += distance(points.get(i - 1), points.get(i));
                if (length <= 0) continue;
                // One square per active connection follows the complete polyline, including its bends.
                double remaining = length * (graph.isRecursiveCycle() ? phase : 1 - phase);
                for (int i = 1; i < points.size(); i++) {
                    var from = points.get(i - 1);
                    var to = points.get(i);
                    double segment = distance(from, to);
                    if (remaining <= segment && segment > 0) {
                        float x = (float) (from.x() + (to.x() - from.x()) * remaining / segment);
                        float y = (float) (from.y() + (to.y() - from.y()) * remaining / segment);
                        GraphDraw.fill(graphics, x - half, y - half, x + half, y + half,
                            GraphTheme.current().accent());
                        break;
                    }
                    remaining -= segment;
                }
            }
            for (var box : visible.nodes()) {
                var node = graph.nodes().get(box.nodeId());
                if (node == null || node.material() == null || node.kind() == ClientCraftingGraph.Kind.FOLDER
                        || !progress.completed().contains(node.material().nodeId())) continue;
                pose.pushMatrix();
                pose.translate(box.x() + box.width() - 6, box.y() - 2);
                graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, COMPLETED, 0, 0, 0, 0, 8, 8, 8, 8);
                pose.popMatrix();
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static boolean running(ClientCraftingGraph graph, @Nullable ClientCraftingGraph.Node node,
            CraftingGraphProgress progress) {
        if (node == null || node.kind() == ClientCraftingGraph.Kind.FOLDER) return false;
        if (node.material() != null) return progress.running().contains(node.material().nodeId());
        if (node.pattern() != null) return node.pattern().outputs().stream()
            .anyMatch(output -> progress.running().contains(output.materialNodeId()));
        if (node.cycle() != null) return node.cycle().memberNodeIds().stream().anyMatch(progress.running()::contains);
        if (node.cluster() != null) return node.cluster().componentIds().stream().anyMatch(component ->
            graph.source().cycleGroups().stream().filter(cycle -> cycle.componentId() == component)
                .anyMatch(cycle -> cycle.memberNodeIds().stream().anyMatch(progress.running()::contains)));
        return false;
    }

    private static double distance(GraphLayoutSnapshot.Point from, GraphLayoutSnapshot.Point to) {
        return Math.hypot(to.x() - from.x(), to.y() - from.y());
    }

    /** One scene, painted in phases so a raster tile can stop at any work unit and resume next frame. */
    private static final class ScenePainter implements GraphViewportCache.Painter {
        private final ClientCraftingGraph graph;
        private final GraphLayoutSnapshot layout;
        private double pixelsPerUnit;
        private final List<GraphLayoutSnapshot.Box> nodes;
        private final List<ClientCraftingGraph.Link> links;
        private final List<Bounds> rows;
        private final List<GraphLayoutSnapshot.RecursionRegion> regions;
        private int phase, cursor;

        ScenePainter(ClientCraftingGraph graph, GraphLayoutSnapshot layout, Bounds area, double pixelsPerUnit,
                List<GraphLayoutSnapshot.Box> nodes, List<ClientCraftingGraph.Link> links) {
            this.graph = graph;
            this.layout = layout;
            this.pixelsPerUnit = pixelsPerUnit;
            this.nodes = nodes;
            this.links = links;
            rows = graph.isCompactTree() ? layout.queryRowFrames(area) : List.of();
            regions = layout.recursionRegions().stream()
                .filter(region -> region.bounds().padded(180).intersects(area)).toList();
        }

        void restart(double nextPixelsPerUnit) {
            pixelsPerUnit = nextPixelsPerUnit;
            phase = cursor = 0;
        }

        @Override
        public boolean advance(GuiGraphicsExtractor graphics, long deadline) {
            int work = 0;
            while (true) {
                int size = switch (phase) {
                    case 0 -> regions.size();
                    case 1 -> links.size();
                    case 2, 3, 4 -> nodes.size();
                    case 5 -> rows.size();
                    case 6 -> graph.focusedCluster() == null ? 0 : graph.focusedCluster().componentIds().size();
                    default -> -1;
                };
                if (size < 0) return true;
                if (cursor >= size) {
                    graphics.nextStratum();

                    phase++;
                    cursor = 0;
                    continue;
                }
                int index = cursor++;
                switch (phase) {
                    case 0 -> RecursiveCycleRenderer.drawRegion(graphics, regions.get(index), pixelsPerUnit);
                    case 1 -> {
                        var link = links.get(index);
                        drawLink(graphics, graph, layout, link, link.selected() ? GraphTheme.current().edge()
                            : GraphTheme.current().edgeRejected(), pixelsPerUnit);
                    }
                    case 2 -> body(graphics, nodes.get(index));
                    case 3 -> icon(graphics, nodes.get(index));
                    case 4 -> text(graphics, nodes.get(index));
                    case 5 -> {
                        var row = rows.get(index);
                        GraphDraw.frame(graphics, row.left(), row.top(), row.right(), row.bottom(), GraphTheme.current().line(),
                            halfWidth(pixelsPerUnit, 0.5f) * 2);
                    }
                    case 6 -> clusterLabel(graphics, graph.focusedCluster().componentIds().get(index));
                    default -> { }
                }
                if (++work >= 64) {
                    if (System.nanoTime() >= deadline) return false;
                    work = 0;
                }
            }
        }

        /** Names each recursive module above its members inside a cluster view. */
        private void clusterLabel(GuiGraphicsExtractor graphics, int componentId) {
            float left = Float.MAX_VALUE, top = Float.MAX_VALUE;
            for (var box : layout.boxes().values()) {
                var node = graph.nodes().get(box.nodeId());
                if (node == null || componentOf(graph, node) != componentId) continue;
                left = Math.min(left, box.x());
                top = Math.min(top, box.y());
            }
            if (left == Float.MAX_VALUE) return;
            GraphDraw.text(graphics, Minecraft.getInstance().font, recursionText("module", componentId).getString(),
                left, top - 12, GraphTheme.current().accent(), 1);
        }

        private void body(GuiGraphicsExtractor graphics, GraphLayoutSnapshot.Box box) {
            var node = graph.nodes().get(box.nodeId());
            if (node == null) return;
            if (node.recursion() != null) RecursiveCycleRenderer.drawBody(graphics, node, box, pixelsPerUnit);
            else CompactTreeRenderer.drawBody(graphics, box);
        }

        private void icon(GuiGraphicsExtractor graphics, GraphLayoutSnapshot.Box box) {
            var node = graph.nodes().get(box.nodeId());
            if (node == null) return;
            if (node.recursion() != null) RecursiveCycleRenderer.drawIcon(graphics, node, box);
            else CompactTreeRenderer.drawIcon(graphics, node, box);
        }

        private void text(GuiGraphicsExtractor graphics, GraphLayoutSnapshot.Box box) {
            var node = graph.nodes().get(box.nodeId());
            if (node == null) return;
            float thin = halfWidth(pixelsPerUnit, 0.5f) * 2;
            if (node.recursion() != null) {
                RecursiveCycleRenderer.drawText(graphics, graph, node, box, thin);
                return;
            }
            CompactTreeRenderer.drawText(graphics, graph, node, box);
            if (!graph.isCompactTree()) {
                GraphDraw.frame(graphics, box.x(), box.y(), box.x() + box.width(), box.y() + box.height(),
                    CompactTreeRenderer.nodeBorder(graph, node), thin);
            } else {
                GraphDraw.frame(graphics, box.x(), box.y(), box.x() + box.width(), box.y() + box.height(),
                    GraphTheme.current().line(), thin);
                CompactTreeRenderer.drawStatusStrip(graphics, graph, node, box);
            }
            if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) {
                for (var link : layout.linksAt(node.id())) {
                    if (link.toId() == node.id()) drawEdgeAmount(graphics, graph, layout, link);
                }
            }
        }
    }

    /** Recursive module that owns a pattern or cycle-member material, or -1. */
    static int componentOf(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        if (node.pattern() != null) return node.pattern().componentId();
        if (node.material() == null) return -1;
        int materialId = node.material().nodeId();
        return graph.source().cycleGroups().stream().filter(cycle -> cycle.memberNodeIds().contains(materialId))
            .mapToInt(CraftingGraphSnapshot.CycleGroup::componentId).findFirst().orElse(-1);
    }

    private static void drawLink(GuiGraphicsExtractor graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            ClientCraftingGraph.Link link, int color, double pixelsPerUnit) {
        if (graph.isRecursiveCycle()) {
            RecursiveCycleRenderer.drawLink(graphics, graph, layout, link, color == GraphTheme.current().accent() ? color : null,
                pixelsPerUnit);
            return;
        }
        var route = route(graph, layout, link);
        if (route.size() < 2) return;
        float half = halfWidth(pixelsPerUnit, 0.5f);
        for (int i = 1; i < route.size(); i++) {
            var from = route.get(i - 1);
            var to = route.get(i);
            GraphDraw.segment(graphics, from.x(), from.y(), to.x(), to.y(), color, half);
        }
        var end = route.getLast();
        if (graph.isCompactTree()) GraphDraw.fill(graphics, end.x() - 2, end.y() - 2, end.x() + 2, end.y(), color);
        else drawArrowHead(graphics, route, color);
    }

    /** Layout route, or an orthogonal fallback matching the layout direction. */
    private static List<GraphLayoutSnapshot.Point> route(ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            ClientCraftingGraph.Link link) {
        var route = layout.edgePoints(link);
        if (route.size() >= 2) return route;
        var from = layout.box(link.fromId());
        var to = layout.box(link.toId());
        if (from == null || to == null) return List.of();
        if (graph.isCompactTree()) {
            float middle = (from.y() + from.height() + to.y()) / 2;
            return List.of(new GraphLayoutSnapshot.Point(from.centerX(), from.y() + from.height()),
                new GraphLayoutSnapshot.Point(from.centerX(), middle),
                new GraphLayoutSnapshot.Point(to.centerX(), middle),
                new GraphLayoutSnapshot.Point(to.centerX(), to.y()));
        }
        float middle = (from.x() + from.width() + to.x()) / 2;
        return List.of(new GraphLayoutSnapshot.Point(from.x() + from.width(), from.centerY()),
            new GraphLayoutSnapshot.Point(middle, from.centerY()),
            new GraphLayoutSnapshot.Point(middle, to.centerY()),
            new GraphLayoutSnapshot.Point(to.x(), to.centerY()));
    }

    /** Amount label on the longest run of an orthogonal route. */
    private static void drawEdgeAmount(GuiGraphicsExtractor graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            ClientCraftingGraph.Link link) {
        if (link.amount() == 0) return;
        var route = route(graph, layout, link);
        if (route.size() < 2) return;
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
        String text = "×" + edgeAmount(graph, link);
        Font font = Minecraft.getInstance().font;
        float scale = 0.75f;
        float width = font.width(text) * scale + 4;
        float x = (a.x() + b.x()) / 2;
        float y = (a.y() + b.y()) / 2;
        GraphDraw.fill(graphics, x - width / 2, y - 4.5f, x + width / 2, y + 4.5f,
            GraphTheme.alpha(GraphTheme.current().panel(), 0xcc));
        GraphDraw.centeredText(graphics, font, text, x, y - 3.5f, GraphTheme.current().muted(), scale);
    }

    private static String edgeAmount(ClientCraftingGraph graph, ClientCraftingGraph.Link link) {
        ClientCraftingGraph.Node material = graph.nodes().get(link.fromId());
        if (material == null || material.material() == null) material = graph.nodes().get(link.toId());
        AEKey key = material == null || material.material() == null ? null : material.material().key();
        return key == null ? HostText.ae2Amount(link.plannerAmount().toBigInteger())
            : formatMagnitude(key, link.plannerAmount());
    }

    /** Tangent-aligned triangle so curved or diagonal routes do not snap to four directions. */
    static void drawArrowHead(GuiGraphicsExtractor graphics, List<GraphLayoutSnapshot.Point> route, int color) {
        var end = route.getLast();
        GraphLayoutSnapshot.Point previous = route.get(route.size() - 2);
        for (int i = route.size() - 2; i >= 0 && previous.equals(end); i--) previous = route.get(i);
        float dx = end.x() - previous.x();
        float dy = end.y() - previous.y();
        float length = Math.max(0.001f, (float) Math.hypot(dx, dy));
        float unitX = dx / length;
        float unitY = dy / length;
        float baseX = end.x() - unitX * 6f;
        float baseY = end.y() - unitY * 6f;
        float normalX = -unitY * 3.5f;
        float normalY = unitX * 3.5f;
        GraphDraw.triangle(graphics, end.x(), end.y(), baseX + normalX, baseY + normalY,
            baseX - normalX, baseY - normalY, color);
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

    static List<Component> tooltip(ClientCraftingGraph graph, @Nullable ClientCraftingGraph.Node node) {
        if (node == null) return List.of();
        if (node.recursion() != null) return RecursiveCycleRenderer.tooltip(graph, node);
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER || node.kind() == ClientCraftingGraph.Kind.REFERENCE
                || node.material() != null) {
            return CompactTreeRenderer.tooltip(graph, node);
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP && node.cycle() != null) {
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
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER && node.cluster() != null) {
            var cluster = node.cluster();
            return List.of(recursionText("modules", cluster.clusterId()),
                Component.translatable("gui.neoecoae.crafting_graph.details.cluster_cycles", cluster.componentIds().size()),
                Component.translatable("gui.neoecoae.crafting_graph.details.cluster_flows", cluster.flows().size()),
                recursionText("open_modules"));
        }
        if (node.pattern() != null) {
            var pattern = node.pattern();
            return List.of(Component.literal(pattern.displayIdentity()),
                Component.translatable("gui.neoecoae.crafting_graph.details.firings",
                    HostText.ae2Amount(pattern.plannerFiringCount().toBigInteger())),
                Component.translatable("gui.neoecoae.crafting_graph.details.status", Component.translatable(
                    "gui.neoecoae.crafting_graph.status." + pattern.status().name().toLowerCase(java.util.Locale.ROOT))),
                Component.translatable("gui.neoecoae.crafting_graph.details.inputs", pattern.inputs().size()),
                Component.translatable("gui.neoecoae.crafting_graph.details.outputs", pattern.outputs().size()));
        }
        return List.of(Component.literal(CompactTreeRenderer.nodeTitle(node)));
    }

    static String formatMagnitude(AEKey key, PlannerAmount amount) {
        if (!amount.fitsLong()) {
            return BigNumberFormatter.format(amount.abs().toBigInteger(), key.getAmountPerUnit(), false);
        }
        long value = amount.longValueExact();
        long magnitude = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        return DisplayNumbers.grouped(key.formatAmount(magnitude, AmountFormat.SLOT));
    }
}
