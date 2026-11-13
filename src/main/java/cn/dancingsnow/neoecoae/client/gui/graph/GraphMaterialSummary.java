package cn.dancingsnow.neoecoae.client.gui.graph;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.CraftingGraphProgress;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Fixed-size material strips for the complete order, independent of the tree projection and camera. */
final class GraphMaterialSummary {
    static final int HEIGHT = 54;
    private static final int HEADER_HEIGHT = 16;
    private static final int TOGGLE_WIDTH = 14;
    private static final int CELL_WIDTH = 22;
    private final List<Section> sections;
    private List<Panel> panels = List.of();
    private int top;
    private int width;
    private int screenHeight;
    private boolean collapsed;
    private @Nullable Item hovered;
    private @Nullable Item tooltipItem;
    private @Nullable ClientCraftingGraph tooltipGraph;
    private @Nullable CraftingGraphProgress tooltipProgress;
    private @Nullable Language tooltipLanguage;
    private List<Component> tooltip = List.of();

    private record Item(ClientCraftingGraph.Node node, PlannerAmount amount, String compact) {}

    private static final class Section {
        final String label;
        final List<Item> items = new ArrayList<>();
        int offset;

        Section(String label) { this.label = label; }

        void add(CraftingGraphSnapshot.MaterialNode material, PlannerAmount amount) {
            if (amount.signum() <= 0) return;
            var node = new ClientCraftingGraph.Node(material.nodeId(), ClientCraftingGraph.Kind.MATERIAL,
                material.key().getDisplayName().getString(), material.key(), material, null, null);
            items.add(new Item(node, amount, GraphRenderer.formatMagnitude(material.key(), amount)));
        }

        Component title() { return Component.translatable("gui.neoecoae.crafting_graph." + label); }
    }

    private record Panel(Section section, int left, int right, int columns) {}

    GraphMaterialSummary(CraftingGraphSnapshot snapshot) {
        var stored = new Section("summary.from_storage");
        var crafting = new Section("card.to_craft");
        var outputs = new Section("summary.outputs");
        var missing = new Section("card.missing");
        for (var material : snapshot.nodes()) {
            var inventory = PlannerAmount.of(material.fromInventoryBigInteger());
            stored.add(material, inventory);
            crafting.add(material, PlannerAmount.of(material.toCraftBigInteger()));
            missing.add(material, PlannerAmount.of(material.missingBigInteger()));
            // Include returned seed stock as well as newly produced surplus, without counting internal turnover.
            var surplus = inventory.add(PlannerAmount.of(material.producedBigInteger()))
                .subtract(PlannerAmount.of(material.consumedBigInteger()));
            if (material.nodeId() == snapshot.rootNodeId()) {
                surplus = surplus.max(PlannerAmount.of(material.requestedBigInteger()));
            }
            outputs.add(material, surplus);
        }
        sections = List.of(stored, crafting, outputs, missing);
    }

    void resize(int screenWidth, int screenHeight) {
        width = screenWidth;
        this.screenHeight = screenHeight;
        top = Math.max(0, screenHeight - (collapsed ? HEADER_HEIGHT : HEIGHT));
        int available = Math.max(sections.size(), width - 8 - TOGGLE_WIDTH - (sections.size() - 1) * 4);
        int minimum = Math.min(72, available / sections.size());
        int extra = available - minimum * sections.size();
        int weight = sections.stream().mapToInt(section -> Math.max(1, Math.min(24, section.items.size()))).sum();
        var next = new ArrayList<Panel>();
        int left = 4;
        for (int i = 0; i < sections.size(); i++) {
            var section = sections.get(i);
            int panelWidth = i == sections.size() - 1 ? width - 4 - TOGGLE_WIDTH - left
                : minimum + extra * Math.max(1, Math.min(24, section.items.size())) / weight;
            int columns = Math.max(1, (panelWidth - 8) / CELL_WIDTH);
            section.offset = Math.min(section.offset, Math.max(0, section.items.size() - columns));
            next.add(new Panel(section, left, left + panelWidth, columns));
            left += panelWidth + 4;
        }
        panels = List.copyOf(next);
    }

    int top() { return top; }

    boolean contains(double x, double y) { return x >= 0 && x < width && y >= top && y < screenHeight; }

    void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        hovered = itemAt(mouseX, mouseY);
        var theme = GraphTheme.current();
        graphics.fill(0, top, width, screenHeight, theme.bar());
        graphics.fill(0, top, width, top + 1, theme.line());
        if (collapsed) {
            drawHeaders(graphics, font);

            return;
        }
        for (var panel : panels) for (int column = 0; column < panel.columns(); column++) {
            var item = item(panel, column);
            if (item != null) CompactTreeRenderer.drawBody(graphics, box(panel, column, item));
        }

        for (var panel : panels) {
            graphics.enableScissor(panel.left(), top + HEADER_HEIGHT, panel.right(), screenHeight);
            try {
                for (int column = 0; column < panel.columns(); column++) {
                    var item = item(panel, column);
                    if (item != null) CompactTreeRenderer.drawIcon(graphics, item.node(), box(panel, column, item));
                }

            } finally { graphics.disableScissor(); }
        }
        graphics.nextStratum();
        // Keep amount labels above the recorded item icons.

