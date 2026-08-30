package cn.dancingsnow.neoecoae.client.craftinggraph;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AmountFormat;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Minecraft-style compact tree renderer. */
public final class CompactTreeRenderer {
    private static final int TEXT = 0xffe8edf2;
    private static final int MUTED = 0xffaab3bd;

    public GraphRenderer.Frame render(GuiGraphics graphics, ClientCraftingGraph graph,
            GraphLayoutSnapshot layout, float cameraX, float cameraY, float zoom, int screenWidth, int screenHeight,
            int mouseX, int mouseY, @Nullable Integer selectedId, GraphProfiler profiler) {
        long started = System.nanoTime();
        GraphRenderer.VisiblePlan visible = GraphRenderer.collectVisible(layout, cameraX, cameraY, zoom,
            screenWidth, screenHeight);
        Set<Integer> neighborhood = selectedId == null ? Set.of() : graph.neighborhood(selectedId);
        for (var link : visible.edges()) {
            drawLink(graphics, layout, link, selectedId, neighborhood, cameraX, cameraY, zoom);
        }
        ClientCraftingGraph.Node hovered = null;
        float worldMouseX = (mouseX - cameraX) / zoom;
        float worldMouseY = (mouseY - cameraY) / zoom;
        for (var box : visible.nodes()) {
            var node = graph.nodes().get(box.nodeId());
            if (node == null) continue;
            boolean selected = selectedId != null && selectedId == node.id();
            drawNode(graphics, graph, node, box, selected, neighborhood.contains(node.id()), cameraX, cameraY, zoom);
            if (box.contains(worldMouseX, worldMouseY)) hovered = node;
        }
        drawRowFrames(graphics, layout, cameraX, cameraY, zoom);
        profiler.update(visible.nodes().size(), visible.edges().size(), System.nanoTime() - started);
        return new GraphRenderer.Frame(hovered, tooltip(graph, hovered));
    }

    private static void drawRowFrames(GuiGraphics graphics, GraphLayoutSnapshot layout,
            float cameraX, float cameraY, float zoom) {
        Map<Float, List<GraphLayoutSnapshot.Box>> rows = new HashMap<>();
        for (var box : layout.boxes().values()) {
            rows.computeIfAbsent(box.y(), ignored -> new ArrayList<>()).add(box);
        }
        for (var row : rows.values()) {
            row.sort(Comparator.comparingDouble(GraphLayoutSnapshot.Box::x));
            float left = 0;
            float top = 0;
            float right = 0;
            float bottom = 0;
            boolean active = false;
            for (var box : row) {
                if (!active || box.x() > right + 0.001f) {
                    if (active) drawRowFrame(graphics, left, top, right, bottom, cameraX, cameraY, zoom);
                    left = box.x();
                    top = box.y();
                    right = box.x() + box.width();
                    bottom = box.y() + box.height();
                    active = true;
                } else {
                    right = Math.max(right, box.x() + box.width());
                    bottom = Math.max(bottom, box.y() + box.height());
                }
            }
            if (active) drawRowFrame(graphics, left, top, right, bottom, cameraX, cameraY, zoom);
        }
    }

    private static void drawRowFrame(GuiGraphics graphics, float left, float top, float right, float bottom,
            float cameraX, float cameraY, float zoom) {
        int x = screen(cameraX, left, zoom);
        int y = screen(cameraY, top, zoom);
        int r = screen(cameraX, right, zoom);
        int b = screen(cameraY, bottom, zoom);
        if (r <= x || b <= y) return;
        int color = 0xff8293a4;
        graphics.fill(x, y, r, Math.min(b, y + 1), color);
        graphics.fill(x, Math.max(y, b - 1), r, b, color);
        graphics.fill(x, y, Math.min(r, x + 1), b, color);
        graphics.fill(Math.max(x, r - 1), y, r, b, color);
    }

