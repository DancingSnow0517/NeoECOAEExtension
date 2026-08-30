package cn.dancingsnow.neoecoae.client.craftinggraph;

import appeng.api.client.AEKeyRendering;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
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
            var end = route.getLast();
            int endX = screen(cameraX, end.x(), zoom);
            int endY = screen(cameraY, end.y(), zoom);
            graphics.fill(endX - 4, endY - 2, endX, endY + 3, color);
            if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && zoom >= 0.72f) {
                drawEdgeAmount(graphics, graph, link, route, cameraX, cameraY, zoom);
            }
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
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && zoom >= 0.72f) {
            drawEdgeAmount(graphics, graph, link, List.of(new GraphLayoutSnapshot.Point(x1, y1),
                new GraphLayoutSnapshot.Point(x2, y2)), cameraX, cameraY, zoom);
        }
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
                // Rounding can introduce a one-pixel diagonal between otherwise orthogonal points.
                int middle = (x1 + x2) / 2;
                graphics.hLine(Math.min(x1, middle), Math.max(x1, middle), y1, color);
                graphics.vLine(middle, Math.min(y1, y2), Math.max(y1, y2), color);
                graphics.hLine(Math.min(middle, x2), Math.max(middle, x2), y2, color);
            }
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
                if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
                    drawCycleMaterial(graphics, graph, node, box, border, x, y, right, bottom, pixelWidth,
                        pixelHeight, zoom, font);
                    break;
                }
                if (zoom >= 0.7f && pixelWidth >= 82 && pixelHeight >= 48) {
                    AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x + 7, y + 8, material.key());
                    drawFitted(graphics, font, node.label(), x + 29, y + 7, pixelWidth - 35, TEXT);
                    drawFitted(graphics, font, statusSymbol(material.status()) + " " + material.status().name(),
                        x + 29, y + 18, pixelWidth - 35, border);
                    drawFitted(graphics, font,
                        "需求 " + material.exactRequested() + "  库存 " + material.exactFromInventory(),
                        x + 7, y + 38, pixelWidth - 14, MUTED);
                    if (pixelHeight >= 64) {
                        drawFitted(graphics, font,
                            "合成 " + material.exactToCraft() + "  缺少 " + material.exactMissing(),
                            x + 7, y + 53, pixelWidth - 14, material.missing() > 0 ? 0xffff7777 : MUTED);
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
                String symbol = graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS ? "⚒"
                    : pattern.status() == CraftingGraphSnapshot.CandidateStatus.SELECTED ? "⚒" : "×";
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

    private static void drawCycleMaterial(GuiGraphics graphics, ClientCraftingGraph graph,
            ClientCraftingGraph.Node node, GraphLayoutSnapshot.Box box, int border, int x, int y, int right,
            int bottom, int pixelWidth, int pixelHeight, float zoom, Font font) {
        var material = node.material();
        if (zoom >= 0.55f && pixelWidth >= 82 && pixelHeight >= 54) {
            AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x + 6, y + 5, material.key());
            String prefix = graph.isExternalInput(node.id()) ? "↤ "
                : graph.isBoundaryOutput(node.id()) ? "↦ " : "";
            drawFitted(graphics, font, prefix + node.label(), x + 29, y + 6, pixelWidth - 35, TEXT);
            var cycle = graph.source().cycleGroups().stream()
                .filter(value -> value.componentId() == graph.focusedCycleId()).findFirst().orElse(null);
            if (cycle != null && !graph.isBoundaryMaterial(node.id())) {
                long single = amountFor(cycle.singleNetOutputs(), material.key());
                long total = amountFor(cycle.totalNetOutputs(), material.key());
                drawFitted(graphics, font, "单次 " + signed(material.key(), single), x + 6, y + 38,
                    pixelWidth - 12, single == 0 ? MUTED : single > 0 ? 0xff75c48b : 0xffe07a7a);
                drawFitted(graphics, font, "总计 " + signed(material.key(), total), x + 6, y + 52,
                    pixelWidth - 12, total == 0 ? MUTED : total > 0 ? 0xff75c48b : 0xffe07a7a);
            } else {
                drawFitted(graphics, font,
                    graph.isExternalInput(node.id()) ? "外部输入" : "循环副产物/边界输出", x + 6, y + 38,
                    pixelWidth - 12, MUTED);
            }
        } else if (zoom >= 0.42f && pixelWidth >= 48 && pixelHeight >= 22) {
            AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x + 4, y + 4, material.key());
            drawFitted(graphics, font, node.label(), x + 24, y + 4, pixelWidth - 28, TEXT);
        } else {
            graphics.drawCenteredString(font, graph.isBoundaryMaterial(node.id()) ? "·" : "↻",
                (x + right) / 2, y + Math.max(2, (pixelHeight - font.lineHeight) / 2), border);
        }
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
