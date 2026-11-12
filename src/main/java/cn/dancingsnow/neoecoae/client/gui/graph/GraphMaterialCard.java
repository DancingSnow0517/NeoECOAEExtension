package cn.dancingsnow.neoecoae.client.gui.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.client.gui.ClientModularUITooltipComponent;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.MaterialStatus;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard;
import java.math.BigInteger;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/** Material details and hover content composed from the shared card rows, measured once per selection. */
final class GraphMaterialCard {
    private static final int PRIMARY = 0xffecf3f8, LABEL = 0xffa9c5d6, MUTED = 0xff809bab;
    private static final int GOLD = 0xffffc45b, CYAN = 0xff63dce9, GREEN = 0xff76dfa0, RED = 0xffff7a7a;
    private static final int DIVIDER = 0xff39464f, BACKGROUND = 0xff0f1419;
    private static final int PADDING = 8;
    private final ClientCraftingGraph graph;
    private final ClientCraftingGraph.Node node;
    private final Language language = Language.getInstance();
    private final Font font;
    private final ClientModularUITooltipComponent content;
    private final int width, height;

    GraphMaterialCard(ClientCraftingGraph graph, ClientCraftingGraph.Node node, Font font, boolean details) {
        this.graph = graph;
        this.node = node;
        this.font = font;
        content = new ClientModularUITooltipComponent(builder(graph, node, details).build());
        width = content.getWidth(font) + PADDING * 2;
        height = content.getHeight() + PADDING * 2;
    }

    static boolean supports(@Nullable ClientCraftingGraph.Node node) {
        return node != null
            && (node.kind() == ClientCraftingGraph.Kind.MATERIAL || node.kind() == ClientCraftingGraph.Kind.REFERENCE)
            && node.material() != null && node.recursion() == null;
    }

    boolean matches(ClientCraftingGraph graph, ClientCraftingGraph.Node node, Font font) {
        return this.graph == graph && this.node == node && this.font == font && language == Language.getInstance();
    }

    static CraftingListCard.Builder builder(ClientCraftingGraph graph, ClientCraftingGraph.Node node, boolean details) {
        var material = node.material();
        var key = material.key();
        var requested = value(key, material.requestedBigInteger(), !details, PRIMARY);
        var inventory = value(key, material.fromInventoryBigInteger(), !details,
            material.fromInventoryBigInteger().signum() == 0 ? MUTED : CYAN);
        var toCraft = value(key, material.toCraftBigInteger(), !details,
            material.toCraftBigInteger().signum() == 0 ? MUTED : GOLD);
        var missing = value(key, material.missingBigInteger(), !details,
            material.missingBigInteger().signum() == 0 ? GREEN : RED);
        var consumed = value(key, material.consumedBigInteger(), !details,
            material.consumedBigInteger().signum() == 0 ? MUTED : PRIMARY);
        var produced = value(key, material.producedBigInteger(), !details,
            material.producedBigInteger().signum() == 0 ? MUTED : CYAN);
        var title = key.getDisplayName().copy().withStyle(style -> style.withColor(GOLD & 0xffffff));
        int statusColor = statusColor(material.status());
        var status = Component.literal(material.status() == MaterialStatus.SATISFIED ? "✓ " : "○ ")
            .append(Component.translatable("gui.neoecoae.crafting_graph.status." + material.status().name().toLowerCase(Locale.ROOT)))
            .withStyle(style -> style.withColor(statusColor & 0xffffff));
        var card = CraftingListCard.builder().minimumWidth(details ? 272 : 150);
        if (details) card.header(CraftingListCard.Glyph.SPARKLE, title, label("request"), MUTED);
        else card.header(null, title, Component.empty(), CYAN, CraftingListCard.Glyph.CUBE);
        if (node.kind() == ClientCraftingGraph.Kind.REFERENCE) {
            card.space(6).row(null, 0,
                Component.translatable("gui.neoecoae.crafting_graph.reference.description"), Component.empty(), MUTED);
        }
        card.space(8).divider(DIVIDER).space(6);
        if (details) {
            card.metricColumns(label("requested"), requested, PRIMARY, label("inventory"), inventory, MUTED)
                .space(8)
                .metricColumns(label("to_craft"), toCraft, GOLD, label("missing"), missing, GREEN)
                .space(8).divider(DIVIDER).space(6)
                .row(null, 0, label("consumed").append(" ").append(consumed),
                    label("produced").append(" ").append(produced), CYAN)
                .space(6).divider(DIVIDER).space(6)
                .row(null, 0, status, label("source_patterns", DisplayNumbers.grouped(graph.source().patterns().size())), MUTED);
        } else {
            card.row(null, 0, label("requested"), requested, PRIMARY).space(7)
                .row(null, 0, label("inventory"), inventory, MUTED).space(7)
                .row(null, 0, label("to_craft"), toCraft, GOLD).space(7)
                .row(null, 0, label("missing"), missing, GREEN)
                .space(7).divider(DIVIDER).space(6)
                .row(null, 0, label("consumed"), consumed, MUTED).space(7)
                .row(null, 0, label("produced"), produced, CYAN)
                .space(7).divider(DIVIDER).space(6)
                .row(null, 0, label("status"), status, statusColor);
        }
        return card;
    }

