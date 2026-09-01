package cn.dancingsnow.neoecoae.client.craftinggraph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Full-screen immediate-mode ECO crafting DAG and SCC viewer. */
public final class ECOCraftingGraphScreen extends Screen {
    private static final float MIN_ZOOM = 0.15f;
    private static final float MAX_ZOOM = 2.5f;
    private static final int TOOLBAR_HEIGHT = 30;

    private final Screen previous;
    private final CraftingGraphSnapshot snapshot;
    private final @Nullable Integer initialCycleComponentId;
    private final @Nullable AEKey initialFocusedMaterial;
    private GraphLayoutCache layoutCache;
    private final GraphRenderer renderer = new GraphRenderer();
    private final GraphProfiler profiler = new GraphProfiler();
    private final Set<Integer> collapsed = new HashSet<>();
    private final Set<String> expandedTreePaths = new HashSet<>();
    private final Set<String> fullyExpandedTreePaths = new HashSet<>();
    private final Set<String> collapsedTreePaths = new HashSet<>();
    private ClientCraftingGraph baseGraph;
    private ClientCraftingGraph graph;
    private GraphLayoutSnapshot layout;
    private EditBox search;
    private @Nullable Integer selectedId;
    private @Nullable ClientCraftingGraph.Node hovered;
    private float cameraX;
    private float cameraY = TOOLBAR_HEIGHT;
    private float zoom = 1;
    private int depth = CompactTreeProjection.DEFAULT_DEPTH;
    private GraphLayout.Mode layoutMode;
    private boolean advanced;
    private boolean panning;
    private long lastClickMillis;
    private int lastClickNode = Integer.MAX_VALUE;
    private boolean lastClickWasFolder;
    private @Nullable CycleCluster parentCluster;

    public ECOCraftingGraphScreen(Screen previous, CraftingGraphSnapshot snapshot) {
        this(previous, snapshot, null, null);
    }

    public ECOCraftingGraphScreen(Screen previous, CraftingGraphSnapshot snapshot,
            @Nullable Integer initialCycleComponentId, @Nullable AEKey focusedMaterial) {
        super(Component.translatable("gui.neoecoae.crafting_graph.title"));
        this.previous = previous;
        this.snapshot = snapshot;
        this.initialCycleComponentId = initialCycleComponentId;
        this.initialFocusedMaterial = focusedMaterial;
        this.baseGraph = initialCycleComponentId == null ? ClientCraftingGraph.main(snapshot, false)
            : ClientCraftingGraph.cycle(snapshot, initialCycleComponentId, false, focusedMaterial);
        this.graph = baseGraph;
        this.selectedId = baseGraph.focusedMaterialId() != null ? baseGraph.focusedMaterialId() : baseGraph.rootId();
        this.layoutMode = GraphLayout.Mode.fromSystemProperty();
        this.layoutCache = new GraphLayoutCache(layoutMode);
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = 5;
        x = addButton(x, 48, tr("toolbar.fit_all"), this::fitAll);
        x = addButton(x, 46, tr("toolbar.root"), this::goRoot);
        x = addButton(x, 58, tr("toolbar.expand"), this::expandSelected);
        x = addButton(x, 58, tr("toolbar.collapse"), this::collapseSelected);
        x = addButton(x, 50, tr("toolbar.expand_all"), this::expandAllSelected);
        x = addButton(x, 58, depthLabel(), this::cycleDepth);
        x = addButton(x, 42, tr("toolbar.depth_four"), this::expandToFour);
        x = addButton(x, 54, tr("toolbar.fold_four"), this::foldToFour);
        x = addButton(x, 76, viewLabel(), this::cycleLayoutMode);
        x = addButton(x, 70, tr(advanced ? "toolbar.debug_on" : "toolbar.debug_off"), this::toggleAdvanced);
        search = new EditBox(font, x + 4, 6, Math.min(180, Math.max(80, width - x - 250)), 18,
            Component.translatable("gui.neoecoae.crafting_graph.search"));
        search.setHint(tr("search_hint"));
        search.setResponder(ignored -> {});
        addRenderableWidget(search);
        rebuildGraph(initialCycleComponentId != null);
    }