    private static void drawLink(GuiGraphics graphics, GraphLayoutSnapshot layout, ClientCraftingGraph.Link link,
            @Nullable Integer selected, Set<Integer> neighborhood, float cameraX, float cameraY, float zoom) {
        boolean highlighted = selected != null && (link.fromId() == selected || link.toId() == selected)
            && (neighborhood.contains(link.fromId()) || neighborhood.contains(link.toId()));
        int color = highlighted ? 0xfff6c453 : link.selected() ? 0xff8293a4 : 0xff66505a;
        List<GraphLayoutSnapshot.Point> route = layout.edgePoints(link);
        if (route.isEmpty()) {
            var from = layout.box(link.fromId());
            var to = layout.box(link.toId());
            if (from == null || to == null) return;
            route = verticalRoute(from, to);
        }
        drawRoute(graphics, route, color, cameraX, cameraY, zoom);
        var end = route.getLast();
        int endX = screen(cameraX, end.x(), zoom);
        int endY = screen(cameraY, end.y(), zoom);
        graphics.fill(endX - 3, endY - 2, endX + 1, endY + 3, color);
    }

    private static void drawRoute(GuiGraphics graphics, List<GraphLayoutSnapshot.Point> route, int color,
            float cameraX, float cameraY, float zoom) {
        for (int i = 1; i < route.size(); i++) {
            var from = route.get(i - 1);
            var to = route.get(i);
            int x1 = screen(cameraX, from.x(), zoom);
            int y1 = screen(cameraY, from.y(), zoom);
            int x2 = screen(cameraX, to.x(), zoom);
            int y2 = screen(cameraY, to.y(), zoom);
            if (x1 == x2) graphics.vLine(x1, Math.min(y1, y2), Math.max(y1, y2), color);
            else if (y1 == y2) graphics.hLine(Math.min(x1, x2), Math.max(x1, x2), y1, color);
            else {
                int middle = (x1 + x2) / 2;
                graphics.hLine(Math.min(x1, middle), Math.max(x1, middle), y1, color);
                graphics.vLine(middle, Math.min(y1, y2), Math.max(y1, y2), color);
                graphics.hLine(Math.min(middle, x2), Math.max(middle, x2), y2, color);
            }
        }
    }

    private static List<GraphLayoutSnapshot.Point> verticalRoute(GraphLayoutSnapshot.Box from,
            GraphLayoutSnapshot.Box to) {
        float startX = from.centerX();
        float startY = from.y() + from.height();
        float endX = to.centerX();
        float endY = to.y();
        float middle = (startY + endY) / 2;
        return List.of(new GraphLayoutSnapshot.Point(startX, startY), new GraphLayoutSnapshot.Point(startX, middle),
            new GraphLayoutSnapshot.Point(endX, middle), new GraphLayoutSnapshot.Point(endX, endY));
    }

