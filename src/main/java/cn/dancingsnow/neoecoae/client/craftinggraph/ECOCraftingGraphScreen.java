package cn.dancingsnow.neoecoae.client.craftinggraph;

import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
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

    public ECOCraftingGraphScreen(Screen previous, CraftingGraphSnapshot snapshot) {
        super(Component.translatable("gui.neoecoae.crafting_graph.title"));
        this.previous = previous;
        this.snapshot = snapshot;
        this.baseGraph = ClientCraftingGraph.main(snapshot, false);
        this.graph = baseGraph;
        this.layoutMode = GraphLayout.Mode.fromSystemProperty();
        this.layoutCache = new GraphLayoutCache(layoutMode);
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = 5;
        x = addButton(x, 48, "Fit All", this::fitAll);
        x = addButton(x, 46, "Root", this::goRoot);
        x = addButton(x, 58, "Expand", this::expandSelected);
        x = addButton(x, 58, "Collapse", this::collapseSelected);
        x = addButton(x, 50, "All", this::expandAllSelected);
        x = addButton(x, 58, depthLabel(), this::cycleDepth);
        x = addButton(x, 42, "D4", this::expandToFour);
        x = addButton(x, 54, "Fold4", this::foldToFour);
        x = addButton(x, 76, viewLabel(), this::cycleLayoutMode);
        x = addButton(x, 70, advanced ? "Debug: ON" : "Debug: OFF", this::toggleAdvanced);
        search = new EditBox(font, x + 4, 6, Math.min(180, Math.max(80, width - x - 250)), 18,
            Component.translatable("gui.neoecoae.crafting_graph.search"));
        search.setHint(Component.literal("Search AEKey / item"));
        search.setResponder(ignored -> {});
        addRenderableWidget(search);
        rebuildGraph(false);
    }

    private int addButton(int x, int width, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run())
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
        else if (graph.isCompactTree()) drawDetailsPanel(graphics);
        if (!frame.tooltip().isEmpty() && mouseY > TOOLBAR_HEIGHT) {
            graphics.renderComponentTooltip(font, frame.tooltip(), mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This screen paints its background before the graph. Screen.render() must not blur the graph afterward.
    }

    private void drawBreadcrumb(GuiGraphics graphics) {
        String breadcrumb = graph.view() == ClientCraftingGraph.View.MAIN ? "ECO Plan"
            : "ECO Plan  >  Cycle #" + graph.focusedCycleId();
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
        int panelWidth = 210;
        int left = width - panelWidth - 8;
        int top = 38;
        int lines = 5 + (!cycle.requiredSeed().isEmpty() ? 1 : 0) + (!cycle.externalInputs().isEmpty() ? 1 : 0)
            + (!cycle.patternTimes().isEmpty() ? 1 : 0) + (!cycle.executionWitness().isEmpty() ? 1 : 0);
        graphics.fill(left, top, width - 8, top + 10 + lines * 12, 0xdd171b20);
        graphics.drawString(font, "Cycle #" + cycle.componentId() + " · " + cycle.status(), left + 7, top + 6,
            0xffe2b766, false);
        int y = top + 19;
        y = line(graphics, left, y, "members", cycle.memberNodeIds().size());
        y = line(graphics, left, y, "required outputs", cycle.requiredOutputs().size());
        y = line(graphics, left, y, "external inputs", cycle.externalInputs().size());
        if (!cycle.requiredSeed().isEmpty()) y = line(graphics, left, y, "required seed", cycle.requiredSeed().size());
        if (!cycle.patternTimes().isEmpty()) y = line(graphics, left, y, "pattern times", cycle.patternTimes().size());
        if (!cycle.executionWitness().isEmpty()) line(graphics, left, y, "execution witness", cycle.executionWitness().size());
    }

    private int line(GuiGraphics graphics, int left, int y, String name, int value) {
        graphics.drawString(font, name + ": " + value, left + 7, y, 0xffaeb8c2, false);
        return y + 12;
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
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) {
            baseGraph = ClientCraftingGraph.cycle(snapshot, node.cycle().componentId(), advanced);
            selectedId = baseGraph.rootId();
            collapsed.clear();
            rebuildGraph(true);
        } else if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            expandAllSelected();
        } else if (node.kind() == ClientCraftingGraph.Kind.MATERIAL) {
            selectedId = node.id();
            rebuildGraph(true);
        }
    }

    private void goRoot() {
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
            baseGraph = ClientCraftingGraph.main(snapshot, advanced);
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
        baseGraph = graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS
            ? ClientCraftingGraph.cycle(snapshot, graph.focusedCycleId(), advanced)
            : ClientCraftingGraph.main(snapshot, advanced);
        refreshControls();
    }

    private void refreshControls() {
        String query = search == null ? "" : search.getValue();
        init();
        search.setValue(query);
    }

    private void rebuildGraph(boolean fit) {
        if (layoutMode == GraphLayout.Mode.COMPACT_TREE && baseGraph.view() == ClientCraftingGraph.View.MAIN) {
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
        float detailsReserve = graph.isCompactTree() ? 236 : 0;
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
        graphics.drawString(font, node.kind() == ClientCraftingGraph.Kind.FOLDER ? "Folder" : node.label(), left + 7,
            top + 6, node.kind() == ClientCraftingGraph.Kind.FOLDER ? 0xff9fb3c2 : 0xffe2b766, false);
        int y = top + 20;
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            var folder = graph.compactTreeNode(node.id());
            if (folder != null) {
                y = detailLine(graphics, left, y, "hidden nodes", folder.hiddenNodeCount());
                y = detailLine(graphics, left, y, "hidden layers", folder.hiddenDepth());
                y = detailLine(graphics, left, y, "depth", folder.depth());
                y = detailTextLine(graphics, left, y, "missing", folder.containsMissing() ? "yes" : "no");
                y = detailTextLine(graphics, left, y, "unsupported", folder.containsUnsupported() ? "yes" : "no");
                y = detailTextLine(graphics, left, y, "cycle", folder.containsCycle() ? "yes" : "no");
                detailTextLine(graphics, left, y, "action", "expand");
            }
        } else if (node.material() != null) {
            var material = node.material();
            y = detailLine(graphics, left, y, "requested", material.requested());
            y = detailLine(graphics, left, y, "inventory", material.fromInventory());
            y = detailLine(graphics, left, y, "to craft", material.toCraft());
            y = detailLine(graphics, left, y, "missing", material.missing());
            y = detailTextLine(graphics, left, y, "status", material.status().name());
            detailLine(graphics, left, y, "source patterns", baseGraph.source().patterns().size());
        } else if (node.cycle() != null) {
            var cycle = node.cycle();
            y = detailLine(graphics, left, y, "cycle", cycle.componentId());
            y = detailLine(graphics, left, y, "members", cycle.memberNodeIds().size());
            detailTextLine(graphics, left, y, "status", cycle.status());
        }
    }

    private int detailLine(GuiGraphics graphics, int left, int y, String name, long value) {
        graphics.drawString(font, name + ": " + value, left + 7, y, 0xffaeb8c2, false);
        return y + 12;
    }

    private int detailTextLine(GuiGraphics graphics, int left, int y, String name, String value) {
        graphics.drawString(font, name + ": " + value, left + 7, y, 0xffaeb8c2, false);
        return y + 12;
    }

    private String depthLabel() { return depth == 99 ? "Depth: All" : "Depth: " + depth; }
    private String viewLabel() { return "View: " + (layoutMode == GraphLayout.Mode.COMPACT_TREE ? "Tree" : layoutMode.name()); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
