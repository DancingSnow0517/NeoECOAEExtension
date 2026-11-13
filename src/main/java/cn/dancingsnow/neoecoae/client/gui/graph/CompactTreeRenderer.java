package cn.dancingsnow.neoecoae.client.gui.graph;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.CompactTreeNode;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Minecraft-style compact node art in world units. The caller owns the camera transform, so every zoom level and
 * the raster cache draw exactly the same picture.
 */
final class CompactTreeRenderer {
    private CompactTreeRenderer() {}

    static void drawBody(GuiGraphics graphics, GraphLayoutSnapshot.Box box) {
        GraphDraw.fill(graphics, box.x(), box.y(), box.x() + box.width(), box.y() + box.height(),
            GraphTheme.current().panel());
    }

    /** Material status as a bottom strip; the node frame itself stays neutral. */
    static void drawStatusStrip(GuiGraphics graphics, ClientCraftingGraph graph, ClientCraftingGraph.Node node,
            GraphLayoutSnapshot.Box box) {
        if (node.material() == null && node.kind() != ClientCraftingGraph.Kind.FOLDER) return;
        float inset = Math.min(2, box.width() / 4);
        GraphDraw.fill(graphics, box.x() + inset, box.y() + box.height() - 1.5f,
            box.x() + box.width() - inset, box.y() + box.height(), nodeBorder(graph, node));
    }

