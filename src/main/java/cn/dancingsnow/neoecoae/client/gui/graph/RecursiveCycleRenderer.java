package cn.dancingsnow.neoecoae.client.gui.graph;

import static cn.dancingsnow.neoecoae.client.gui.graph.GraphRenderer.recursionText;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Box;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.RecursionRegion;
import cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode.Role;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.RecursionLayer;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.PatternNode;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Compact material nodes and execution icons with shared cycle regions at every zoom/cache level. */
final class RecursiveCycleRenderer {
    static final int SEED = 0xff5fd0e8, STOCK = 0xff7bb2d4, INPUT = 0xffb48ef0;
    static final int OUTPUT = 0xfff2c14e, RETAINED = 0xff75c48b, MISSING = 0xffcf5e5e;
    static final int TEXT = 0xffe6edf3, MUTED = 0xff929ca6, STEP = 0xff83c5be;

    private RecursiveCycleRenderer() {}

    static int color(Role role) {
        return switch (role) {
            case SEED -> SEED;
            case STOCK -> STOCK;
            case INPUT -> INPUT;
            case OUTPUT -> OUTPUT;
            case RETAINED -> RETAINED;
            case STEP -> STEP;
            case UNKNOWN -> MISSING;
        };
    }

    static void drawRegion(GuiGraphics graphics, RecursionRegion region,
            float cameraX, float cameraY, float zoom) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cameraX, cameraY, 0);
        pose.scale(zoom, zoom, 1);
        try {
            var bounds = region.bounds();
            Font font = Minecraft.getInstance().font;
            boolean checkpoint = region.role() == Role.SEED || region.role() == Role.STOCK || region.role() == Role.RETAINED;
            if (region.role() != Role.STOCK) {
                int accent = color(region.role());
                GraphDraw.fill(graphics, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(),
                    0x12000000 | accent & 0xffffff);
                frame(graphics, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), accent,
                    region.role() == Role.INPUT);
                if (!checkpoint) {
                    String label = recursionText("zone_" + region.role().name().toLowerCase(java.util.Locale.ROOT)).getString();
                    GraphDraw.fill(graphics, bounds.left() + 3, bounds.top() - 5,
                        bounds.left() + Math.min(bounds.width() - 3, font.width(label) * 0.7f + 7), bounds.top() + 5, 0xff0e1115);
                    text(graphics, font, label, bounds.left() + 5, bounds.top() - 3, bounds.width() - 10, accent, 0.7f);
                }
            }
            if (checkpoint) {
                float x = bounds.left() + 5;
                float y = bounds.top() + 5;
                String label = recursionText(region.role() == Role.SEED ? "seed" : region.role() == Role.RETAINED ? "result" : "row",
                    region.level() / 2).getString();
                text(graphics, font, label, x, y, bounds.width() - 10, TEXT, 0.85f);
                if (region.role() != Role.STOCK) {
                    text(graphics, font, recursionText(region.role() == Role.RETAINED ? "result_hint" : "initial_stock").getString(),
                        x, y + 11, bounds.width() - 10, MUTED, 0.65f);
                }
            }
        } finally {
            graphics.flush();
            pose.popPose();
        }
    }

    static void drawNode(GuiGraphics graphics, ClientCraftingGraph graph, ClientCraftingGraph.Node node,
            Box box, boolean selected, boolean adjacent, float cameraX, float cameraY, float zoom) {
        var detail = node.recursion();
        if (node.key() != null) {
            // Material occurrences use the exact same icon, amount, body and frame as the outer compact tree.
            CompactTreeRenderer.drawNode(graphics, graph, node, box, selected, adjacent, cameraX, cameraY, zoom);
            if (detail.role() == Role.RETAINED && detail.amount().isZero()) {
                graphics.flush();
                GraphDraw.fill(graphics, cameraX + box.x() * zoom, cameraY + box.y() * zoom,
                    cameraX + (box.x() + box.width()) * zoom, cameraY + (box.y() + box.height()) * zoom,
                    0x66434a51);
            }
            CompactTreeRenderer.drawRowFrame(graphics, box.x(), box.y(), box.x() + box.width(),
                box.y() + box.height(), cameraX, cameraY, zoom);
            if (detail.role() == Role.SEED && detail.balance() != null
                    && detail.balance().seedShortfall() != null && detail.balance().seedShortfall().signum() > 0) {
                CompactTreeRenderer.drawNodeFrame(graphics, box, MISSING, cameraX, cameraY, zoom);
            }
            return;
        }
        int accent = color(detail.role());
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cameraX + box.x() * zoom, cameraY + box.y() * zoom, 0);
        pose.scale(zoom, zoom, 1);
        try {
            float w = box.width(), h = box.height();
            if (detail.role() == Role.STEP) {
                graphics.flush();
                ECOIcon.CRAFT_HAMMER.blit(graphics, (int) (w - ECOIcon.CRAFT_HAMMER.width) / 2,
                    (int) (h - ECOIcon.CRAFT_HAMMER.height) / 2, 0);
                return;
            }
            if (detail.role() == Role.UNKNOWN) {
                centered(graphics, Minecraft.getInstance().font, "?", w / 2, (h - 9) / 2, w - 2, MISSING, 1.1f);
                return;
            }
            GraphDraw.fill(graphics, 1, 1, w - 1, h - 1, 0xff252d35);
            GraphDraw.fill(graphics, 2, 2, w - 2, 4, 0xff626b75);
            GraphDraw.fill(graphics, 2, 4, 4, h - 2, 0xff464e58);
            GraphDraw.fill(graphics, 2, h - 4, w - 2, h - 2, 0xff101318);
            GraphDraw.fill(graphics, w - 4, 4, w - 2, h - 2, 0xff101318);
            frame(graphics, 0, 0, w, h, accent, false);
            if (selected || adjacent) frame(graphics, -2, -2, w + 2, h + 2,
                selected ? OUTPUT : STEP, false);
            Font font = Minecraft.getInstance().font;
            if (detail.role() == Role.SEED) {
                centered(graphics, font, recursionText("no_seed").getString(), w / 2, h / 2, w - 8, MUTED, 0.8f);
            } else {
                graphics.flush();
                ECOIcon.ARROW_RIGHT.blit(graphics, 4, 2, 0);
                text(graphics, font, title(node).getString(), 24, (h - 8) / 2, w - 29, accent, 0.85f);
            }
        } finally {
            graphics.flush();
            pose.popPose();
        }
    }

    static void drawLink(GuiGraphics graphics, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
            ClientCraftingGraph.Link link, float cameraX, float cameraY, float zoom, @Nullable Integer highlight) {
        var route = layout.edgePoints(link);
        if (route.size() < 2) return;
        var fromRole = graph.nodes().get(link.fromId()).recursion().role();
        var toRole = graph.nodes().get(link.toId()).recursion().role();
        boolean external = fromRole == Role.INPUT;
        int accent = highlight != null ? highlight : external ? INPUT
            : toRole == Role.OUTPUT ? OUTPUT : toRole == Role.RETAINED ? RETAINED : 0xff8293a4;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cameraX, cameraY, 0);
        pose.scale(zoom, zoom, 1);
        try {
            for (int i = 1; i < route.size(); i++) {
                var a = route.get(i - 1); var b = route.get(i);
                if (external) GraphDraw.dashedLine(graphics, a.x(), a.y(), b.x(), b.y(), accent, 0.7f);
                else GraphDraw.line(graphics, a.x(), a.y(), b.x(), b.y(), accent, 0.6f);
            }
            GraphRenderer.drawArrowHead(graphics, route, accent, 0, 0, 1);
        } finally { pose.popPose(); }
    }

    static Component title(ClientCraftingGraph.Node node) {
        var detail = node.recursion();
        return switch (detail.role()) {
            case STEP -> detail.layer().folded() ? recursionText("folded_layer", (detail.level() + 1) / 2)
                : recursionText("row", (detail.level() + 1) / 2);
            case STOCK -> recursionText(detail.level() == 0 ? "initial_stock" : "stock");
            case OUTPUT -> recursionText(node.cycle().recursion().complete() ? "output" : "target");
            default -> recursionText(detail.role().name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    static List<Component> tooltip(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        List<Component> lines = node.key() == null ? new ArrayList<>() : new ArrayList<>(AEKeyRendering.getTooltip(node.key()));
        var detail = node.recursion();
        if (detail.role() != Role.STEP) lines.add(title(node).copy().withStyle(ChatFormatting.GOLD));
        if (node.key() != null) {
            lines.add(recursionText("amount", exact(node.key(), detail.amount())));
            if (detail.role() == Role.RETAINED && detail.amount().isZero()) lines.add(recursionText("depleted"));
            if (detail.delta() != null) lines.add(recursionText(detail.role() == Role.RETAINED ? "net_value" : "layer_delta",
                signedExact(node.key(), detail.delta())));
            if (detail.role() == Role.SEED && detail.balance() != null && detail.balance().seedShortfall() != null) {
                lines.add(recursionText("seed_available", exact(node.key(), detail.balance().seed().subtract(detail.balance().seedShortfall()))));
                if (detail.balance().seedShortfall().signum() > 0) {
                    lines.add(recursionText("seed_missing", exact(node.key(), detail.balance().seedShortfall())));
                }
            }
            if (detail.role() == Role.RETAINED && detail.balance() != null) {
                lines.add(recursionText("seed_returned", exact(node.key(), detail.amount().min(detail.balance().seed()).max(PlannerAmount.ZERO))));
            }
        }
        if (detail.layer() != null) {
            if (!lines.isEmpty()) lines.add(Component.empty());
            lines.addAll(layerTooltip(graph, detail.layer(),
                (detail.level() + (detail.role() == Role.INPUT ? 2 : 1)) / 2));
        }
        if (!node.cycle().recursion().complete()) lines.add(recursionText("unknown_hint").copy().withStyle(ChatFormatting.RED));
        else if (!node.cycle().status().equals("SOLVED") && !node.cycle().status().equals("NOT_REQUIRED")) {
            lines.add(recursionText("conditional").copy().withStyle(ChatFormatting.RED));
        }
        if (detail.role() == Role.OUTPUT) lines.add(recursionText("return_to_plan").copy().withStyle(ChatFormatting.GREEN));
        return lines;
    }

    static List<Component> layerTooltip(ClientCraftingGraph graph, RecursionLayer layer, int level) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty().append(recursionText("row", level).copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal(" │ ").withStyle(ChatFormatting.DARK_GRAY))
            .append(recursionText(layer.folded() ? "merged_node" : "processing_node").copy().withStyle(ChatFormatting.LIGHT_PURPLE)));
        lines.add(tooltipDivider());
        if (layer.folded()) lines.add(recursionText("folded_hint").copy().withStyle(ChatFormatting.GRAY));
        for (var firing : layer.patternTimes().stream().limit(8).toList()) {
            lines.add(Component.empty());
            lines.add(Component.literal("◆ ").withStyle(ChatFormatting.AQUA)
                .append(recursionText("processing_recipe"))
                .append(Component.literal(" × ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(DisplayNumbers.grouped(firing.plannerAmount().toBigInteger())).withStyle(ChatFormatting.WHITE)));
            var pattern = graph.source().patterns().stream().filter(value -> value.patternNodeId() == firing.patternNodeId())
                .findFirst().orElse(null);
            lines.add(Component.literal("  ").withStyle(ChatFormatting.WHITE).append(patternOutputName(graph, pattern, firing.patternNodeId())));
            String identity = pattern == null ? recursionText("pattern_id", firing.patternNodeId()).getString() : pattern.displayIdentity();
            lines.add(Component.literal("  " + identity).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.empty());
        lines.add(Component.literal("◆ ").withStyle(ChatFormatting.GOLD).append(recursionText("external_additions")));
        if (layer.addedInputs().isEmpty()) lines.add(recursionText("no_external").copy().withStyle(ChatFormatting.GRAY));
        for (var value : layer.addedInputs().stream().limit(8).toList()) {
            lines.add(Component.literal("  ").withStyle(ChatFormatting.GRAY)
                .append(value.key().getDisplayName().copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" × ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(exact(value.key(), value.plannerAmount()))
                    .withStyle(value.key() instanceof AEFluidKey ? ChatFormatting.AQUA : ChatFormatting.WHITE)));
        }
        if (layer.patternTimes().size() > 8 || layer.addedInputs().size() > 8) {
            lines.add(recursionText("tooltip_limit").copy().withStyle(ChatFormatting.GRAY));
        }
        if (layer.folded()) {
            lines.add(tooltipDivider());
            lines.add(Component.literal("✔ ").withStyle(ChatFormatting.GREEN)
                .append(recursionText("merged_summary").copy().withStyle(ChatFormatting.GRAY)));
        }
        return lines;
    }

    private static Component patternOutputName(ClientCraftingGraph graph, @Nullable PatternNode pattern, int id) {
        if (pattern != null) {
            for (var output : pattern.outputs()) {
                var material = graph.source().nodes().stream().filter(node -> node.nodeId() == output.materialNodeId())
                    .findFirst().orElse(null);
                if (material != null) return material.key().getDisplayName().copy().withStyle(ChatFormatting.WHITE);
            }
        }
        return recursionText("pattern_id", id);
    }

    private static Component tooltipDivider() {
        return Component.literal("━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY);
    }

    static String exact(AEKey key, PlannerAmount value) {
        String unit = key.getUnitSymbol();
        return (value.signum() < 0 ? "−" : "")
            + BigNumberFormatter.format(value.abs().toBigInteger(), Math.max(1, key.getAmountPerUnit()), true)
            + (unit == null ? "" : " " + unit);
    }
    static String signedExact(AEKey key, PlannerAmount value) { return (value.signum() > 0 ? "+" : "") + exact(key, value); }

    static void text(GuiGraphics graphics, Font font, String text, float x, float y, float width, int color, float maxScale) {
        float scale = Math.min(maxScale, width / Math.max(1, font.width(text)));
        if (scale <= 0) return;
        // Keep text readable; the full label remains available in the tooltip and detail panel.
        if (scale < 0.6f) {
            scale = 0.6f;
            text = font.plainSubstrByWidth(text, Math.max(0, (int) (width / scale) - font.width("…"))) + "…";
        }
        var pose = graphics.pose();
        pose.pushPose(); pose.translate(x, y, 0); pose.scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }
    private static void centered(GuiGraphics graphics, Font font, String text, float centerX, float y, float width, int color, float scale) {
        float fitted = Math.min(scale, width / Math.max(1, font.width(text)));
        text(graphics, font, text, centerX - Math.min(width, font.width(text) * fitted) / 2, y, width, color, scale);
    }
    private static void frame(GuiGraphics graphics, float left, float top, float right, float bottom, int color, boolean dashed) {
        if (dashed) {
            GraphDraw.dashedLine(graphics, left, top, right, top, color, 0.7f);
            GraphDraw.dashedLine(graphics, right, top, right, bottom, color, 0.7f);
            GraphDraw.dashedLine(graphics, right, bottom, left, bottom, color, 0.7f);
            GraphDraw.dashedLine(graphics, left, bottom, left, top, color, 0.7f);
        } else {
            GraphDraw.line(graphics, left, top, right, top, color, 0.5f);
            GraphDraw.line(graphics, right, top, right, bottom, color, 0.5f);
            GraphDraw.line(graphics, right, bottom, left, bottom, color, 0.5f);
            GraphDraw.line(graphics, left, bottom, left, top, color, 0.5f);
        }
    }
}
