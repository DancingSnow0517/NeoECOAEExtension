package cn.dancingsnow.neoecoae.client.craftinggraph;

import appeng.api.client.AEKeyRendering;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Immediate-mode renderer. Only queried viewport primitives reach the draw loops. */
public final class GraphRenderer {
    private static final int TEXT = 0xffe8edf2;
    private static final int MUTED = 0xffaab3bd;

    public record Frame(@Nullable ClientCraftingGraph.Node hovered, List<Component> tooltip) {}
    public record VisiblePlan(List<GraphLayoutSnapshot.Box> nodes, List<ClientCraftingGraph.Link> edges) {}
    private final CompactTreeRenderer compactTreeRenderer = new CompactTreeRenderer();

    static VisiblePlan collectVisible(GraphLayoutSnapshot layout, float cameraX, float cameraY, float zoom,
            int screenWidth, int screenHeight) {
        float left = -cameraX / zoom;
        float top = (30 - cameraY) / zoom;
        float right = (screenWidth - cameraX) / zoom;
        float bottom = (screenHeight - cameraY) / zoom;
        return new VisiblePlan(layout.query(left, top, right, bottom, 180 / zoom),
            layout.queryLinks(left, top, right, bottom, 180 / zoom));
    }

    public Frame render(GuiGraphics graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            float cameraX, float cameraY, float zoom, int screenWidth, int screenHeight, int mouseX, int mouseY,
            @Nullable Integer selectedId, GraphProfiler profiler) {
        if (graph.isCompactTree()) {
            return compactTreeRenderer.render(graphics, graph, layout, cameraX, cameraY, zoom, screenWidth,
                screenHeight, mouseX, mouseY, selectedId, profiler);
        }
        long started = System.nanoTime();
        VisiblePlan visible = collectVisible(layout, cameraX, cameraY, zoom, screenWidth, screenHeight);
        List<GraphLayoutSnapshot.Box> boxes = visible.nodes();
        List<ClientCraftingGraph.Link> links = visible.edges();
        Set<Integer> neighborhood = selectedId == null ? Set.of() : graph.neighborhood(selectedId);
        Set<Integer> visibleIds = new HashSet<>();
        boxes.forEach(box -> visibleIds.add(box.nodeId()));

        for (var link : links) {
            drawLink(graphics, graph, layout, link, selectedId, neighborhood, cameraX, cameraY, zoom);
        }
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
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
        return new Frame(hovered, tooltip(graph, hovered));
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
        graphics.hLine(Math.min(x1, middle), Math.max(x1, middle), y1, color);
        graphics.vLine(middle, Math.min(y1, y2), Math.max(y1, y2), color);
        graphics.hLine(Math.min(middle, x2), Math.max(middle, x2), y2, color);
        graphics.fill(x2 - 4, y2 - 2, x2, y2 + 3, color);
    }

    private static void drawCycleOutputArrow(GuiGraphics graphics, ClientCraftingGraph graph,
            GraphLayoutSnapshot layout, float cameraX, float cameraY, float zoom) {
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
        graphics.fill(x - width / 2, y - 8, x + (width + 1) / 2, y + 3, 0xcc171b20);
        graphics.drawCenteredString(font, text, x, y - 7, MUTED);
    }