    static void drawIcon(GuiGraphics graphics, ClientCraftingGraph.Node node, GraphLayoutSnapshot.Box box) {
        var material = node.material();
        if (material == null || material.key() == null) return;
        if (node.kind() != ClientCraftingGraph.Kind.MATERIAL && node.kind() != ClientCraftingGraph.Kind.REFERENCE) return;
        boolean large = box.width() >= GraphLayoutSnapshot.MATERIAL_WIDTH;
        float iconX = large ? box.x() + 7 : box.x() + (box.width() - 16) / 2;
        float iconY = large ? box.y() + 8 : box.y() + (Math.min(20, box.height()) - 16) / 2;
        // Non-item keys (fluids) blit immediately; the buffered bodies were flushed by the caller beforehand.
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(iconX, iconY, 0);
        AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, 0, 0, material.key());
        pose.popPose();
        if (!(material.key() instanceof AEItemKey)) graphics.flush();
    }

    static void drawText(GuiGraphics graphics, ClientCraftingGraph graph, ClientCraftingGraph.Node node,
            GraphLayoutSnapshot.Box box) {
        Font font = Minecraft.getInstance().font;
        float x = box.x(), y = box.y(), right = x + box.width(), bottom = y + box.height();
        int accent = nodeBorder(graph, node);
        int text = GraphTheme.current().text(), muted = GraphTheme.current().muted();
        switch (node.kind()) {
            case FOLDER -> {
                CompactTreeNode metadata = graph.compactTreeNode(node.id());
                if (metadata == null) return;
                String symbol = metadata.containsCycle() ? "↻" : metadata.containsMissing() ? "!"
                    : metadata.containsUnsupported() ? "?" : "▱";
                GraphDraw.centeredText(graphics, font, symbol, (x + right) / 2, y + 5, accent, 1);
                drawAmount(graphics, font, "+" + metadata.hiddenNodeCount(), x, y + 20, right, bottom, text);
            }
            case MATERIAL, REFERENCE -> {
                var material = node.material();
                if (material == null || material.key() == null) {
                    GraphDraw.centeredText(graphics, font, "↪", (x + right) / 2, y + (box.height() - 9) / 2, muted, 1);
                    return;
                }
                if (box.width() >= GraphLayoutSnapshot.MATERIAL_WIDTH) {
                    GraphDraw.text(graphics, font, GraphDraw.fit(font, nodeTitle(node), box.width() - 35),
                        x + 29, y + 7, text, 1);
                    GraphDraw.text(graphics, font, GraphDraw.fit(font, Component.translatable(
                        "gui.neoecoae.crafting_graph.status." + material.status().name().toLowerCase(java.util.Locale.ROOT))
                        .getString(), box.width() - 35), x + 29, y + 18, accent, 1);
                    GraphDraw.text(graphics, font, GraphDraw.fit(font, Component.translatable(
                        "gui.neoecoae.crafting_graph.details.requested", HostText.ae2Amount(material.requestedBigInteger()))
                        .getString(), box.width() - 14), x + 7, y + 35, muted, 1);
                    GraphDraw.text(graphics, font, GraphDraw.fit(font, Component.translatable(
                        "gui.neoecoae.crafting_graph.details.inventory", HostText.ae2Amount(material.fromInventoryBigInteger()))
                        .getString(), box.width() - 14), x + 7, y + 47, muted, 1);
                    GraphDraw.text(graphics, font, GraphDraw.fit(font, materialAmount(node), box.width() - 14),
                        x + 7, y + 59, muted, 1);
                    return;
                }
                if (node.recursion() != null || material.consumedBigInteger().signum() > 0
                        || material.producedBigInteger().signum() > 0 || material.requestedBigInteger().signum() > 0) {
                    drawAmount(graphics, font, materialAmount(node), x, y + 20, right, bottom, text);
                }
            }
            case CYCLE_GROUP -> {
                var cycle = node.cycle();
                GraphDraw.text(graphics, font, GraphDraw.fit(font, nodeTitle(node), box.width() - 24), x + 6, y + 5,
                    text, 1);
                if (cycle != null) GraphDraw.text(graphics, font,
                    GraphDraw.fit(font, GraphRenderer.recursiveModuleSummary(cycle).getString(), box.width() - 12),
                    x + 6, y + 19, muted, 1);
                GraphDraw.text(graphics, font, "↻", right - 14, y + 9, accent, 1);
            }
            case CYCLE_CLUSTER -> {
                var cluster = node.cluster();
                GraphDraw.text(graphics, font, GraphDraw.fit(font, nodeTitle(node), box.width() - 24), x + 6, y + 5,
                    text, 1);
                if (cluster != null) GraphDraw.text(graphics, font, GraphDraw.fit(font, Component.translatable(
                        "gui.neoecoae.crafting_graph.details.cluster_cycles", cluster.componentIds().size()).getString(),
                    box.width() - 12), x + 6, y + 19, muted, 1);
                GraphDraw.text(graphics, font, "◎", right - 14, y + 9, accent, 1);
            }
            case PATTERN -> {
                var pattern = node.pattern();
                GraphDraw.centeredText(graphics, font, "⚒", (x + right) / 2, y + 4, accent, 1);
                if (pattern != null) drawAmount(graphics, font,
                    "×" + HostText.ae2Amount(pattern.plannerFiringCount().toBigInteger()), x, y + 16, right, bottom, text);
            }
        }
    }

    /** Localized title for module nodes; material titles come from their key. */
    static String nodeTitle(ClientCraftingGraph.Node node) {
        if (node.cycle() != null && node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) {
            return GraphRenderer.recursionText("module", node.cycle().componentId()).getString();
        }
        if (node.cluster() != null && node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER) {
            return GraphRenderer.recursionText("modules", node.cluster().clusterId()).getString();
        }
        return node.key() != null ? node.key().getDisplayName().getString() : node.label();
    }

    static void drawAmount(GuiGraphics graphics, Font font, String amount, float left, float top,
            float right, float bottom, int color) {
        float availableWidth = Math.max(1, right - left - 2);
        float availableHeight = Math.max(1, bottom - top);
        float scale = Math.min(0.5f, Math.min(availableWidth / Math.max(1, font.width(amount)),
            availableHeight / font.lineHeight));
        GraphDraw.centeredText(graphics, font, amount, (left + right) / 2,
            top + (availableHeight - font.lineHeight * scale) / 2, color, scale);
    }

    static List<Component> tooltip(ClientCraftingGraph graph, @Nullable ClientCraftingGraph.Node node) {
        if (node == null) return List.of();
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            var folder = graph.compactTreeNode(node.id());
            if (folder == null) return List.of(Component.literal(node.label()));
            List<Component> result = new ArrayList<>();
            result.add(Component.literal(node.label()));
            result.add(Component.translatable("gui.neoecoae.crafting_graph.details.hidden_nodes", folder.hiddenNodeCount()));
            result.add(Component.translatable("gui.neoecoae.crafting_graph.details.hidden_layers", folder.hiddenDepth()));
            result.add(Component.translatable("gui.neoecoae.crafting_graph.details.missing", yesNo(folder.containsMissing())));
            result.add(Component.translatable("gui.neoecoae.crafting_graph.details.unsupported", yesNo(folder.containsUnsupported())));
            result.add(Component.translatable("gui.neoecoae.crafting_graph.details.cycle", yesNo(folder.containsCycle())));
            result.add(Component.translatable("gui.neoecoae.crafting_graph.action.expand_hint"));
            return result;
        }
        if (GraphMaterialCard.supports(node)) return GraphMaterialCard.builder(graph, node, false).texts();
        if (node.kind() == ClientCraftingGraph.Kind.REFERENCE) {
            var treeNode = graph.compactTreeNode(node.id());
            return List.of(Component.literal("↪ " + nodeTitle(node)),
                Component.translatable("gui.neoecoae.crafting_graph.reference.description"),
                treeNode == null ? Component.empty()
                    : Component.translatable("gui.neoecoae.crafting_graph.reference.source", treeNode.sourceId()));
        }
        if (node.material() != null) return GraphMaterialCard.builder(graph, node, false).texts();
        return GraphRenderer.tooltip(graph, node);
    }

    private static Component yesNo(boolean value) {
        return Component.translatable("gui.neoecoae.common." + (value ? "yes" : "no"));
    }

    static int nodeBorder(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        var theme = GraphTheme.current();
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            var metadata = graph.compactTreeNode(node.id());
            if (metadata != null && metadata.containsCycle()) return theme.pending();
            if (metadata != null && metadata.containsMissing()) return theme.missing();
            if (metadata != null && metadata.containsUnsupported()) return theme.byproduct();
            return theme.muted();
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP || node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER) {
            return theme.pending();
        }
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN) {
            return node.pattern() != null && node.pattern().status() == CraftingGraphSnapshot.CandidateStatus.SELECTED
                ? theme.stock() : theme.missing();
        }
        if (node.kind() == ClientCraftingGraph.Kind.REFERENCE || node.material() == null) return theme.faint();
        return switch (node.material().status()) {
            case SATISFIED -> theme.stock();
            case CRAFTING, CYCLE -> theme.pending();
            case MISSING -> theme.missing();
            case UNSUPPORTED -> theme.byproduct();
        };
    }

    private static String flowText(CraftingGraphSnapshot.MaterialNode material) {
        if (showsCraftQuantity(material)) {
            // Intermediate products can be entirely consumed by the order: zero net change still needs crafting.
            return "\u00d7" + GraphRenderer.formatMagnitude(material.key(), PlannerAmount.of(material.toCraftBigInteger()));
        }
        if (material.consumedBigInteger().signum() == 0 && material.producedBigInteger().signum() == 0) {
            return HostText.ae2Amount(material.requestedBigInteger());
        }
        PlannerAmount delta = PlannerAmount.of(material.producedBigInteger()).subtract(PlannerAmount.of(material.consumedBigInteger()));
        String amount = HostText.ae2Amount(delta.abs().toBigInteger());
        return delta.signum() > 0 ? "+" + amount : delta.signum() < 0 ? "-" + amount : "0";
    }

    /** Recursive occurrences show their own checkpoint stock/input, never the complete order's net change. */
    static String materialAmount(ClientCraftingGraph.Node node) {
        return node.recursion() == null ? flowText(node.material())
            : GraphRenderer.formatMagnitude(node.key(), node.recursion().amount());
    }

    static boolean showsCraftQuantity(CraftingGraphSnapshot.MaterialNode material) {
        return material.status() == CraftingGraphSnapshot.MaterialStatus.CRAFTING
            && material.toCraftBigInteger().signum() > 0
            && material.producedBigInteger().equals(material.consumedBigInteger());
    }
}
