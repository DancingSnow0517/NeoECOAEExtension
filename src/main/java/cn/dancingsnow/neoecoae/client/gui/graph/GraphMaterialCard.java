package cn.dancingsnow.neoecoae.client.gui.graph;

import appeng.api.stacks.AEKey;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.CraftingGraphProgress;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.MaterialStatus;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard;
import java.math.BigInteger;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/** Material details and hover content composed from the shared card rows, measured once per selection. */
final class GraphMaterialCard {
    /** The shared card rows draw shadowed light text, so material cards stay dark in both themes. */
    private static final GraphTheme THEME = GraphTheme.DARK;
    private static final int PADDING = 8;
    private final ClientCraftingGraph graph;
    private final ClientCraftingGraph.Node node;
    private final Language language = Language.getInstance();
    private final Font font;
    private final @Nullable CraftingGraphProgress progress;
    private final ClientTooltipComponent content;
    private final int width, height;

    GraphMaterialCard(ClientCraftingGraph graph, ClientCraftingGraph.Node node, Font font, boolean details,
            @Nullable CraftingGraphProgress progress) {
        this.graph = graph;
        this.node = node;
        this.font = font;
        this.progress = progress;
        content = ClientTooltipComponent.create(builder(graph, node, details, progress).buildComponent());
        width = content.getWidth(font) + PADDING * 2;
        height = content.getHeight(font) + PADDING * 2;
    }

    static boolean supports(@Nullable ClientCraftingGraph.Node node) {
        return node != null
            && (node.kind() == ClientCraftingGraph.Kind.MATERIAL || node.kind() == ClientCraftingGraph.Kind.REFERENCE)
            && node.material() != null && node.recursion() == null;
    }

    boolean matches(ClientCraftingGraph graph, ClientCraftingGraph.Node node, Font font,
            @Nullable CraftingGraphProgress progress) {
        return this.graph == graph && this.node == node && this.font == font && this.progress == progress
            && language == Language.getInstance();
    }

    static CraftingListCard.Builder builder(ClientCraftingGraph graph, ClientCraftingGraph.Node node, boolean details) {
        return builder(graph, node, details, null);
    }

    static CraftingListCard.Builder builder(ClientCraftingGraph graph, ClientCraftingGraph.Node node, boolean details,
            @Nullable CraftingGraphProgress progress) {
        if (progress != null && progress.amounts().containsKey(node.material().nodeId()))
            return liveBuilder(node, details, progress);
        var theme = THEME;
        int primary = theme.text(), muted = theme.faint(), gold = theme.pending(), cyan = theme.accent();
        int green = theme.stock(), red = theme.missing(), divider = theme.line();
        var material = node.material();
        var key = material.key();
        var requested = value(key, material.requestedBigInteger(), !details, primary);
        var inventory = value(key, material.fromInventoryBigInteger(), !details,
            material.fromInventoryBigInteger().signum() == 0 ? muted : cyan);
        var toCraft = value(key, material.toCraftBigInteger(), !details,
            material.toCraftBigInteger().signum() == 0 ? muted : gold);
        var missing = value(key, material.missingBigInteger(), !details,
            material.missingBigInteger().signum() == 0 ? green : red);
        var consumed = value(key, material.consumedBigInteger(), !details,
            material.consumedBigInteger().signum() == 0 ? muted : primary);
        var produced = value(key, material.producedBigInteger(), !details,
            material.producedBigInteger().signum() == 0 ? muted : cyan);
        var title = key.getDisplayName().copy().withStyle(style -> style.withColor(gold & 0xffffff));
        int statusColor = statusColor(theme, material.status());
        var status = Component.literal(material.status() == MaterialStatus.SATISFIED ? "✓ " : "○ ")
            .append(Component.translatable("gui.neoecoae.crafting_graph.status." + material.status().name().toLowerCase(Locale.ROOT)))
            .withStyle(style -> style.withColor(statusColor & 0xffffff));
        var card = CraftingListCard.builder().minimumWidth(details ? 272 : 150);
        if (details) card.header(CraftingListCard.Glyph.SPARKLE, title, label("request"), muted);
        else card.header(null, title, Component.empty(), cyan, CraftingListCard.Glyph.CUBE);
        if (node.kind() == ClientCraftingGraph.Kind.REFERENCE) {
            card.space(6).row(null, 0,
                Component.translatable("gui.neoecoae.crafting_graph.reference.description"), Component.empty(), muted);
        }
        card.space(8).divider(divider).space(6);
        if (details) {
            card.metricColumns(label("requested"), requested, primary, label("inventory"), inventory, muted)
                .space(8)
                .metricColumns(label("to_craft"), toCraft, gold, label("missing"), missing, green)
                .space(8).divider(divider).space(6)
                .row(null, 0, label("consumed").append(" ").append(consumed),
                    label("produced").append(" ").append(produced), cyan)
                .space(6).divider(divider).space(6)
                .row(null, 0, status, label("source_patterns", DisplayNumbers.grouped(graph.source().patterns().size())), muted);
        } else {
            card.row(null, 0, label("requested"), requested, primary).space(7)
                .row(null, 0, label("inventory"), inventory, muted).space(7)
                .row(null, 0, label("to_craft"), toCraft, gold).space(7)
                .row(null, 0, label("missing"), missing, green)
                .space(7).divider(divider).space(6)
                .row(null, 0, label("consumed"), consumed, muted).space(7)
                .row(null, 0, label("produced"), produced, cyan)
                .space(7).divider(divider).space(6)
                .row(null, 0, label("status"), status, statusColor);
        }
        return card;
    }