    private int addButton(int x, int width, Component label, Runnable action) {
        addRenderableWidget(Button.builder(label, ignored -> action.run())
            .bounds(x, 5, width, 20).build());
        return x + width + 3;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff0e1115);
        graphics.fill(0, 0, width, TOOLBAR_HEIGHT, 0xff242a31);
        graphics.enableScissor(0, TOOLBAR_HEIGHT, width, height);
        var frame = renderer.render(graphics, graph, layout, cameraX, cameraY, zoom, width, height, mouseX, mouseY,
            selectedId, profiler);
        hovered = frame.hovered();
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
        drawBreadcrumb(graphics);
        drawStats(graphics);
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) drawCycleDetails(graphics);
        else if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) drawClusterDetails(graphics);
        else if (graph.isCompactTree()) drawDetailsPanel(graphics);
        if (!frame.tooltip().isEmpty() && mouseY > TOOLBAR_HEIGHT) {
            graphics.renderComponentTooltip(font, frame.tooltip(), mouseX, mouseY);
            var tooltipCycle = cycleForTooltipNode(hovered);
            if (tooltipCycle != null) drawCycleTooltipRing(graphics, tooltipCycle, mouseX, mouseY);
        }
    }

    @Nullable
    private CraftingGraphSnapshot.CycleGroup cycleForTooltipNode(@Nullable ClientCraftingGraph.Node node) {
        if (node == null) return null;
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) return node.cycle();
        if (node.kind() != ClientCraftingGraph.Kind.MATERIAL || node.material() == null) return null;
        int sourceId = node.material().nodeId();
        return snapshot.cycleGroups().stream()
            .filter(cycle -> cycle.memberNodeIds().contains(sourceId))
            .findFirst().orElse(null);
    }

    /** Draws a compact cycle summary over the tooltip without changing vanilla tooltip layout. */
    private void drawCycleTooltipRing(GuiGraphics graphics, CraftingGraphSnapshot.CycleGroup cycle,
            int mouseX, int mouseY) {
        int radius = 18;
        int centerX = Math.min(width - radius - 4, mouseX + 112);
        int centerY = Math.max(radius + TOOLBAR_HEIGHT + 2, mouseY - 24);
        int backgroundLeft = centerX - radius - 5;
        int backgroundTop = centerY - radius - 5;
        int backgroundRight = centerX + radius + 6;
        int backgroundBottom = centerY + radius + 6;
        graphics.fill(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, 0xee171b20);

        int activeColor = cycleStatusColor(cycle.status());
        int segments = 12;
        int activeSegments = Math.max(1, Math.min(segments, cycle.memberNodeIds().size()));
        for (int i = 0; i < segments; i++) {
            double angle = -Math.PI / 2 + i * (Math.PI * 2 / segments);
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius);
            int color = i < activeSegments ? activeColor : 0xff56616c;
            graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
        }
        graphics.drawCenteredString(font, Integer.toString(cycle.memberNodeIds().size()), centerX,
            centerY - font.lineHeight / 2, 0xffe8edf2);
    }

    private static int cycleStatusColor(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "SOLVED", "PLANNED", "READY" -> 0xff68b58a;
            case "UNSUPPORTED", "DISABLED" -> 0xffa36c9a;
            case "FAILED", "MISSING" -> 0xffcf5e5e;
            default -> 0xffd59b45;
        };
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This screen paints its background before the graph. Screen.render() must not blur the graph afterward.
    }

    private void drawBreadcrumb(GuiGraphics graphics) {
        String breadcrumb = switch (graph.view()) {
            case MAIN -> tr("breadcrumb.plan").getString();
            case CYCLE_FOCUS -> tr("breadcrumb.cycle", graph.focusedCycleId()).getString();
            case CYCLE_CLUSTER -> tr("breadcrumb.cluster", graph.focusedCluster().clusterId()).getString();
        };
        graphics.drawString(font, breadcrumb, 7, height - 14, 0xffc6d0da, false);
    }

    private void drawStats(GuiGraphics graphics) {
        String stats = String.format(Locale.ROOT,
            "nodes %d/%d  edges %d/%d  zoom %.0f%%  layout %.2f ms  index %.2f ms  render %.2f ms  v%d",
            profiler.visibleNodes(), snapshot.summary().materialNodes() + snapshot.summary().patternNodes(),
            profiler.visibleEdges(), snapshot.summary().edges(), zoom * 100,
            layout.layoutNanos() / 1_000_000.0, layout.spatialIndexNanos() / 1_000_000.0,
            profiler.renderNanos() / 1_000_000.0, layout.version());
        graphics.drawString(font, stats, Math.max(7, width - font.width(stats) - 7), height - 14, 0xff8e9aa6, false);
    }

    private void drawCycleDetails(GuiGraphics graphics) {
        if (graph.view() != ClientCraftingGraph.View.CYCLE_FOCUS) return;
        var cycle = snapshot.cycleGroups().stream()
            .filter(value -> value.componentId() == graph.focusedCycleId()).findFirst().orElse(null);
        if (cycle == null) return;
        int panelWidth = 250;
        int left = width - panelWidth - 8;
        int top = 38;
        int lines = 7;
        graphics.fill(left, top, width - 8, top + 10 + lines * 12, 0xdd171b20);
        graphics.drawString(font, tr("details.cycle_title", cycle.componentId()), left + 7, top + 6,
            0xffe2b766, false);
        int y = top + 19;
        y = textLine(graphics, left, panelWidth, y, "status", statusText(cycle.status()));
        y = textLine(graphics, left, panelWidth, y, "seed", summarizeAmounts(cycle.requiredSeed()));
        y = textLine(graphics, left, panelWidth, y, "external", summarizeAmounts(cycle.externalInputs()));
        y = textLine(graphics, left, panelWidth, y, "execute", summarizePatterns(cycle));
        y = line(graphics, left, y, "witness_steps", cycle.executionWitness().size());
        y = line(graphics, left, y, "members", cycle.memberNodeIds().size());
        line(graphics, left, y, "required_outputs", cycle.requiredOutputs().size());
    }

    private int line(GuiGraphics graphics, int left, int y, String keySuffix, int value) {
        graphics.drawString(font, tr("details." + keySuffix, value), left + 7, y, 0xffaeb8c2, false);
        return y + 12;
    }

    private void drawClusterDetails(GuiGraphics graphics) {
        CycleCluster cluster = graph.focusedCluster();
        if (cluster == null) return;
        int panelWidth = 250;
        int left = width - panelWidth - 8;
        int top = 38;
        graphics.fill(left, top, width - 8, top + 70, 0xdd171b20);
        graphics.drawString(font, tr("details.cluster_title", cluster.clusterId()), left + 7, top + 6,
            0xffe2b766, false);
        int y = top + 20;
        y = line(graphics, left, y, "cluster_cycles", cluster.componentIds().size());
        y = line(graphics, left, y, "cluster_flows", cluster.flows().size());
        graphics.drawString(font, tr("details.cluster_hint"), left + 7, y, 0xffaeb8c2, false);
    }

    private int textLine(GuiGraphics graphics, int left, int panelWidth, int y, String keySuffix, Component value) {
        Component label = tr("details." + keySuffix + "_label");
        int valueX = left + 7 + font.width(label);
        graphics.drawString(font, label, left + 7, y, 0xffaeb8c2, false);
        graphics.drawString(font, fit(value.getString(), panelWidth - 14 - font.width(label)), valueX, y,
            0xffe2b766, false);
        return y + 12;
    }

    private Component summarizeAmounts(List<CraftingGraphSnapshot.KeyAmount> values) {
        if (values.isEmpty()) return Component.literal("-");
        StringBuilder text = new StringBuilder();
        int shown = Math.min(2, values.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) text.append(", ");
            var value = values.get(i);
            text.append(value.key().getDisplayName().getString()).append(" ×")
                .append(HostText.ae2Amount(value.amount()));
        }
        if (values.size() > shown) text.append(tr("summary.more", values.size() - shown).getString());
        return Component.literal(text.toString());
    }

    private Component summarizePatterns(CraftingGraphSnapshot.CycleGroup cycle) {
        if (cycle.patternTimes().isEmpty()) return Component.literal("-");
        StringBuilder text = new StringBuilder();
        int shown = Math.min(2, cycle.patternTimes().size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) text.append(", ");
            var value = cycle.patternTimes().get(i);
            var pattern = snapshot.patterns().stream()
                .filter(candidate -> candidate.patternNodeId() == value.patternNodeId()).findFirst().orElse(null);
            text.append(pattern == null ? "Pattern #" + value.patternNodeId() : pattern.displayIdentity())
                .append(" ×").append(HostText.ae2Amount(value.amount()));
        }
        if (cycle.patternTimes().size() > shown) text.append(" +").append(cycle.patternTimes().size() - shown)
            .append(tr("summary.more_suffix").getString());
        return Component.literal(text.toString());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY <= TOOLBAR_HEIGHT) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (graph.isCompactTree() && !Screen.hasControlDown()) {
            cameraX -= (float) scrollX * 28;
            cameraY -= (float) scrollY * 28;
            return true;
        }
        float oldZoom = zoom;
        zoom = clamp((float) (zoom * Math.pow(1.12, scrollY)), MIN_ZOOM, MAX_ZOOM);
        float worldX = ((float) mouseX - cameraX) / oldZoom;
        float worldY = ((float) mouseY - cameraY) / oldZoom;
        cameraX = (float) mouseX - worldX * zoom;
        cameraY = (float) mouseY - worldY * zoom;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseY >= height - 20
                && graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
            goRoot();
            return true;
        }
        if (mouseY <= TOOLBAR_HEIGHT) return false;
        ClientCraftingGraph.Node hit = hit(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hit != null && graph.isCompactTree()) {
            selectedId = hit.id();
            collapseSelected();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit != null) {
            selectedId = hit.id();
            long now = Util.getMillis();
            boolean doubleClick = lastClickNode == hit.id() && now - lastClickMillis < 350;
            // A one-level expansion replaces a folder with its source node at the same path/id. Keep the
            // previous folder gesture only while the second click is on that same projected occurrence.
            boolean sameFolderOccurrence = lastClickWasFolder && lastClickNode == hit.id();
            if (graph.isCompactTree() && (hit.kind() == ClientCraftingGraph.Kind.FOLDER || sameFolderOccurrence)) {
                if (doubleClick && sameFolderOccurrence) expandAllSelected();
                else expandSelected();
            } else if (doubleClick) activate(hit);
            lastClickWasFolder = hit.kind() == ClientCraftingGraph.Kind.FOLDER;
            lastClickNode = hit.id();
            lastClickMillis = now;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            panning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning) {
            cameraX += (float) dragX;
            cameraY += (float) dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && search.isFocused()) {
            findNext();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && !search.isFocused()) {
            fitAll();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME && !search.isFocused()) {
            goRoot();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(previous);
    }

    private void activate(ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER) {
            parentCluster = null;
            baseGraph = ClientCraftingGraph.cluster(snapshot, node.cluster(), advanced);
            selectedId = baseGraph.rootId();
            collapsed.clear();
            rebuildGraph(true);
        } else if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) {
            parentCluster = null;
            baseGraph = ClientCraftingGraph.cycle(snapshot, node.cycle().componentId(), advanced);
            selectedId = baseGraph.rootId();
            collapsed.clear();
            rebuildGraph(true);
        } else if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            expandAllSelected();
        } else if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER
                && (node.kind() == ClientCraftingGraph.Kind.MATERIAL
                    || node.kind() == ClientCraftingGraph.Kind.PATTERN)) {
            Integer componentId = componentForClusterNode(node);
            if (componentId != null) {
                parentCluster = graph.focusedCluster();
                baseGraph = ClientCraftingGraph.cycle(snapshot, componentId, advanced,
                    node.kind() == ClientCraftingGraph.Kind.MATERIAL ? node.key() : null);
                selectedId = baseGraph.rootId();
                rebuildGraph(true);
            }
        } else if (node.kind() == ClientCraftingGraph.Kind.MATERIAL) {
            selectedId = node.id();
            rebuildGraph(true);
        }
    }

    private void goRoot() {
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && parentCluster != null) {
            baseGraph = ClientCraftingGraph.cluster(snapshot, parentCluster, advanced);
            parentCluster = null;
        } else if (graph.view() != ClientCraftingGraph.View.MAIN) {
            baseGraph = ClientCraftingGraph.main(snapshot, advanced);
            parentCluster = null;
        }
        selectedId = baseGraph.rootId();
        collapsed.clear();
        expandedTreePaths.clear();
        fullyExpandedTreePaths.clear();
        collapsedTreePaths.clear();
        rebuildGraph(true);
    }

    private void expandSelected() {
        if (selectedId == null) return;
        if (graph.isCompactTree()) {
            CompactTreeNode treeNode = graph.compactTreeNode(selectedId);
            if (treeNode == null) return;
            collapsedTreePaths.remove(treeNode.path());
            fullyExpandedTreePaths.remove(treeNode.path());
            expandedTreePaths.add(treeNode.path());
            rebuildGraph(false);
        } else {
            collapsed.remove(selectedId);
            rebuildGraph(false);
        }
    }

    private void collapseSelected() {
        if (selectedId == null) return;
        if (graph.isCompactTree()) {
            CompactTreeNode treeNode = graph.compactTreeNode(selectedId);
            if (treeNode == null) return;
            collapsedTreePaths.add(treeNode.path());
            expandedTreePaths.remove(treeNode.path());
            fullyExpandedTreePaths.remove(treeNode.path());
            rebuildGraph(false);
        } else {
            if (!collapsed.add(selectedId)) collapsed.remove(selectedId);
            rebuildGraph(false);
        }
    }

    private void expandAllSelected() {
        if (selectedId == null || !graph.isCompactTree()) return;
        CompactTreeNode treeNode = graph.compactTreeNode(selectedId);
        if (treeNode == null) return;
        collapsedTreePaths.remove(treeNode.path());
        expandedTreePaths.remove(treeNode.path());
        fullyExpandedTreePaths.add(treeNode.path());
        rebuildGraph(false);
    }

    private void toggleCollapse() { collapseSelected(); }

    private void expandToFour() {
        depth = CompactTreeProjection.DEFAULT_DEPTH;
        expandedTreePaths.clear();
        fullyExpandedTreePaths.clear();
        collapsedTreePaths.clear();
        collapsed.clear();
        rebuildGraph(true);
        refreshControls();
    }

    private void foldToFour() {
        depth = CompactTreeProjection.DEFAULT_DEPTH;
        expandedTreePaths.clear();
        fullyExpandedTreePaths.clear();
        collapsedTreePaths.clear();
        collapsed.clear();
        rebuildGraph(true);
        refreshControls();
    }

    private void cycleDepth() {
        depth = depth == 99 ? 2 : depth == 2 ? 4 : depth == 4 ? 8 : 99;
        refreshControls();
    }

    private void cycleLayoutMode() {
        layoutMode = switch (layoutMode) {
            case COMPACT_TREE -> GraphLayout.Mode.LEGACY;
            case LEGACY -> GraphLayout.Mode.COMPACT_TREE;
        };
        layoutCache = new GraphLayoutCache(layoutMode);
        rebuildGraph(true);
        refreshControls();
    }

    private void toggleAdvanced() {
        advanced = !advanced;
        AEKey focusedMaterial = graph.focusedMaterialId() == null ? null
            : graph.nodes().get(graph.focusedMaterialId()) == null ? null
                : graph.nodes().get(graph.focusedMaterialId()).key();
        baseGraph = switch (graph.view()) {
            case CYCLE_FOCUS -> ClientCraftingGraph.cycle(snapshot, graph.focusedCycleId(), advanced, focusedMaterial);
            case CYCLE_CLUSTER -> ClientCraftingGraph.cluster(snapshot, graph.focusedCluster(), advanced);
            case MAIN -> ClientCraftingGraph.main(snapshot, advanced);
        };
        refreshControls();
    }

    private void refreshControls() {
        String query = search == null ? "" : search.getValue();
        init();
        search.setValue(query);
    }

    private void rebuildGraph(boolean fit) {
        if (baseGraph.view() == ClientCraftingGraph.View.CYCLE_FOCUS
                || baseGraph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) {
            // A cycle focus is a complete SCC explanation. Depth/collapse controls belong to MAIN projection and
            // must never silently remove a boundary or an internal pattern from this view.
            graph = baseGraph;
        } else if (layoutMode == GraphLayout.Mode.COMPACT_TREE && baseGraph.view() == ClientCraftingGraph.View.MAIN) {
            graph = CompactTreeProjection.project(baseGraph, depth, expandedTreePaths, fullyExpandedTreePaths,
                collapsedTreePaths);
        } else {
            int focus = selectedId != null && baseGraph.nodes().containsKey(selectedId) ? selectedId : baseGraph.rootId();
            graph = baseGraph.limited(focus, depth, collapsed);
        }
        layoutCache.invalidate();
        layout = layoutCache.get(graph);
        if (fit) fitAll();
    }

    private void fitAll() {
        if (layout == null || layout.boxes().isEmpty()) return;
        var bounds = layout.bounds();
        float detailsReserve = graph.isCompactTree() || graph.view() != ClientCraftingGraph.View.MAIN ? 258 : 0;
        float availableWidth = Math.max(1, width - 40 - detailsReserve);
        float availableHeight = Math.max(1, height - TOOLBAR_HEIGHT - 42);
        zoom = clamp(Math.min(availableWidth / bounds.width(), availableHeight / bounds.height()), MIN_ZOOM, 1.5f);
        cameraX = 20 - bounds.left() * zoom + (availableWidth - bounds.width() * zoom) / 2;
        cameraY = TOOLBAR_HEIGHT + 12 - bounds.top() * zoom
            + (availableHeight - bounds.height() * zoom) / 2;
    }

    private void findNext() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return;
        var match = graph.nodes().values().stream()
            .filter(node -> node.label().toLowerCase(Locale.ROOT).contains(query)
                || node.key() != null && node.key().toString().toLowerCase(Locale.ROOT).contains(query))
            .findFirst();
        if (match.isPresent()) {
            selectedId = match.get().id();
            center(selectedId);
            return;
        }
        if (layoutMode == GraphLayout.Mode.COMPACT_TREE) {
            var sourceMatch = baseGraph.nodes().values().stream()
                .filter(node -> node.label().toLowerCase(Locale.ROOT).contains(query)
                    || node.key() != null && node.key().toString().toLowerCase(Locale.ROOT).contains(query))
                .findFirst();
            if (sourceMatch.isPresent()) {
                List<String> paths = CompactTreeProjection.pathTo(baseGraph, sourceMatch.get().id());
                if (!paths.isEmpty()) {
                    expandedTreePaths.addAll(paths);
                    collapsedTreePaths.removeAll(paths);
                    rebuildGraph(false);
                    graph.compactTreeNodes().values().stream()
                        .filter(node -> node.sourceId() == sourceMatch.get().id()).findFirst()
                        .ifPresent(node -> { selectedId = node.id(); center(node.id()); });
                }
            }
        }
    }

    private void center(int id) {
        var box = layout.box(id);
        if (box == null) return;
        cameraX = width / 2.0f - box.centerX() * zoom;
        cameraY = (height + TOOLBAR_HEIGHT) / 2.0f - box.centerY() * zoom;
    }

    private @Nullable ClientCraftingGraph.Node hit(double mouseX, double mouseY) {
        float worldX = ((float) mouseX - cameraX) / zoom;
        float worldY = ((float) mouseY - cameraY) / zoom;
        return layout.query(worldX, worldY, worldX, worldY, 0).stream()
            .filter(box -> box.contains(worldX, worldY)).findFirst().map(box -> graph.nodes().get(box.nodeId()))
            .orElse(null);
    }

    private void drawDetailsPanel(GuiGraphics graphics) {
        if (selectedId == null) return;
        var node = graph.nodes().get(selectedId);
        if (node == null) return;
        int panelWidth = 220;
        int left = width - panelWidth - 8;
        int top = 38;
        int lines = 7;
        graphics.fill(left, top, width - 8, top + 12 + lines * 12, 0xdd171b20);
        graphics.drawString(font, node.kind() == ClientCraftingGraph.Kind.FOLDER
                ? Component.translatable("gui.neoecoae.crafting_graph.details.folder")
                : Component.literal(node.label()), left + 7,
            top + 6, node.kind() == ClientCraftingGraph.Kind.FOLDER ? 0xff9fb3c2 : 0xffe2b766, false);
        int y = top + 20;
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            var folder = graph.compactTreeNode(node.id());
            if (folder != null) {
                y = detailLine(graphics, left, y, "hidden_nodes", folder.hiddenNodeCount());
                y = detailLine(graphics, left, y, "hidden_layers", folder.hiddenDepth());
                y = detailLine(graphics, left, y, "depth", folder.depth());
                y = detailTextLine(graphics, left, y, "missing", yesNo(folder.containsMissing()));
                y = detailTextLine(graphics, left, y, "unsupported", yesNo(folder.containsUnsupported()));
                y = detailTextLine(graphics, left, y, "cycle", yesNo(folder.containsCycle()));
                detailTextLine(graphics, left, y, "action",
                    Component.translatable("gui.neoecoae.crafting_graph.action.expand"));
            }
        } else if (node.material() != null) {
            var material = node.material();
            y = detailTextLine(graphics, left, y, "requested", compact(material.exactRequested()));
            y = detailTextLine(graphics, left, y, "inventory", compact(material.exactFromInventory()));
            y = detailTextLine(graphics, left, y, "to_craft", compact(material.exactToCraft()));
            y = detailTextLine(graphics, left, y, "missing", compact(material.exactMissing()));
            y = detailTextLine(graphics, left, y, "status", statusText(material.status().name()));
            detailLine(graphics, left, y, "source_patterns", baseGraph.source().patterns().size());
        } else if (node.cluster() != null) {
            var cluster = node.cluster();
            y = detailLine(graphics, left, y, "cluster_cycles", cluster.componentIds().size());
            detailLine(graphics, left, y, "cluster_flows", cluster.flows().size());
        } else if (node.cycle() != null) {
            var cycle = node.cycle();
            y = detailLine(graphics, left, y, "cycle", cycle.componentId());
            y = detailLine(graphics, left, y, "members", cycle.memberNodeIds().size());
            detailTextLine(graphics, left, y, "status", statusText(cycle.status()));
        }
    }

    private int detailLine(GuiGraphics graphics, int left, int y, String keySuffix, long value) {
        graphics.drawString(font,
            Component.translatable("gui.neoecoae.crafting_graph.details." + keySuffix, value),
            left + 7, y, 0xffaeb8c2, false);
        return y + 12;
    }

    private int detailTextLine(GuiGraphics graphics, int left, int y, String keySuffix, Component value) {
        graphics.drawString(font,
            Component.translatable("gui.neoecoae.crafting_graph.details." + keySuffix, value),
            left + 7, y, 0xffaeb8c2, false);
        return y + 12;
    }

    private static Component yesNo(boolean value) {
        return Component.translatable("gui.neoecoae.common." + (value ? "yes" : "no"));
    }

    private static Component statusText(String status) {
        return Component.translatable("gui.neoecoae.crafting_graph.status."
            + status.toLowerCase(Locale.ROOT));
    }

    private Component depthLabel() { return tr(depth == 99 ? "toolbar.depth_all" : "toolbar.depth", depth); }
    private Component viewLabel() { return tr(layoutMode == GraphLayout.Mode.COMPACT_TREE
        ? "toolbar.view_tree" : "toolbar.view_graph"); }
    private static Component compact(String exact) {
        try {
            return Component.literal(HostText.ae2Amount(new BigInteger(exact)));
        } catch (RuntimeException ignored) {
            return Component.literal(exact);
        }
    }

    private @Nullable Integer componentForClusterNode(ClientCraftingGraph.Node node) {
        if (node.pattern() != null) return node.pattern().componentId();
        if (node.material() == null) return null;
        int materialId = node.material().nodeId();
        return snapshot.cycleGroups().stream().filter(cycle -> cycle.memberNodeIds().contains(materialId))
            .map(CraftingGraphSnapshot.CycleGroup::componentId).findFirst().orElse(null);
    }
    private String fit(String value, int maxWidth) {
        if (maxWidth <= 0 || font.width(value) <= maxWidth) return maxWidth <= 0 ? "" : value;
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }
    private static Component tr(String suffix, Object... args) {
        return Component.translatable("gui.neoecoae.crafting_graph." + suffix, args);
    }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