    void renderDetails(GuiGraphics graphics, int screenWidth, int contentBottom, int top) {
        float scale = fit(Math.min(242, screenWidth - 16), contentBottom - top - 8);
        draw(graphics, screenWidth - 8 - (int) Math.ceil(width * scale), top, scale, 300, 0xff556675);
    }

    boolean containsDetails(double mouseX, double mouseY, int screenWidth, int contentBottom, int top) {
        float scale = fit(Math.min(242, screenWidth - 16), contentBottom - top - 8);
        int drawnWidth = (int) Math.ceil(width * scale), drawnHeight = (int) Math.ceil(height * scale);
        return mouseX >= screenWidth - 8 - drawnWidth && mouseX < screenWidth - 8
            && mouseY >= top && mouseY < top + drawnHeight;
    }

    void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        float scale = fit(screenWidth - 8, screenHeight - 8);
        int drawnWidth = (int) Math.ceil(width * scale), drawnHeight = (int) Math.ceil(height * scale);
        var position = DefaultTooltipPositioner.INSTANCE.positionTooltip(screenWidth, screenHeight,
            mouseX, mouseY, drawnWidth, drawnHeight);
        int left = Math.max(4, Math.min(screenWidth - drawnWidth - 4, position.x()));
        int top = Math.max(4, Math.min(screenHeight - drawnHeight - 4, position.y()));
        draw(graphics, left, top, scale, 400, 0xff796a94);
    }

    private float fit(int availableWidth, int availableHeight) {
        return Math.min(1, Math.min(Math.max(1, availableWidth) / (float) width, Math.max(1, availableHeight) / (float) height));
    }

    private void draw(GuiGraphics graphics, int left, int top, float scale, int z, int border) {
        graphics.flush();
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(left, top, z);
            pose.scale(scale, scale, 1);
            rounded(graphics, 0, 0, width, height, border);
            rounded(graphics, 1, 1, width - 2, height - 2, BACKGROUND);
            graphics.flush();
            content.renderImage(font, PADDING, PADDING, graphics);
        } finally {
            graphics.flush();
            pose.popPose();
        }
    }

    private static void rounded(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 3, y, x + width - 3, y + height, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        graphics.fill(x, y + 3, x + width, y + height - 3, color);
    }

    private static MutableComponent label(String key, Object... args) {
        return Component.translatable("gui.neoecoae.crafting_graph.card." + key, args).withStyle(style -> style.withColor(LABEL & 0xffffff));
    }

    private static Component value(AEKey key, BigInteger amount, boolean full, int color) {
        String unit = key.getUnitSymbol();
        return Component.literal(BigNumberFormatter.format(amount, key.getAmountPerUnit(), full)
            + (unit == null ? "" : " " + unit)).withStyle(style -> style.withColor(color & 0xffffff));
    }

    private static int statusColor(MaterialStatus status) {
        return switch (status) {
            case SATISFIED -> GREEN;
            case CRAFTING, CYCLE -> GOLD;
            case MISSING, UNSUPPORTED -> RED;
        };
    }
}