        drawHeaders(graphics, font);
        for (var panel : panels) {
            var section = panel.section();
            for (int column = 0; column < panel.columns(); column++) {
                var item = item(panel, column);
                if (item == null) continue;
                var box = box(panel, column, item);
                GraphDraw.frame(graphics, box.x(), box.y(), box.x() + box.width(), box.y() + box.height(),
                    item == hovered ? theme.accent() : theme.line(), 1);
                CompactTreeRenderer.drawAmount(graphics, font, item.compact(), box.x(), box.y() + 20,
                    box.x() + box.width(), box.y() + box.height(), theme.text());
            }
            if (section.items.isEmpty()) cn.dancingsnow.neoecoae.gui.common.GuiDraw.text(graphics, font, "-", panel.left() + 6, top + 26, theme.faint(), false);
        }

    }

    private void drawHeaders(GuiGraphicsExtractor graphics, Font font) {
        var theme = GraphTheme.current();
        for (var panel : panels) {
            var section = panel.section();
            boolean overflow = !collapsed && section.items.size() > panel.columns();
            String title = section.title().getString() + " (" + section.items.size() + ")";
            cn.dancingsnow.neoecoae.gui.common.GuiDraw.text(graphics, font, GraphDraw.fit(font, title, panel.right() - panel.left() - (overflow ? 25 : 4)),
                panel.left() + 2, top + 5, theme.muted(), false);
            if (overflow) {
                cn.dancingsnow.neoecoae.gui.common.GuiDraw.text(graphics, font, "\u2039", panel.right() - 19, top + 5,
                    section.offset > 0 ? theme.text() : theme.faint(), false);
                cn.dancingsnow.neoecoae.gui.common.GuiDraw.text(graphics, font, "\u203a", panel.right() - 9, top + 5,
                    section.offset + panel.columns() < section.items.size() ? theme.text() : theme.faint(), false);
            }
        }
        // Rotate the same chevron used by the horizontal paging controls.
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(width - 9, top + HEADER_HEIGHT / 2f);
        pose.rotate((float) Math.toRadians(collapsed ? -90 : 90));
        cn.dancingsnow.neoecoae.gui.common.GuiDraw.text(graphics, font, "\u203a", -font.width("\u203a") / 2, -font.lineHeight / 2, theme.text(), false);
        pose.popMatrix();
    }

    private @Nullable Item item(Panel panel, int column) {
        int index = panel.section().offset + column;
        return index < panel.section().items.size() ? panel.section().items.get(index) : null;
    }

    private GraphLayoutSnapshot.Box box(Panel panel, int column, Item item) {
        return new GraphLayoutSnapshot.Box(item.node().id(), panel.left() + 4 + column * CELL_WIDTH,
            top + 18, GraphLayoutSnapshot.COMPACT_MATERIAL_WIDTH, GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT);
    }

    private @Nullable Item itemAt(double x, double y) {
        if (collapsed || !contains(x, y)) return null;
        for (var panel : panels) {
            if (x < panel.left() || x >= panel.right()) continue;
            int column = (int) Math.floor((x - panel.left() - 4) / CELL_WIDTH);
            if (column < 0 || column >= panel.columns()) return null;
            var item = item(panel, column);
            return item != null && box(panel, column, item).contains((float) x, (float) y) ? item : null;
        }
        return null;
    }

    @Nullable CraftingGraphSnapshot.MaterialNode materialAt(double x, double y) {
        var item = itemAt(x, y);
        return item == null ? null : item.node().material();
    }

    boolean mouseClicked(double x, double y, Consumer<CraftingGraphSnapshot.MaterialNode> select) {
        if (!contains(x, y)) return false;
        if (x >= width - 16 && x < width - 2 && y >= top + 1 && y < top + HEADER_HEIGHT - 1) {
            collapsed = !collapsed;
            hovered = null;
            resize(width, screenHeight);
            return true;
        }
        if (collapsed) return false;
        for (var panel : panels) {
            if (x < panel.left() || x >= panel.right()) continue;
            if (y < top + HEADER_HEIGHT && x >= panel.right() - 22 && panel.section().items.size() > panel.columns()) {
                scroll(panel, x < panel.right() - 11 ? -panel.columns() : panel.columns());
                return true;
            }
        }
        var material = materialAt(x, y);
        if (material == null) return false;
        select.accept(material);
        return true;
    }

    boolean mouseScrolled(double x, double y, double delta) {
        if (!contains(x, y)) return false;
        if (collapsed) return true;
        for (var panel : panels) if (x >= panel.left() && x < panel.right()) {
            scroll(panel, (int) -Math.copySign(Math.ceil(Math.abs(delta)), delta));
            break;
        }
        return true;
    }

    private static void scroll(Panel panel, int delta) {
        var section = panel.section();
        section.offset = Math.max(0, Math.min(Math.max(0, section.items.size() - panel.columns()), section.offset + delta));
    }

    List<Component> tooltip(ClientCraftingGraph graph, @Nullable CraftingGraphProgress progress) {
        if (hovered == null) return List.of();
        if (tooltipItem != hovered || tooltipGraph != graph || tooltipProgress != progress || tooltipLanguage != Language.getInstance()) {
            tooltipItem = hovered;
            tooltipGraph = graph;
            tooltipProgress = progress;
            tooltipLanguage = Language.getInstance();
            var lines = new ArrayList<>(GraphMaterialCard.builder(graph, hovered.node(), false, progress).texts());
            for (var section : sections) if (section.items.stream().anyMatch(item -> item == hovered)) {
                lines.add(1, Component.translatable("gui.neoecoae.crafting_graph.summary.amount", section.title(),
                    RecursiveCycleRenderer.exact(hovered.node().key(), hovered.amount())));
                break;
            }
            tooltip = List.copyOf(lines);
        }
        return tooltip;
    }
}