    private static void drawNode(GuiGraphics graphics, ClientCraftingGraph graph, ClientCraftingGraph.Node node,
            GraphLayoutSnapshot.Box box, boolean selected, boolean adjacent, float cameraX, float cameraY,
            float zoom) {
        int x = screen(cameraX, box.x(), zoom);
        int y = screen(cameraY, box.y(), zoom);
        int right = screen(cameraX, box.x() + box.width(), zoom);
        int bottom = screen(cameraY, box.y() + box.height(), zoom);
        if (right <= x || bottom <= y) return;
        int accent = selected ? 0xfff6c453 : adjacent ? 0xff83c5be : nodeBorder(graph, node);
        int background = selected ? 0xee2b271c : adjacent ? 0xee182527 : 0xee171b20;
        graphics.fill(x, y, right, bottom, background);
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            drawFolder(graphics, graph, node, x, y, right, bottom, accent);
        } else {
            drawCompactContent(graphics, node, x, y, right, bottom, accent, zoom);
        }
    }

    private static void drawFolder(GuiGraphics graphics, ClientCraftingGraph graph, ClientCraftingGraph.Node node,
            int x, int y, int right, int bottom, int border) {
        CompactTreeNode metadata = graph.compactTreeNode(node.id());
        if (metadata == null || bottom - y < 14) return;
        Font font = Minecraft.getInstance().font;
        String symbol = metadata.containsCycle() ? "↻" : metadata.containsMissing() ? "!"
            : metadata.containsUnsupported() ? "?" : "▱";
        graphics.drawCenteredString(font, symbol, (x + right) / 2, y + 5, border);
        if (bottom - y >= 24) drawAmount(graphics, font, "+" + metadata.hiddenNodeCount(),
            x, y + 20, right, bottom);
    }

    private static void drawCompactContent(GuiGraphics graphics, ClientCraftingGraph.Node node, int x, int y,
            int right, int bottom, int border, float zoom) {
        Font font = Minecraft.getInstance().font;
        if (bottom - y < 14) return;
        if (node.kind() == ClientCraftingGraph.Kind.MATERIAL
                || node.kind() == ClientCraftingGraph.Kind.REFERENCE && node.material() != null) {
            var material = node.material();
            if (material != null && material.key() != null && right - x >= 16 && bottom - y >= 20) {
                int iconX = x + (right - x - 16) / 2;
                int iconY = y + (Math.min(20, bottom - y) - 16) / 2;
                AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, iconX, iconY, material.key());
            }
            if (material != null && material.key() != null && material.requested() > 0 && bottom - y >= 24) {
                drawAmount(graphics, font, material.key().formatAmount(material.requested(), AmountFormat.SLOT),
                    x, y + 20, right, bottom);
            }
        } else if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) {
            var cycle = node.cycle();
            String label = cycle == null ? node.label() : "↻ Cycle #" + cycle.componentId();
            graphics.drawString(font, fit(font, label, Math.max(20, right - x - 24)), x + 6, y + 5, TEXT, false);
            if (cycle != null) graphics.drawString(font, cycle.memberNodeIds().size() + " items", x + 6, y + 19,
                MUTED, false);
            graphics.drawString(font, "↻", right - 14, y + 9, border, false);
        } else if (node.kind() == ClientCraftingGraph.Kind.REFERENCE) {
            graphics.drawString(font, fit(font, node.label(), Math.max(20, right - x - 10)), x + 6, y + 9,
                MUTED, false);
        }
    }

    private static void drawAmount(GuiGraphics graphics, Font font, String amount, int left, int top,
            int right, int bottom) {
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
        graphics.drawString(font, amount, -font.width(amount) / 2, 0, TEXT, false);
        pose.popPose();
    }

    private static List<Component> tooltip(ClientCraftingGraph graph, @Nullable ClientCraftingGraph.Node node) {
        if (node == null) return List.of();
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            var folder = graph.compactTreeNode(node.id());
            if (folder == null) return List.of(Component.literal(node.label()));
            List<Component> result = new ArrayList<>();
            result.add(Component.literal(node.label()));
            result.add(Component.literal("hidden nodes: " + folder.hiddenNodeCount()));
            result.add(Component.literal("hidden layers: " + folder.hiddenDepth()));
            result.add(Component.literal("missing: " + folder.containsMissing() + ", unsupported: "
                + folder.containsUnsupported() + ", cycle: " + folder.containsCycle()));
            result.add(Component.literal("click: expand one level, right click: expand all"));
            return result;
        }
        if (node.kind() == ClientCraftingGraph.Kind.REFERENCE) {
            var treeNode = graph.compactTreeNode(node.id());
            return List.of(Component.literal(node.label()), Component.literal("shared dependency reference"),
                Component.literal(treeNode == null ? "" : "source id: " + treeNode.sourceId()));
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP && node.cycle() != null) {
            var cycle = node.cycle();
            return List.of(Component.literal("Cycle #" + cycle.componentId()),
                Component.literal("members: " + cycle.memberNodeIds().size()),
                Component.literal("status: " + cycle.status()),
                Component.literal("double click: open cycle focus"));
        }
        if (node.material() != null) {
            var material = node.material();
            return List.of(Component.literal(node.label()), Component.literal("requested: " + material.requested()),
                Component.literal("inventory: " + material.fromInventory()),
                Component.literal("to craft: " + material.toCraft()), Component.literal("missing: " + material.missing()),
                Component.literal("status: " + material.status()));
        }
        return List.of(Component.literal(node.label()));
    }

    private static int nodeBorder(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            var metadata = graph.compactTreeNode(node.id());
            if (metadata != null && metadata.containsCycle()) return 0xffd59b45;
            if (metadata != null && metadata.containsMissing()) return 0xffcf5e5e;
            if (metadata != null && metadata.containsUnsupported()) return 0xffa36c9a;
            return 0xff788996;
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) return 0xffd59b45;
        if (node.kind() == ClientCraftingGraph.Kind.REFERENCE) return 0xff8d98a3;
        if (node.material() == null) return 0xff8293a4;
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

    private static int screen(float camera, float world, float zoom) { return Math.round(camera + world * zoom); }

    private static String fit(Font font, String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }
}
