package cn.dancingsnow.neoecoae.client.craftinggraph;

import appeng.api.client.AEKeyRendering;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Immediate-mode renderer. Only queried viewport primitives reach the draw loops. */
public final class GraphRenderer {
    private static final int TEXT = 0xffe8edf2;
    private static final int MUTED = 0xffaab3bd;

    public record Frame(@Nullable ClientCraftingGraph.Node hovered, List<Component> tooltip) {}

    public Frame render(GuiGraphics graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            float cameraX, float cameraY, float zoom, int screenWidth, int screenHeight, int mouseX, int mouseY,
            @Nullable Integer selectedId, GraphProfiler profiler) {
        long started = System.nanoTime();
        float left = -cameraX / zoom;
        float top = (30 - cameraY) / zoom;
        float right = (screenWidth - cameraX) / zoom;
        float bottom = (screenHeight - cameraY) / zoom;
        List<GraphLayoutSnapshot.Box> boxes = layout.query(left, top, right, bottom, 180 / zoom);
        List<ClientCraftingGraph.Link> links = layout.queryLinks(left, top, right, bottom, 180 / zoom);
        Set<Integer> neighborhood = selectedId == null ? Set.of() : graph.neighborhood(selectedId);
        Set<Integer> visibleIds = new HashSet<>();
        boxes.forEach(box -> visibleIds.add(box.nodeId()));

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cameraX, cameraY, 0);
        pose.scale(zoom, zoom, 1);
        for (var link : links) drawLink(graphics, layout, link, selectedId, neighborhood);
        ClientCraftingGraph.Node hovered = null;
        float worldMouseX = (mouseX - cameraX) / zoom;
        float worldMouseY = (mouseY - cameraY) / zoom;
        for (var box : boxes) {
            ClientCraftingGraph.Node node = graph.nodes().get(box.nodeId());
            if (node == null) continue;
            boolean selected = selectedId != null && selectedId == node.id();
            boolean adjacent = neighborhood.contains(node.id());
            drawNode(graphics, node, box, selected, adjacent);
            if (box.contains(worldMouseX, worldMouseY)) hovered = node;
        }
        pose.popPose();
        profiler.update(boxes.size(), links.size(), System.nanoTime() - started);
        return new Frame(hovered, tooltip(hovered));
    }

    private static void drawLink(GuiGraphics graphics, GraphLayoutSnapshot layout, ClientCraftingGraph.Link link,
            @Nullable Integer selected, Set<Integer> neighborhood) {
        var from = layout.box(link.fromId());
        var to = layout.box(link.toId());
        if (from == null || to == null) return;
        boolean highlighted = selected != null && (link.fromId() == selected || link.toId() == selected)
            && (neighborhood.contains(link.fromId()) || neighborhood.contains(link.toId()));
        int color = highlighted ? 0xfff6c453 : link.selected() ? 0xff8293a4 : 0xff66505a;
        int x1 = Math.round(from.x() + from.width());
        int y1 = Math.round(from.centerY());
        int x2 = Math.round(to.x());
        int y2 = Math.round(to.centerY());
        int middle = (x1 + x2) / 2;
        graphics.hLine(Math.min(x1, middle), Math.max(x1, middle), y1, color);
        graphics.vLine(middle, Math.min(y1, y2), Math.max(y1, y2), color);
        graphics.hLine(Math.min(middle, x2), Math.max(middle, x2), y2, color);
        graphics.fill(x2 - 4, y2 - 2, x2, y2 + 3, color);
    }

    private static void drawNode(GuiGraphics graphics, ClientCraftingGraph.Node node, GraphLayoutSnapshot.Box box,
            boolean selected, boolean adjacent) {
        int x = Math.round(box.x());
        int y = Math.round(box.y());
        int right = Math.round(box.x() + box.width());
        int bottom = Math.round(box.y() + box.height());
        int border = selected ? 0xfff6c453 : adjacent ? 0xff83c5be : border(node);
        graphics.fill(x, y, right, bottom, 0xee171b20);
        graphics.fill(x, y, right, y + (selected ? 3 : 2), border);
        graphics.fill(x, bottom - 2, right, bottom, border);
        graphics.fill(x, y, x + 2, bottom, border);
        graphics.fill(right - 2, y, right, bottom, border);
        var font = Minecraft.getInstance().font;
        switch (node.kind()) {
            case MATERIAL -> {
                var material = node.material();
                AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x + 7, y + 8, material.key());
                graphics.drawString(font, trim(node.label(), 18), x + 29, y + 7, TEXT, false);
                graphics.drawString(font, statusSymbol(material.status()) + " " + material.status().name(),
                    x + 29, y + 18, border, false);
                graphics.drawString(font, "需求 " + material.requested() + "  库存 " + material.fromInventory(),
                    x + 7, y + 38, MUTED, false);
                graphics.drawString(font, "合成 " + material.toCraft() + "  缺少 " + material.missing(),
                    x + 7, y + 53, material.missing() > 0 ? 0xffff7777 : MUTED, false);
            }
            case PATTERN -> {
                var pattern = node.pattern();
                graphics.drawCenteredString(font, pattern.status() == CraftingGraphSnapshot.CandidateStatus.SELECTED
                    ? "⚒" : "×", (x + right) / 2, y + 6, border);
                graphics.drawCenteredString(font, "× " + pattern.firingCount(), (x + right) / 2, y + 18, TEXT);
            }
            case CYCLE_GROUP -> {
                var cycle = node.cycle();
                graphics.drawString(font, "↻  Cycle #" + cycle.componentId(), x + 9, y + 8, border, false);
                graphics.drawString(font, cycle.memberNodeIds().size() + " members · " + cycle.status(),
                    x + 9, y + 25, TEXT, false);
                String required = cycle.requiredOutputs().isEmpty() ? "net output: -"
                    : "required outputs: " + cycle.requiredOutputs().size();
                graphics.drawString(font, required, x + 9, y + 44, MUTED, false);
                graphics.drawString(font, "双击查看内部图", x + 9, y + 57, MUTED, false);
            }
        }
    }

    private static List<Component> tooltip(@Nullable ClientCraftingGraph.Node node) {
        if (node == null) return List.of();
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN) {
            var pattern = node.pattern();
            var lines = new java.util.ArrayList<Component>();
            lines.add(Component.literal(pattern.identity()));
            lines.add(Component.literal("firing count: " + pattern.firingCount()));
            lines.add(Component.literal("inputs: " + pattern.inputs().size() + ", outputs: " + pattern.outputs().size()));
            lines.add(Component.literal(pattern.status().name()));
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
        return node.key() == null ? List.of() : AEKeyRendering.getTooltip(node.key());
    }

    private static int border(ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) return 0xffd59b45;
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN) {
            return node.pattern().status() == CraftingGraphSnapshot.CandidateStatus.SELECTED ? 0xff66a182 : 0xff9a5964;
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

    private static String trim(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }
}