    private static String edgeAmount(ClientCraftingGraph graph, ClientCraftingGraph.Link link) {
        if (link.amount() == 0) return "";
        ClientCraftingGraph.Node material = graph.nodes().get(link.fromId());
        if (material == null || material.kind() != ClientCraftingGraph.Kind.MATERIAL) {
            material = graph.nodes().get(link.toId());
        }
        AEKey key = material == null || material.material() == null ? null : material.material().key();
        if (key == null) return "×" + link.amount();
        long magnitude = link.amount() == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(link.amount());
        return "×" + key.formatAmount(magnitude, AmountFormat.SLOT);
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
                graphics.vLine(x1, Math.min(y1, y2), Math.max(y1, y2), color);
            } else if (y1 == y2) {
                graphics.hLine(Math.min(x1, x2), Math.max(x1, x2), y1, color);
            } else {
                drawDiagonal(graphics, x1, y1, x2, y2, color);
            }
        }
    }

    private static void drawDiagonal(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + Math.round(dx * (i / (float) steps));
            int y = y1 + Math.round(dy * (i / (float) steps));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
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
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
            drawCompactCycleNode(graphics, graph, node, box, border, selected, x, y, right, bottom,
                pixelWidth, pixelHeight, cameraY, zoom);
            return;
        }
        graphics.fill(x, y, right, bottom, 0xee171b20);
        int borderWidth = selected ? 3 : 2;
        graphics.fill(x, y, right, Math.min(bottom, y + borderWidth), border);
        graphics.fill(x, Math.max(y, bottom - 2), right, bottom, border);
        graphics.fill(x, y, Math.min(right, x + 2), bottom, border);
        graphics.fill(Math.max(x, right - 2), y, right, bottom, border);
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
                            "合成 " + compactAmount(material.exactToCraft()) + "  缺少 "
                                + compactAmount(material.exactMissing()),
                            x + 7, y + 53, pixelWidth - 14,
                            material.missingBigInteger().signum() > 0 ? 0xffff7777 : MUTED);
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
                    graphics.drawCenteredString(font, fit(font, "× " + pattern.firingCount(), pixelWidth - 6),
                        (x + right) / 2, y + 18, TEXT);
                }
            }
            case CYCLE_GROUP -> {
                var cycle = node.cycle();
                drawFitted(graphics, font, "↻  Cycle #" + cycle.componentId(), x + 7, y + 7,
                    pixelWidth - 14, border);
                if (zoom >= 0.6f && pixelHeight >= 38) {
                    drawFitted(graphics, font, cycle.memberNodeIds().size() + " members · " + cycle.status(),
                        x + 7, y + 23, pixelWidth - 14, TEXT);
                    String required = cycle.requiredOutputs().isEmpty() ? "net output: -"
                        : "required outputs: " + cycle.requiredOutputs().size();
                    if (pixelHeight >= 55) drawFitted(graphics, font, required, x + 7, y + 40,
                        pixelWidth - 14, MUTED);
                    if (pixelHeight >= 68) drawFitted(graphics, font, "双击查看内部图", x + 7, y + 54,
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
        graphics.fill(x, y, right, bottom, 0xee171b20);
        int borderWidth = selected ? 2 : 1;
        graphics.fill(x, y, right, Math.min(bottom, y + borderWidth), border);
        graphics.fill(x, Math.max(y, bottom - borderWidth), right, bottom, border);
        graphics.fill(x, y, Math.min(right, x + borderWidth), bottom, border);
        graphics.fill(Math.max(x, right - borderWidth), y, right, bottom, border);
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
        int textY = numberTop + Math.max(0, (bottom - numberTop - font.lineHeight) / 2);
        graphics.drawCenteredString(font, fit(font, value.text(), Math.max(1, pixelWidth - 2)),
            (x + right) / 2, textY, value.color());
    }

    private record CompactCycleValue(String text, int color) {}

    private static CompactCycleValue compactCycleValue(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN && node.pattern() != null) {
            return new CompactCycleValue(compactAmount(Long.toString(node.pattern().firingCount())), TEXT);
        }
        if (node.material() == null) return new CompactCycleValue("-", MUTED);
        var cycle = graph.source().cycleGroups().stream()
            .filter(value -> value.componentId() == graph.focusedCycleId()).findFirst().orElse(null);
        if (cycle == null) return new CompactCycleValue("-", MUTED);
        AEKey key = node.material().key();
        long amount;
        boolean signed;
        if (graph.isExternalInput(node.id())) {
            amount = amountFor(cycle.externalInputs(), key);
            signed = false;
        } else if (graph.isBoundaryOutput(node.id())) {
            amount = graph.links().stream().filter(link -> link.toId() == node.id())
                .mapToLong(ClientCraftingGraph.Link::amount).sum();
            signed = true;
        } else {
            amount = amountFor(cycle.singleNetOutputs(), key);
            signed = true;
        }
        String magnitude = compactAmount(Long.toString(amount == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(amount)));
        String text = signed && amount > 0 ? "+" + magnitude : signed && amount < 0 ? "-" + magnitude : magnitude;
        int color = amount == 0 ? MUTED : signed && amount < 0 ? 0xffe07a7a
            : signed && amount > 0 ? 0xff75c48b : TEXT;
        return new CompactCycleValue(text, color);
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
            lines.add(Component.literal("firing count: " + pattern.firingCount()));
            lines.add(Component.literal("status: " + pattern.status().name()));
            lines.add(Component.literal("inputs: " + relationshipText(graph, pattern.inputs())));
            lines.add(Component.literal("outputs: " + relationshipText(graph, pattern.outputs())));
            if (pattern.rejectionReason() != null) lines.add(Component.literal(pattern.rejectionReason()));
            return lines;
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) {
            var cycle = node.cycle();
            return List.of(Component.literal("Cycle #" + cycle.componentId()), Component.literal(cycle.status()),
                Component.literal("members: " + cycle.memberNodeIds().size()),
                Component.literal("required outputs: " + cycle.requiredOutputs().size()),
                Component.literal("external inputs: " + cycle.externalInputs().size()),
                Component.literal("required seed: " + cycle.requiredSeed().size()));
        }
        if (node.key() == null) return List.of();
        var lines = new ArrayList<>(AEKeyRendering.getTooltip(node.key()));
        lines.add(Component.literal("registry id: " + node.key().getId()));
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && node.material() != null) {
            var cycle = graph.source().cycleGroups().stream()
                .filter(value -> value.componentId() == graph.focusedCycleId()).findFirst().orElse(null);
            if (cycle != null) {
                lines.add(Component.literal("single net output: "
                    + signed(node.key(), amountFor(cycle.singleNetOutputs(), node.key()))));
                lines.add(Component.literal("total net output: "
                    + signed(node.key(), amountFor(cycle.totalNetOutputs(), node.key()))));
                lines.add(Component.literal("required seed: " + amountText(cycle.requiredSeed(), node.key())));
                lines.add(Component.literal("required output: " + amountText(cycle.requiredOutputs(), node.key())));
                lines.add(Component.literal("external input: " + amountText(cycle.externalInputs(), node.key())));
            }
        }
        return lines;
    }

    private static int border(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) return 0xffd59b45;
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
            return HostText.hugeStackAmount(new java.math.BigInteger(value));
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    /** Draws a tangent-aligned triangle so curved cycle arrows do not snap to four directions. */
    private static void drawArrowHead(GuiGraphics graphics, List<GraphLayoutSnapshot.Point> route, int color,
            float cameraX, float cameraY, float zoom) {
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
        float baseX = tipX - unitX * 6f;
        float baseY = tipY - unitY * 6f;
        float normalX = -unitY * 3.5f;
        float normalY = unitX * 3.5f;
        fillTriangle(graphics, tipX, tipY, baseX + normalX, baseY + normalY,
            baseX - normalX, baseY - normalY, color);
    }

    private static void fillTriangle(GuiGraphics graphics, float ax, float ay, float bx, float by,
            float cx, float cy, int color) {
        int minX = (int) Math.floor(Math.min(ax, Math.min(bx, cx)));
        int maxX = (int) Math.ceil(Math.max(ax, Math.max(bx, cx)));
        int minY = (int) Math.floor(Math.min(ay, Math.min(by, cy)));
        int maxY = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));
        float orientation = edge(ax, ay, bx, by, cx, cy);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float one = edge(ax, ay, bx, by, px, py);
                float two = edge(bx, by, cx, cy, px, py);
                float three = edge(cx, cy, ax, ay, px, py);
                if (orientation >= 0 ? one >= 0 && two >= 0 && three >= 0
                        : one <= 0 && two <= 0 && three <= 0) {
                    graphics.fill(x, y, x + 1, y + 1, color);
                }
            }
        }
    }

    private static float edge(float ax, float ay, float bx, float by, float px, float py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
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
            return label + " ×" + relationship.amount();
        }).reduce((left, right) -> left + ", " + right).orElse("-");
    }

    private static String signed(AEKey key, long amount) {
        String value = formatMagnitude(key, amount);
        return amount > 0 ? "+" + value : amount < 0 ? "-" + value : "0";
    }

    private static String formatMagnitude(AEKey key, long amount) {
        long magnitude = amount == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(amount);
        return key.formatAmount(magnitude, AmountFormat.SLOT);
    }

}
