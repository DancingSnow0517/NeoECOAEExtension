package cn.dancingsnow.neoecoae.client.gui.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.CompactTreeNode;
import cn.dancingsnow.neoecoae.crafting.graph.CompactTreeProjection;
import cn.dancingsnow.neoecoae.crafting.graph.CycleCluster;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayout;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutCache;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Full-screen immediate-mode ECO crafting DAG and SCC viewer. */
public final class ECOCraftingGraphScreen extends Screen {
    private static final int INFO_HEIGHT = 18;
    private int toolbarHeight = 30;
    private Button depthButton;
    private Button missingButton;
    private int toolbarX, toolbarY;
    private int previousWidth, previousHeight;
    private String lastSearchQuery = "";
    private int lastSearchIndex = -1;

    private final Screen previous;
    private final CraftingGraphSnapshot snapshot;
    private final GraphLayoutCache layoutCache = new GraphLayoutCache(GraphLayout.Mode.COMPACT_TREE);
    private final GraphRenderer renderer = new GraphRenderer();
    private final GraphMaterialSummary materialSummary;
    private Component recursionOverview = Component.empty();
    private final Set<String> expandedTreePaths = new HashSet<>();
    private final Set<String> fullyExpandedTreePaths = new HashSet<>();
    private final Set<String> collapsedTreePaths = new HashSet<>();
    private ClientCraftingGraph baseGraph;
    private ClientCraftingGraph graph;
    private GraphLayoutSnapshot layout;
    private EditBox search;
    private final List<ClientCraftingGraph.Node> searchSuggestions = new ArrayList<>();
    private @Nullable Integer selectedId;
    private @Nullable ClientCraftingGraph.Node hovered;
    private @Nullable GraphMaterialCard materialDetails;
    private @Nullable GraphMaterialCard materialTooltip;
    private float cameraX;
    private float cameraY = 30;
    private float zoom = 1;
    private int depth = CompactTreeProjection.DEFAULT_DEPTH;
    private boolean missingOnly;
    private boolean panning;
    private long lastClickMillis;
    private String lastClickPath = "";
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
        this.materialSummary = new GraphMaterialSummary(snapshot);
        this.baseGraph = initialCycleComponentId == null ? ClientCraftingGraph.main(snapshot, false)
            : ClientCraftingGraph.cycle(snapshot, initialCycleComponentId, false, focusedMaterial);
        this.graph = baseGraph;
        this.selectedId = baseGraph.focusedMaterialId() != null ? baseGraph.focusedMaterialId() : baseGraph.rootId();
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        boolean focused = search != null && search.isFocused();
        int oldTop = graphContentTop();
        clearWidgets();
        panning = false;
        toolbarX = 5;
        toolbarY = 5;
        addButton(tr("toolbar.root"), this::goRoot);
        addButton(tr("toolbar.fit_all"), this::fitAll);
        depthButton = addButton(depthLabel(), this::cycleDepth);
        missingButton = addButton(tr(missingOnly ? "toolbar.missing_on" : "toolbar.missing_off"), this::toggleMissingOnly);
        addButton(tr(GraphTheme.current().light() ? "toolbar.theme_light" : "toolbar.theme_dark"), this::toggleTheme);
        int searchWidth = Math.min(180, Math.max(60, width - toolbarX - 8));
        if (toolbarX + searchWidth + 5 > width) {
            toolbarX = 5;
            toolbarY += 24;
            searchWidth = Math.min(180, Math.max(1, width - 10));
        }
        search = new EditBox(font, toolbarX + 1, toolbarY + 1, searchWidth, 18, tr("search"));
        search.setHint(tr("search_hint"));
        search.setValue(query);
        search.setResponder(ignored -> updateSearchSuggestions());
        addRenderableWidget(search);
        search.setFocused(focused);
        if (focused) setFocused(search);
        toolbarHeight = toolbarY + 25;
        materialSummary.resize(width, height);
        if (layout == null) rebuildGraph(true);
        else {
            cameraX += (width - previousWidth) / 2f;
            cameraY += graphContentTop() - oldTop
                + (height - previousHeight - graphContentTop() + oldTop) / 2f;
        }
        previousWidth = width;
        previousHeight = height;
        updateControls();
        updateSearchSuggestions();
    }

    private Button addButton(Component label, Runnable action) {
        int buttonWidth = Math.min(Math.max(1, width - 10), Math.max(48, font.width(label) + 16));
        if (toolbarX + buttonWidth + 5 > width && toolbarX > 5) {
            toolbarX = 5;
            toolbarY += 24;
        }
        Button button = addRenderableWidget(Button.builder(label, ignored -> action.run())
            .bounds(toolbarX, toolbarY, buttonWidth, 20).build());
        toolbarX += buttonWidth + 3;
        return button;
    }

    private void updateControls() {
        if (depthButton == null) return;
        boolean main = graph.view() == ClientCraftingGraph.View.MAIN;
        depthButton.active = main;
        missingButton.active = main;
        depthButton.setTooltip(main ? null : Tooltip.create(tr("toolbar.main_only")));
        missingButton.setTooltip(main ? null : Tooltip.create(tr("toolbar.main_only")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var theme = GraphTheme.current();
        graphics.fill(0, 0, width, height, theme.canvas());
        graphics.fill(0, 0, width, toolbarHeight, theme.bar());
        graphics.fill(0, toolbarHeight - 1, width, toolbarHeight, theme.line());
        int contentTop = graphContentTop();
        boolean overGraph = overGraph(mouseX, mouseY);
        int graphMouseX = overGraph ? mouseX : -1_000_000;
        int graphMouseY = overGraph ? mouseY : -1_000_000;
        var frame = renderer.render(graphics, graph, layout, cameraX, cameraY, zoom,
            new GraphRenderer.Viewport(0, contentTop, width, Math.max(contentTop, graphContentBottom())),
            graphMouseX, graphMouseY, selectedId);
        hovered = frame.hovered();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, infoTop(), width, materialSummary.top(), theme.bar());
        graphics.fill(0, infoTop(), width, infoTop() + 1, theme.line());
        drawBreadcrumb(graphics);
        drawStats(graphics);
        materialSummary.render(graphics, font, mouseX, mouseY);
        if (graph.isRecursiveCycle()) RecursiveCycleRenderer.text(graphics, font, recursionOverview.getString(),
            7, toolbarHeight + 7, width - 14, theme.text(), 0.85f);
        if (selectedId != null) {
            if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) drawCycleDetails(graphics);
            else if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) drawClusterDetails(graphics);
            else drawDetailsPanel(graphics);
        }
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        drawSearchSuggestions(graphics, mouseX, mouseY);
        graphics.flush();
        graphics.pose().popPose();
        if (!frame.tooltip().isEmpty() && overGraph) {
            if (graph.view() == ClientCraftingGraph.View.MAIN && GraphMaterialCard.supports(hovered)) {
                if (materialTooltip == null || !materialTooltip.matches(graph, hovered, font)) {
                    materialTooltip = new GraphMaterialCard(graph, hovered, font, false);
                }
                materialTooltip.renderTooltip(graphics, mouseX, mouseY, width, height);
            } else graphics.renderComponentTooltip(font, frame.tooltip(), mouseX, mouseY);
        }
        var summaryTooltip = materialSummary.tooltip(graph);
        if (!summaryTooltip.isEmpty()) graphics.renderComponentTooltip(font, summaryTooltip, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This screen paints its background before the graph. Screen.render() must not blur the graph afterward.
    }

    private void drawBreadcrumb(GuiGraphics graphics) {
        graphics.drawString(font, breadcrumbText(), 7, infoTop() + 5, GraphTheme.current().text(), false);
    }

    private String breadcrumbText() {
        String breadcrumb = switch (graph.view()) {
            case MAIN -> tr("breadcrumb.plan").getString();
            case CYCLE_FOCUS -> tr("breadcrumb.cycle", graph.focusedCycleId()).getString();
            case CYCLE_CLUSTER -> tr("breadcrumb.cluster", graph.focusedCluster().clusterId()).getString();
        };
        if (graph.view() != ClientCraftingGraph.View.MAIN) breadcrumb = "\u2039 " + tr("toolbar.back").getString() + "  " + breadcrumb;
        return fit(breadcrumb, Math.max(1, width / 3 - 12));
    }

    private void drawStats(GuiGraphics graphics) {
        String zoomLabel = tr("zoom", Math.round(zoom * 100)).getString();
        String hint = tr("controls").getString();
        int right = width - font.width(zoomLabel) - 7;
        graphics.drawString(font, zoomLabel, Math.max(7, right), infoTop() + 5, GraphTheme.current().text(), false);
        int hintRight = right - 10;
        String shownHint = fit(hint, Math.max(0, hintRight - font.width(breadcrumbText()) - 19));
        graphics.drawString(font, shownHint, hintRight - font.width(shownHint), infoTop() + 5, GraphTheme.current().faint(), false);
    }

    private void drawCycleDetails(GuiGraphics graphics) {
        if (graph.view() != ClientCraftingGraph.View.CYCLE_FOCUS || graph.isRecursiveCycle()) return;
        var cycle = snapshot.cycleGroups().stream()
            .filter(value -> value.componentId() == graph.focusedCycleId()).findFirst().orElse(null);
        if (cycle == null) return;
        int panelWidth = Math.min(250, Math.max(1, width - 16));
        int left = width - panelWidth - 8;
        int top = graphContentTop() + 8;
        int lines = 8;
        panel(graphics, left, top, width - 8, top + 10 + lines * 12);
        graphics.drawString(font, tr("details.cycle_title", cycle.componentId()), left + 7, top + 6,
            GraphTheme.current().accent(), false);
        int y = top + 19;
        y = textLine(graphics, left, panelWidth, y, "status", statusText(cycle.status()));
        y = textLine(graphics, left, panelWidth, y, "seed", summarizeAmounts(cycle.requiredSeed()));
        y = textLine(graphics, left, panelWidth, y, "external", summarizeAmounts(cycle.externalInputs()));
        y = textLine(graphics, left, panelWidth, y, "target", summarizeAmounts(cycle.requiredOutputs()));
        y = line(graphics, left, y, "recursion_layers", cycle.recursion().layers().size());
        String hint = !cycle.recursion().complete() ? "unknown_hint"
            : cycle.status().equals("SOLVED") || cycle.status().equals("NOT_REQUIRED") ? "direction" : "conditional";
        graphics.drawWordWrap(font, GraphRenderer.recursionText(hint), left + 7, y, panelWidth - 14, GraphTheme.current().muted());
    }

    private static void panel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        var theme = GraphTheme.current();
        graphics.fill(left, top, right, bottom, theme.line());
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, GraphTheme.alpha(theme.panel(), 0xf2));
    }

    private int line(GuiGraphics graphics, int left, int y, String keySuffix, int value) {
        graphics.drawString(font, tr("details." + keySuffix, value), left + 7, y, GraphTheme.current().muted(), false);
        return y + 12;
    }

    private void drawClusterDetails(GuiGraphics graphics) {
        CycleCluster cluster = graph.focusedCluster();
        if (cluster == null) return;
        int panelWidth = Math.min(250, Math.max(1, width - 16));
        int left = width - panelWidth - 8;
        int top = graphContentTop() + 8;
        panel(graphics, left, top, width - 8, top + 70);
        graphics.drawString(font, tr("details.cluster_title", cluster.clusterId()), left + 7, top + 6,
            GraphTheme.current().accent(), false);
        int y = top + 20;
        y = line(graphics, left, y, "cluster_cycles", cluster.componentIds().size());
        y = line(graphics, left, y, "cluster_flows", cluster.flows().size());
        graphics.drawString(font, tr("details.cluster_hint"), left + 7, y, GraphTheme.current().muted(), false);
    }

    private int textLine(GuiGraphics graphics, int left, int panelWidth, int y, String keySuffix, Component value) {
        Component label = tr("details." + keySuffix + "_label");
        int valueX = left + 7 + font.width(label);
        graphics.drawString(font, label, left + 7, y, GraphTheme.current().muted(), false);
        graphics.drawString(font, fit(value.getString(), panelWidth - 14 - font.width(label)), valueX, y,
            GraphTheme.current().accent(), false);
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
                .append(HostText.ae2Amount(value.plannerAmount().toBigInteger()));
        }
        if (values.size() > shown) text.append(tr("summary.more", values.size() - shown).getString());
        return Component.literal(text.toString());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (materialSummary.mouseScrolled(mouseX, mouseY, scrollY != 0 ? scrollY : scrollX)) return true;
        if (!overGraph(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (Screen.hasShiftDown()) {
            cameraX -= (float) scrollX * 28;
            cameraY -= (float) scrollY * 28;
            return true;
        }
        float oldZoom = zoom;
        zoom = GraphViewTransform.scrollZoom(zoom, scrollY);
        float worldX = ((float) mouseX - cameraX) / oldZoom;
        float worldY = ((float) mouseY - cameraY) / oldZoom;
        cameraX = (float) mouseX - worldX * zoom;
        cameraY = (float) mouseY - worldY * zoom;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && selectSearchSuggestion(mouseX, mouseY)) return true;
        if (materialSummary.contains(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && !materialSummary.mouseClicked(mouseX, mouseY, this::focusSummaryMaterial)) clearSelection();
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseY >= infoTop() && mouseY < materialSummary.top()
                && graph.view() != ClientCraftingGraph.View.MAIN && mouseX < font.width(breadcrumbText()) + 14) {
            goRoot();
            return true;
        }
        if (!overGraph(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (mouseY < graphContentTop() || mouseY >= infoTop())) clearSelection();
            return false;
        }
        ClientCraftingGraph.Node hit = hit(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hit != null && graph.view() == ClientCraftingGraph.View.MAIN) {
            selectedId = hit.id();
            if (hit.kind() == ClientCraftingGraph.Kind.FOLDER) expandAllSelected();
            else collapseSelected();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit != null) {
            selectedId = hit.id();
            long now = Util.getMillis();
            var occurrence = graph.compactTreeNode(hit.id());
            String clickPath = occurrence == null ? "node/" + hit.id() : occurrence.path();
            boolean doubleClick = lastClickPath.equals(clickPath) && now - lastClickMillis < 350;
            // A folder becomes a material occurrence on expansion. Match its path across projection rebuilds.
            boolean sameFolderOccurrence = lastClickWasFolder && doubleClick;
            if (hit.recursion() != null && hit.recursion().role()
                    == cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode.Role.OUTPUT) {
                goRoot();
            } else if (hit.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP || hit.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER
                    || hit.kind() == ClientCraftingGraph.Kind.REFERENCE && (hit.cycle() != null || hit.cluster() != null)) {
                activate(hit);
            } else if (graph.isCompactTree() && (hit.kind() == ClientCraftingGraph.Kind.FOLDER || sameFolderOccurrence)) {
                if (doubleClick && sameFolderOccurrence) expandAllSelected();
                else expandSelected();
            } else if (doubleClick) activate(hit);
            lastClickWasFolder = hit.kind() == ClientCraftingGraph.Kind.FOLDER;
            lastClickPath = clickPath;
            lastClickMillis = now;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) clearSelection();
            panning = true;
            return true;
        }
        return false;
    }

    private void clearSelection() {
        selectedId = null;
        materialDetails = null;
        materialTooltip = null;
        hovered = null;
        lastClickPath = "";
        lastClickWasFolder = false;
    }

    private void focusSummaryMaterial(CraftingGraphSnapshot.MaterialNode material) {
        var match = baseGraph.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.MATERIAL && material.key().equals(node.key()))
            .findFirst().orElse(null);
        if (match == null) {
            var cycle = snapshot.cycleGroups().stream()
                .filter(group -> group.memberNodeIds().contains(material.nodeId())).findFirst().orElse(null);
            if (cycle != null) {
                parentCluster = graph.focusedCluster();
                baseGraph = ClientCraftingGraph.cycle(snapshot, cycle.componentId(), false, material.key());
                selectedId = baseGraph.focusedMaterialId() != null ? baseGraph.focusedMaterialId() : baseGraph.rootId();
                rebuildGraph(true);
                return;
            }
            parentCluster = null;
            baseGraph = ClientCraftingGraph.main(snapshot, false);
            rebuildGraph(false);
            match = baseGraph.nodes().get(material.nodeId());
        }
        if (match != null) focusSearchMatch(match);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning && overGraph(mouseX, mouseY)) {
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
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && search.isFocused()) {
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
        if (graph.view() != ClientCraftingGraph.View.MAIN) goRoot();
        else Minecraft.getInstance().setScreen(previous);
    }

    @Override
    public void removed() {
        renderer.close();
        super.removed();
    }

    private void activate(ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER
                || node.kind() == ClientCraftingGraph.Kind.REFERENCE && node.cluster() != null) {
            parentCluster = null;
            baseGraph = ClientCraftingGraph.cluster(snapshot, node.cluster(), false);
            selectedId = baseGraph.rootId();
            rebuildGraph(true);
        } else if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP
                || node.kind() == ClientCraftingGraph.Kind.REFERENCE && node.cycle() != null) {
            parentCluster = graph.focusedCluster();
            baseGraph = ClientCraftingGraph.cycle(snapshot, node.cycle().componentId(), false);
            selectedId = baseGraph.rootId();
            rebuildGraph(true);
        } else if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            expandAllSelected();
        } else if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER
                && (node.kind() == ClientCraftingGraph.Kind.MATERIAL
                    || node.kind() == ClientCraftingGraph.Kind.PATTERN)) {
            Integer componentId = componentForClusterNode(node);
            if (componentId != null) {
                parentCluster = graph.focusedCluster();
                baseGraph = ClientCraftingGraph.cycle(snapshot, componentId, false,
                    node.kind() == ClientCraftingGraph.Kind.MATERIAL ? node.key() : null);
                selectedId = baseGraph.rootId();
                rebuildGraph(true);
            }
        } else if (node.kind() == ClientCraftingGraph.Kind.MATERIAL) {
            selectedId = node.id();
            if (graph.isRecursiveCycle()) center(node.id());
            else rebuildGraph(true);
        }
    }

    private void goRoot() {
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && parentCluster != null) {
            baseGraph = ClientCraftingGraph.cluster(snapshot, parentCluster, false);
            parentCluster = null;
        } else if (graph.view() != ClientCraftingGraph.View.MAIN) {
            baseGraph = ClientCraftingGraph.main(snapshot, false);
            parentCluster = null;
        }
        selectedId = baseGraph.rootId();
        expandedTreePaths.clear();
        fullyExpandedTreePaths.clear();
        collapsedTreePaths.clear();
        rebuildGraph(true);
    }

    private void expandSelected() {
        if (selectedId == null || !graph.isCompactTree()) return;
        CompactTreeNode treeNode = graph.compactTreeNode(selectedId);
        if (treeNode == null) return;
        collapsedTreePaths.remove(treeNode.path());
        fullyExpandedTreePaths.remove(treeNode.path());
        expandedTreePaths.add(treeNode.path());
        rebuildGraph(false);
    }

    private void collapseSelected() {
        if (selectedId == null || !graph.isCompactTree()) return;
        CompactTreeNode treeNode = graph.compactTreeNode(selectedId);
        if (treeNode == null) return;
        collapsedTreePaths.add(treeNode.path());
        expandedTreePaths.remove(treeNode.path());
        fullyExpandedTreePaths.remove(treeNode.path());
        rebuildGraph(false);
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

    private void cycleDepth() {
        depth = depth == 99 ? 2 : depth == 2 ? 4 : depth == 4 ? 8 : 99;
        rebuildGraph(false);
        refreshControls();
    }

    private void toggleMissingOnly() {
        missingOnly = !missingOnly;
        expandedTreePaths.clear();
        fullyExpandedTreePaths.clear();
        collapsedTreePaths.clear();
        rebuildGraph(true);
        refreshControls();
    }

    private void toggleTheme() {
        GraphTheme.toggle();
        materialDetails = null;
        materialTooltip = null;
        refreshControls();
    }

    private void refreshControls() {
        // Reflow translated labels without rebuilding the graph or resetting its camera.
        init();
    }

    private void rebuildGraph(boolean fit) {
        var oldBox = layout == null || selectedId == null ? null : layout.box(selectedId);
        var oldOccurrence = selectedId == null ? null : graph.compactTreeNode(selectedId);
        if (baseGraph.view() == ClientCraftingGraph.View.CYCLE_FOCUS
                || baseGraph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) {
            // A cycle focus is a complete SCC explanation. Depth/collapse controls belong to MAIN projection and
            // must never silently remove a boundary or an internal pattern from this view.
            graph = baseGraph;
        } else {
            ClientCraftingGraph projectedBase = missingOnly ? baseGraph.missingPaths() : baseGraph;
            graph = CompactTreeProjection.project(projectedBase, missingOnly ? 99 : depth, expandedTreePaths,
                fullyExpandedTreePaths, collapsedTreePaths);
        }
        layoutCache.invalidate();
        materialDetails = null;
        materialTooltip = null;
        layout = layoutCache.get(graph);
        if (!fit && oldOccurrence != null && graph.isCompactTree()) {
            // Projected numeric IDs change as earlier branches expand. Selection follows the occurrence path.
            selectedId = graph.compactTreeNodes().values().stream()
                .filter(node -> oldOccurrence.path().equals(node.path())).map(CompactTreeNode::id)
                .findFirst().orElse(graph.rootId());
        }
        if (selectedId != null && !graph.nodes().containsKey(selectedId)) selectedId = graph.rootId();
        var newBox = selectedId == null ? null : layout.box(selectedId);
        if (!fit && oldBox != null && newBox != null) {
            cameraX += (oldBox.centerX() - newBox.centerX()) * zoom;
            cameraY += (oldBox.centerY() - newBox.centerY()) * zoom;
        }
        if (graph.isRecursiveCycle()) {
            var cycle = graph.nodes().values().stream().map(ClientCraftingGraph.Node::cycle)
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            long retained = graph.nodes().values().stream().filter(node -> node.recursion().role()
                == cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode.Role.RETAINED
                && node.recursion().amount().signum() > 0).count();
            recursionOverview = GraphRenderer.recursionText("overview", cycle.componentId(),
                cycle.recursion().layers().size(), cycle.requiredSeed().size(), cycle.externalInputs().size(),
                cycle.recursion().complete() ? retained : "—");
        }
        updateControls();
        updateSearchSuggestions();
        if (fit) {
            fitAll();
            if (graph.isRecursiveCycle()) focusRecursionStart();
        }
    }

    /** Long recursion strips open at their left edge with readable nodes and the next steps in view. */
    private void focusRecursionStart() {
        if (zoom >= 0.85f) return;
        var bounds = layout.bounds();
        float availableHeight = Math.max(1, graphContentBottom() - graphContentTop() - 24);
        zoom = Math.max(GraphViewTransform.MIN_ZOOM, Math.min(0.85f, availableHeight / Math.max(1, bounds.height())));
        cameraX = 16 - bounds.left() * zoom;
        cameraY = graphContentTop() + 12 + (availableHeight - bounds.height() * zoom) / 2 - bounds.top() * zoom;
    }

    private void fitAll() {
        if (layout == null || layout.boxes().isEmpty()) return;
        var bounds = layout.bounds();
        float detailsReserve = detailsReserve();
        float availableWidth = Math.max(1, width - 40 - detailsReserve);
        int contentTop = graphContentTop();
        float availableHeight = Math.max(1, graphContentBottom() - contentTop - 24);
        zoom = GraphViewTransform.fitZoom(bounds, availableWidth, availableHeight);
        cameraX = 20 - bounds.left() * zoom + (availableWidth - bounds.width() * zoom) / 2;
        cameraY = contentTop + 12 - bounds.top() * zoom
            + (availableHeight - bounds.height() * zoom) / 2;
    }

    private void findNext() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return;
        var matches = baseGraph.nodes().values().stream().filter(node -> matchesItemSearch(node, query))
            .sorted(java.util.Comparator.comparingInt(ClientCraftingGraph.Node::id)).toList();
        if (matches.isEmpty()) return;
        if (!query.equals(lastSearchQuery)) lastSearchIndex = -1;
        lastSearchQuery = query;
        lastSearchIndex = (lastSearchIndex + 1) % matches.size();
        focusSearchMatch(matches.get(lastSearchIndex));
    }

    private void center(int id) {
        var box = layout.box(id);
        if (box == null) return;
        float detailsReserve = detailsReserve();
        float usableWidth = Math.max(1, width - detailsReserve);
        int contentTop = graphContentTop();
        float usableHeight = Math.max(1, graphContentBottom() - contentTop);
        cameraX = usableWidth / 2.0f - box.centerX() * zoom;
        cameraY = contentTop + usableHeight / 2.0f - box.centerY() * zoom;
    }

    private int graphContentTop() {
        return toolbarHeight + (graph.isRecursiveCycle() ? 24 : 0);
    }

    private int infoTop() { return Math.max(0, materialSummary.top() - INFO_HEIGHT); }

    private int graphContentBottom() { return Math.max(graphContentTop(), infoTop()); }

    private int detailsReserve() {
        if (graph.isRecursiveCycle() || selectedId == null) return 0;
        var selected = graph.nodes().get(selectedId);
        if (selected == null) return 0;
        return Math.min(Math.max(0, width - 40), graph.view() == ClientCraftingGraph.View.MAIN ? 250 : 258);
    }

    private boolean overGraph(double mouseX, double mouseY) {
        if (mouseY < graphContentTop() || mouseY >= graphContentBottom() || mouseX < 0 || mouseX >= width) return false;
        if (search != null && search.isFocused() && !searchSuggestions.isEmpty()
                && mouseX >= search.getX() && mouseX < search.getX() + search.getWidth()
                && mouseY >= search.getY() + search.getHeight()
                && mouseY < search.getY() + search.getHeight() + searchSuggestions.size() * 18 + 2) return false;
        if (graph.isRecursiveCycle() || selectedId == null) return true;
        int top = graphContentTop() + 8;
        if (graph.view() != ClientCraftingGraph.View.MAIN) return !(mouseX >= Math.max(8, width - 258)
            && mouseX < width - 8 && mouseY >= top && mouseY < top
                + (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER ? 70 : 106));
        var selected = selectedId == null ? null : graph.nodes().get(selectedId);
        if (selected == null) return true;
        if (GraphMaterialCard.supports(selected)) {
            if (materialDetails == null || !materialDetails.matches(graph, selected, font))
                materialDetails = new GraphMaterialCard(graph, selected, font, true);
            return !materialDetails.containsDetails(mouseX, mouseY, width, graphContentBottom(), top);
        }
        return !(mouseX >= Math.max(8, width - 228) && mouseX < width - 8 && mouseY >= top && mouseY < top + 120);
    }

    private @Nullable ClientCraftingGraph.Node hit(double mouseX, double mouseY) {
        if (!overGraph(mouseX, mouseY)) return null;
        float worldX = ((float) mouseX - cameraX) / zoom;
        float worldY = ((float) mouseY - cameraY) / zoom;
        return layout.query(worldX, worldY, worldX, worldY, 0).stream()
            .filter(box -> box.contains(worldX, worldY)).findFirst().map(box -> graph.nodes().get(box.nodeId()))
            .orElse(null);
    }

    /**
     * Returns the material key rendered under the given screen coordinates.
     *
     * <p>The graph is drawn as an immediate-mode canvas without inventory slots. The optional EMI
     * integration uses this hit test to make material nodes available to recipe and usage lookups.</p>
     */
    public @Nullable AEKey getECOStackAt(int mouseX, int mouseY) {
        var summaryMaterial = materialSummary.materialAt(mouseX, mouseY);
        if (summaryMaterial != null) return summaryMaterial.key();
        if (mouseY < graphContentTop() || layout == null) return null;
        ClientCraftingGraph.Node node = hit(mouseX, mouseY);
        return node != null && node.material() != null
            && (node.kind() == ClientCraftingGraph.Kind.MATERIAL || node.kind() == ClientCraftingGraph.Kind.REFERENCE)
            ? node.key() : null;
    }

    private void drawDetailsPanel(GuiGraphics graphics) {
        if (selectedId == null) return;
        var node = graph.nodes().get(selectedId);
        if (node == null) return;
        if (GraphMaterialCard.supports(node)) {
            if (materialDetails == null || !materialDetails.matches(graph, node, font)) {
                materialDetails = new GraphMaterialCard(graph, node, font, true);
            }
            materialDetails.renderDetails(graphics, width, graphContentBottom(), graphContentTop() + 8);
            return;
        }
        int panelWidth = Math.min(220, Math.max(1, width - 16));
        int left = width - panelWidth - 8;
        int top = graphContentTop() + 8;
        int lines = 9;
        panel(graphics, left, top, width - 8, top + 12 + lines * 12);
        graphics.drawString(font, node.kind() == ClientCraftingGraph.Kind.FOLDER
                ? Component.translatable("gui.neoecoae.crafting_graph.details.folder")
                : Component.literal(fit(CompactTreeRenderer.nodeTitle(node), panelWidth - 14)), left + 7,
            top + 6, node.kind() == ClientCraftingGraph.Kind.FOLDER ? GraphTheme.current().muted() : GraphTheme.current().accent(), false);
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
        } else if (node.pattern() != null) {
            var pattern = node.pattern();
            y = detailTextLine(graphics, left, y, "status", statusText(pattern.status().name()));
            y = detailTextLine(graphics, left, y, "firings",
                Component.literal(HostText.ae2Amount(pattern.plannerFiringCount().toBigInteger())));
            y = detailLine(graphics, left, y, "inputs", pattern.inputs().size());
            detailLine(graphics, left, y, "outputs", pattern.outputs().size());
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
            left + 7, y, GraphTheme.current().muted(), false);
        return y + 12;
    }

    private int detailTextLine(GuiGraphics graphics, int left, int y, String keySuffix, Component value) {
        graphics.drawString(font,
            Component.translatable("gui.neoecoae.crafting_graph.details." + keySuffix, value),
            left + 7, y, GraphTheme.current().muted(), false);
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
    private void updateSearchSuggestions() {
        searchSuggestions.clear();
        if (search == null) return;
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return;
        baseGraph.nodes().values().stream()
            .filter(node -> matchesItemSearch(node, query))
            .sorted((a, b) -> a.label().compareToIgnoreCase(b.label()))
            .limit(6).forEach(searchSuggestions::add);
    }

    /** The graph search locates material occurrences; recipe/pattern nodes are not item search results. */
    static boolean matchesItemSearch(ClientCraftingGraph.Node node, String query) {
        if (node.kind() != ClientCraftingGraph.Kind.MATERIAL || node.material() == null || node.key() == null) {
            return false;
        }
        return node.label().toLowerCase(Locale.ROOT).contains(query)
            || node.key().toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private void drawSearchSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        if (search == null || searchSuggestions.isEmpty() || !search.isFocused()) return;
        int left = search.getX();
        int top = search.getY() + search.getHeight();
        int right = left + search.getWidth();
        var theme = GraphTheme.current();
        panel(graphics, left, top, right, top + searchSuggestions.size() * 18 + 2);
        for (int i = 0; i < searchSuggestions.size(); i++) {
            ClientCraftingGraph.Node node = searchSuggestions.get(i);
            int rowTop = top + 1 + i * 18;
            if (mouseX >= left && mouseX < right && mouseY >= rowTop && mouseY < rowTop + 18)
                graphics.fill(left + 1, rowTop, right - 1, rowTop + 18, theme.hover());
            graphics.drawString(font, fit(node.label(), search.getWidth() - 8), left + 4, rowTop + 5, theme.text(), false);
        }
    }

    private boolean selectSearchSuggestion(double mouseX, double mouseY) {
        if (search == null || searchSuggestions.isEmpty() || !search.isFocused()) return false;
        int top = search.getY() + search.getHeight();
        if (mouseX < search.getX() || mouseX >= search.getX() + search.getWidth()
                || mouseY < top || mouseY >= top + searchSuggestions.size() * 18 + 2) return false;
        int index = (int) ((mouseY - top - 1) / 18);
        if (index < 0 || index >= searchSuggestions.size()) return false;
        ClientCraftingGraph.Node match = searchSuggestions.get(index);
        search.setValue(match.label());
        focusSearchMatch(match);
        searchSuggestions.clear();
        return true;
    }

    private void focusSearchMatch(ClientCraftingGraph.Node match) {
        if (missingOnly && baseGraph.view() == ClientCraftingGraph.View.MAIN
                && !baseGraph.missingPaths().nodes().containsKey(match.id())) {
            missingOnly = false;
            refreshControls();
        }
        if (baseGraph.view() == ClientCraftingGraph.View.MAIN) {
            List<String> paths = CompactTreeProjection.pathTo(missingOnly ? baseGraph.missingPaths() : baseGraph, match.id());
            expandedTreePaths.addAll(paths);
            collapsedTreePaths.removeAll(paths);
            rebuildGraph(false);
            graph.compactTreeNodes().values().stream()
                .filter(node -> node.sourceId() == match.id() && !node.folder())
                .sorted(java.util.Comparator.comparing(CompactTreeNode::reference))
                .findFirst().ifPresent(node -> selectedId = node.id());
        } else {
            selectedId = match.id();
            if (!graph.nodes().containsKey(match.id())) rebuildGraph(false);
        }
        if (selectedId != null && layout.box(selectedId) != null) {
            center(selectedId);
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
}