    private static CraftingListCard.Builder liveBuilder(ClientCraftingGraph.Node node, boolean details,
            CraftingGraphProgress progress) {
        var material = node.material();
        var amounts = progress.amounts().get(material.nodeId());
        var key = material.key();
        boolean complete = progress.completed().contains(material.nodeId());
        int color = progress.cancelled() ? THEME.missing() : complete ? THEME.stock() : THEME.pending();
        String state = progress.cancelled() ? "gui.neoecoae.crafting_graph.status.cancelled"
            : progress.paused() ? "gui.neoecoae.cpu.status.suspended"
            : complete ? "gui.neoecoae.crafting_graph.status.satisfied"
            : "gui.neoecoae.computation.ui.card.state." + (amounts.active().signum() > 0 ? "crafting" : "scheduled");
        var card = CraftingListCard.builder().minimumWidth(details ? 272 : 150)
            .header(CraftingListCard.Glyph.CUBE, key.getDisplayName(), Component.empty(), THEME.accent())
            .space(8).divider(THEME.line()).space(6)
            .row(null, 0, label("requested"), value(key, material.requestedBigInteger(), !details, THEME.text()), THEME.text())
            .space(7).row(null, 0, Component.translatable("gui.neoecoae.computation.ui.card.stored"),
                value(key, amounts.stored(), !details, THEME.stock()), THEME.muted())
            .space(7).row(null, 0, Component.translatable("gui.neoecoae.computation.ui.card.crafting"),
                value(key, amounts.active(), !details, THEME.accent()), THEME.accent())
            .space(7).row(null, 0, Component.translatable("gui.neoecoae.computation.ui.card.scheduled"),
                value(key, amounts.pending(), !details, THEME.pending()), THEME.pending())
            .space(7).row(null, 0, Component.translatable("gui.neoecoae.big_order.completed"),
                value(key, amounts.produced(), !details, THEME.stock()), THEME.stock())
            .space(7).divider(THEME.line()).space(6)
            .row(null, 0, label("status"), Component.translatable(state), color);
        return card;
    }

    void renderDetails(GuiGraphicsExtractor graphics, int screenWidth, int contentBottom, int top) {
        float scale = fit(Math.min(242, screenWidth - 16), contentBottom - top - 8);
        draw(graphics, screenWidth - 8 - (int) Math.ceil(width * scale), top, scale, 300);
    }

    boolean containsDetails(double mouseX, double mouseY, int screenWidth, int contentBottom, int top) {
        float scale = fit(Math.min(242, screenWidth - 16), contentBottom - top - 8);
        int drawnWidth = (int) Math.ceil(width * scale), drawnHeight = (int) Math.ceil(height * scale);
        return mouseX >= screenWidth - 8 - drawnWidth && mouseX < screenWidth - 8
            && mouseY >= top && mouseY < top + drawnHeight;
    }

    void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        float scale = fit(screenWidth - 8, screenHeight - 8);
        int drawnWidth = (int) Math.ceil(width * scale), drawnHeight = (int) Math.ceil(height * scale);
        var position = DefaultTooltipPositioner.INSTANCE.positionTooltip(screenWidth, screenHeight,
            mouseX, mouseY, drawnWidth, drawnHeight);
        int left = Math.max(4, Math.min(screenWidth - drawnWidth - 4, position.x()));
        int top = Math.max(4, Math.min(screenHeight - drawnHeight - 4, position.y()));
        draw(graphics, left, top, scale, 400);
    }

    private float fit(int availableWidth, int availableHeight) {
        return Math.min(1, Math.min(Math.max(1, availableWidth) / (float) width, Math.max(1, availableHeight) / (float) height));
    }

    private void draw(GuiGraphicsExtractor graphics, int left, int top, float scale, int z) {
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            graphics.nextStratum();
            pose.translate(left, top);
            pose.scale(scale, scale);
            rounded(graphics, 0, 0, width, height, THEME.line());
            rounded(graphics, 1, 1, width - 2, height - 2, THEME.panel());

            content.extractImage(font, PADDING, PADDING, width, height, graphics);
        } finally {

            pose.popMatrix();
        }
    }

    private static void rounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 3, y, x + width - 3, y + height, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        graphics.fill(x, y + 3, x + width, y + height - 3, color);
    }

    private static MutableComponent label(String key, Object... args) {
        return Component.translatable("gui.neoecoae.crafting_graph.card." + key, args).withStyle(style -> style.withColor(THEME.muted() & 0xffffff));
    }

    private static Component value(AEKey key, BigInteger amount, boolean full, int color) {
        String unit = key.getUnitSymbol();
        return Component.literal(BigNumberFormatter.format(amount, key.getAmountPerUnit(), full)
            + (unit == null ? "" : " " + unit)).withStyle(style -> style.withColor(color & 0xffffff));
    }

    private static int statusColor(GraphTheme theme, MaterialStatus status) {
        return switch (status) {
            case SATISFIED -> theme.stock();
            case CRAFTING, CYCLE -> theme.pending();
            case MISSING, UNSUPPORTED -> theme.missing();
        };
    }
}
