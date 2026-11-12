package cn.dancingsnow.neoecoae.client.gui.crafting;

import appeng.api.stacks.AEKey;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.client.gui.style.Blitter;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.LoggerFactory;

/** Exports the report's native AE2 table as one PNG, using bounded render targets. */
final class ECOCraftingListImageExport {
    // AE2's AbstractTableRenderer uses three 67x22 cells with one-pixel separators.
    private static final int COLUMNS = 3;
    private static final int ROW_HEIGHT = 23;
    private static final int TABLE_WIDTH = 205;
    // Adjacent groups share the same one-pixel cell divider.
    private static final int PANEL_STRIDE = TABLE_WIDTH - 1;
    private static final int TABLE_X = 9;
    private static final int TABLE_TEXTURE_Y = 27;
    private static final int VISIBLE_ROWS = 7;
    private static final int WIDTH = 222;
    private static final int MARGIN = 9;
    private static final int MAX_STRIP_HEIGHT = 1024;
    // Render directly at higher resolution to preserve detail in the table's small text and icons.
    private static final int SCALE = 4;
    private static final long MAX_IMAGE_PIXELS = 64L * 1024 * 1024;
    private static final int LINE_HEIGHT = 11;
    private static final int CARD_WIDTH = TABLE_WIDTH - 1;
    // The report's light outer panel needs dark text; inset table cells need light text.
    private static final int ACCENT = 0xFF321B40;
    private static final int TEXT = 0xFF181C28;
    private static final int SECONDARY = 0xFF232633;
    private static final int PANEL_TEXT = 0xFFF2F3F7;
    private static final int REQUEST = 0xFF493000;
    private static final int CYAN = 0xFF55F4ED;
    private static final int GOLD = 0xFFFFC857;

    record Entry(AEKey key, List<Component> description, int overlay, Component type) {}
    record Header(Component target, Component requested, Component elapsed, Component bytes,
                  boolean partialBytes, Component patterns, Component cycles) {}
    record Strip(int firstRow, int endRow, int y, int height) {}
    record Layout(int height, List<Strip> strips) {}

    private ECOCraftingListImageExport() {}

    static void save(AEBaseScreen<?> screen, List<Entry> entries, Header header) {
        if (entries.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        try {
            int rows = Math.toIntExact(((long) entries.size() + COLUMNS - 1) / COLUMNS);
            List<Entry> legend = legendEntries(entries);
            int panels = Math.max(legend.isEmpty() ? 1 : 2, panelCount(rows));
            int headerHeight = MARGIN + Math.max(cardHeight(client.font, header), legendHeight(client.font, legend)) + 8;
            int maxHeight = Math.min(MAX_STRIP_HEIGHT, RenderSystem.getDevice().getMaxTextureSize() / SCALE);
            int rowsPerPanel = (rows + panels - 1) / panels;
            int entriesPerPanel = Math.multiplyExact(rowsPerPanel, COLUMNS);
            Layout layout = layout(entriesPerPanel, headerHeight, maxHeight);
            long imageWidth = WIDTH + (long) (panels - 1) * PANEL_STRIDE;
            if (imageWidth * SCALE * layout.height() * SCALE > MAX_IMAGE_PIXELS) {
                throw new IllegalArgumentException(Component.translatable(
                    "gui.neoecoae.crafting_report.export_too_large").getString());
            }
            Path directory = client.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(directory);
            Path file = directory.resolve("neoecoae-plan-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ".png");
            renderImage(client, screen, entries, header, legend, headerHeight, layout, panels, entriesPerPanel, file)
                .whenComplete((ignored, failure) -> client.execute(() -> {
                    if (failure != null) { reportFailure(client, failure); return; }
                    Component link = Component.literal(file.getFileName().toString())
                        .withStyle(ChatFormatting.UNDERLINE).withStyle(style -> style.withClickEvent(
                            new ClickEvent.OpenFile(file.toAbsolutePath().toString())));
                    if (client.player != null) client.player.sendSystemMessage(Component.translatable(
                        "gui.neoecoae.crafting_report.export_saved", link));
                }));
        } catch (Exception exception) {
            LoggerFactory.getLogger("neoecoae").warn("Could not export crafting list image", exception);
            reportFailure(client, exception);
        }
    }

    /** Keep the row count as close as possible to the total number of columns (three per panel). */
    private static int panelCount(int rows) {
        int lower = Math.max(1, (int) Math.sqrt(rows / (double) COLUMNS));
        int upper = lower + 1;
        long lowerDifference = Math.abs(((long) rows + lower - 1) / lower - (long) lower * COLUMNS);
        long upperDifference = Math.abs(((long) rows + upper - 1) / upper - (long) upper * COLUMNS);
        return upperDifference < lowerDifference ? upper : lower;
    }

    /** Whole rows are contiguous in the final image; only its first strip has a header. */
    static Layout layout(int entryCount, int headerHeight, int maxStripHeight) {
        if (entryCount <= 0) return new Layout(0, List.of());
        if (headerHeight < 0 || (long) headerHeight + ROW_HEIGHT + MARGIN > maxStripHeight) {
            throw new IllegalArgumentException("Export header exceeds render target height");
        }
        int rows = Math.toIntExact(((long) entryCount + COLUMNS - 1) / COLUMNS);
        int imageHeight = Math.addExact(Math.addExact(headerHeight, Math.multiplyExact(rows, ROW_HEIGHT)), MARGIN);
        List<Strip> strips = new ArrayList<>();
        int y = 0;
        for (int first = 0; first < rows;) {
            int top = first == 0 ? headerHeight : 0;
            int count = Math.min(rows - first, (maxStripHeight - top - MARGIN) / ROW_HEIGHT);
            int end = first + count;
            int height = top + count * ROW_HEIGHT + (end == rows ? MARGIN : 0);
            strips.add(new Strip(first, end, y, height));
            y += height;
            first = end;
        }
        return new Layout(imageHeight, List.copyOf(strips));
    }

    private static void reportFailure(Minecraft client, Throwable failure) {
        LoggerFactory.getLogger("neoecoae").warn("Could not export crafting list image", failure);
        if (client.player != null) client.player.sendSystemMessage(
            Component.translatable("screenshot.failure", failure.getMessage()));
    }

    private static java.util.concurrent.CompletableFuture<Void> renderImage(Minecraft client, AEBaseScreen<?> screen,
            List<Entry> entries, Header header, List<Entry> legend, int headerHeight, Layout layout,
            int panels, int entriesPerPanel, Path file) {
        var state = new net.minecraft.client.renderer.state.gui.GuiRenderState();
        var nodes = new net.minecraft.client.renderer.SubmitNodeStorage();
        var buffers = client.renderBuffers();
        var features = new net.minecraft.client.renderer.feature.FeatureRenderDispatcher(nodes, client.getModelManager(),
            buffers.bufferSource(), client.getAtlasManager(), buffers.outlineBufferSource(), buffers.crumblingBufferSource(),
            client.font, client.gameRenderer.getGameRenderState());
        var renderer = new net.minecraft.client.gui.render.GuiRenderer(state, buffers.bufferSource(), nodes, features, List.of());
        int imageWidth = Math.addExact(WIDTH, Math.multiplyExact(panels - 1, PANEL_STRIDE));
        var image = new NativeImage(Math.multiplyExact(imageWidth, SCALE), Math.multiplyExact(layout.height(), SCALE), false);
        var result = new java.util.concurrent.CompletableFuture<Void>();
        // Only one GPU readback and one bounded target are live at a time; the render thread never waits on a future.
        class Composer {
            int panel, stripIndex;
            void next() {
                if (panel == panels) {
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try { image.writeToFile(file); }
                        catch (IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
                    }, net.minecraft.util.Util.ioPool()).whenComplete((ignored, failure) -> client.execute(() -> finish(failure)));
                    return;
                }
                Strip strip = layout.strips().get(stripIndex);
                int first = Math.min(entries.size(), Math.multiplyExact(panel, entriesPerPanel));
                int end = (int) Math.min(entries.size(), (long) first + entriesPerPanel);
                renderStrip(client, screen, state, renderer, entries.subList(first, end), panel == 0 ? header : null, panel == 1 ? legend : List.of(), entries.size(),
                    headerHeight, strip, panel == 0, panel == panels - 1, stripIndex == layout.strips().size() - 1).whenComplete((rendered, failure) ->
                    client.execute(() -> {
                        if (failure != null) { finish(failure); return; }
                        try (rendered) {
                            int sourceX = panel == 0 ? 0 : TABLE_X - 1;
                            int sourceEndX = panel == panels - 1 ? WIDTH : TABLE_X - 1 + PANEL_STRIDE;
                            rendered.copyRect(image, sourceX * SCALE, 0, (panel * PANEL_STRIDE + sourceX) * SCALE,
                                strip.y() * SCALE, (sourceEndX - sourceX) * SCALE, rendered.getHeight(), false, false);
                        } catch (RuntimeException problem) { finish(problem); return; }
                        if (++stripIndex == layout.strips().size()) { stripIndex = 0; panel++; }
                        next();
                    }));
            }
            void finish(Throwable failure) {
                try { renderer.close(); features.close(); }
                finally { image.close(); }
                if (failure == null) result.complete(null); else result.completeExceptionally(failure);
            }
        }
        new Composer().next();
        return result;
    }

    private static java.util.concurrent.CompletableFuture<NativeImage> renderStrip(Minecraft client, AEBaseScreen<?> screen,
            net.minecraft.client.renderer.state.gui.GuiRenderState state, net.minecraft.client.gui.render.GuiRenderer renderer,
            List<Entry> entries, Header header, List<Entry> legend, int materialCount, int headerHeight, Strip strip,
            boolean leftEdge, boolean rightEdge, boolean last) {
        var result = new java.util.concurrent.CompletableFuture<NativeImage>();
        var target = new TextureTarget("ECO crafting report", WIDTH * SCALE, strip.height() * SCALE, true);
        var previousTarget = client.getMainRenderTarget();
        var access = (cn.dancingsnow.neoecoae.mixins.client.accessor.MinecraftRenderTargetAccessor) client;
        var window = client.gameRenderer.getGameRenderState().windowRenderState;
        int width = window.width, height = window.height, scale = window.guiScale;
        var projection = RenderSystem.getProjectionMatrixBuffer();
        var projectionType = RenderSystem.getProjectionType();
        try {
            access.neoecoae$setMainRenderTarget(target);
            window.width = target.width; window.height = target.height; window.guiScale = SCALE;
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
            var graphics = new GuiGraphicsExtractor(client, state, -1, -1);
            boolean first = strip.firstRow() == 0;
            int tableY = first ? headerHeight : 0;
            renderBackground(screen.getStyle().getBackground(), graphics, strip, tableY, first, last, leftEdge, rightEdge);
            if (first && header != null) renderHeader(graphics, client.font, header, materialCount,
                screen.getStyle().getBackground());
            if (first && !legend.isEmpty()) renderLegend(screen, graphics, client.font, legend);
            new ExportTable(screen, tableY, strip.endRow() - strip.firstRow())
                .render(graphics, -1_000_000, -1_000_000, entries, strip.firstRow());
            renderer.render(java.util.Objects.requireNonNull(RenderSystem.getShaderFog()));
            renderer.endFrame();
            Screenshot.takeScreenshot(target, rendered -> {
                target.destroyBuffers();
                result.complete(rendered);
            });
        } catch (RuntimeException failure) {
            target.destroyBuffers();
            result.completeExceptionally(failure);
        } finally {
            access.neoecoae$setMainRenderTarget(previousTarget);
            window.width = width; window.height = height; window.guiScale = scale;
            if (projection != null) RenderSystem.setProjectionMatrix(projection, projectionType);
        }
        return result;
    }

    private static List<Entry> legendEntries(List<Entry> entries) {
        // Match the visible overlay's priority, keeping one real example of each colour.
        var examples = new LinkedHashMap<Integer, Entry>();
        for (Entry entry : entries) {
            if (entry.overlay() != 0) examples.putIfAbsent(entry.overlay(), entry);
        }
        return List.copyOf(examples.values());
    }

    private static int legendHeight(Font font, List<Entry> legend) {
        int height = 36;
        for (Entry entry : legend) height += Math.max(ROW_HEIGHT, font.split(entry.type(), 116).size() * LINE_HEIGHT) + 10;
        return height;
    }

    private static void renderLegend(AEBaseScreen<?> screen, GuiGraphicsExtractor graphics, Font font, List<Entry> legend) {
        int y = MARGIN + 10;
        fitted(graphics, font, Component.translatable("gui.neoecoae.crafting_graph.recursion.legend"),
            TABLE_X + 7, y, CARD_WIDTH - 16, 1, ACCENT, false);
        y += 25;
        Blitter background = screen.getStyle().getBackground();
        for (Entry entry : legend) {
            int rowHeight = Math.max(ROW_HEIGHT, font.split(entry.type(), 116).size() * LINE_HEIGHT);
            int cellY = y + (rowHeight - ROW_HEIGHT) / 2;
            background.copy().src(background.getSrcX() + TABLE_X, background.getSrcY() + TABLE_TEXTURE_Y + ROW_HEIGHT, 67, 22)
                .dest(TABLE_X, cellY).blit(graphics);
            new ExportTable(screen, cellY, 1).render(graphics, -1_000_000, -1_000_000, List.of(entry), 0);
            wrapped(graphics, font, entry.type(), TABLE_X + 77,
                y + (rowHeight - font.split(entry.type(), 116).size() * LINE_HEIGHT) / 2, 116, TEXT, false);
            y += rowHeight + 10;
        }
    }

    private static int requestHeight(Font font, Header header) {
        return Math.max(1, Math.max(font.split(header.target(), 120).size(),
            font.split(header.requested(), CARD_WIDTH - 144).size())) * LINE_HEIGHT;
    }

    private static int valueHeight(Font font, Component value) {
        return Math.max(1, font.split(value, CARD_WIDTH - 84).size()) * LINE_HEIGHT;
    }

    private static int cardHeight(Font font, Header header) {
        return 144 + requestHeight(font, header) + valueHeight(font, header.elapsed())
            + valueHeight(font, header.bytes())
            + (header.partialBytes() ? font.split(Component.translatable(
                "gui.neoecoae.crafting_report.card.incomplete_hint"), CARD_WIDTH - 16).size() * LINE_HEIGHT + 4 : 0);
    }

    private static void renderHeader(GuiGraphicsExtractor graphics, Font font, Header header, int materialCount,
            Blitter background) {
        int x = TABLE_X - 1, y = MARGIN, width = CARD_WIDTH;
        int left = x + 8, right = x + width - 8;
        fitted(graphics, font, Component.literal("✦ ").append(Component.translatable(
            "gui.neoecoae.crafting_report.export_title").withStyle(ChatFormatting.BOLD)),
            left, y + 10, width - 78, 1, ACCENT, false);
        fitted(graphics, font, Component.literal("ME CRAFTING"), right, y + 12, 56, 0.85F, SECONDARY, true);
        graphics.fill(left, y + 30, right, y + 31, 0xFF514064);
        int rowY = y + 42;
        wrapped(graphics, font, header.target(), left, rowY, 120, TEXT, false);
        wrapped(graphics, font, header.requested(), right, rowY, width - 144, REQUEST, true);
        rowY += requestHeight(font, header) + 6;
        fitted(graphics, font, Component.translatable("gui.neoecoae.crafting_report.card.title"),
            left, rowY, width - 16, 1, SECONDARY, false);
        rowY += 14;
        int elapsedHeight = valueHeight(font, header.elapsed());
        int bytesHeight = valueHeight(font, header.bytes());
        panel(background, graphics, left, rowY, width - 16, elapsedHeight + bytesHeight + 18);
        fitted(graphics, font, Component.translatable("gui.neoecoae.crafting_report.card.elapsed"),
            left + 6, rowY + 7, 58, 1, PANEL_TEXT, false);
        wrapped(graphics, font, header.elapsed(), right - 6, rowY + 7, width - 84, CYAN, true);
        fitted(graphics, font, Component.translatable("gui.neoecoae.crafting_report.card."
            + (header.partialBytes() ? "bytes_partial" : "bytes")),
            left + 6, rowY + elapsedHeight + 11, 58, 1, PANEL_TEXT, false);
        wrapped(graphics, font, header.bytes(), right - 6, rowY + elapsedHeight + 11, width - 84, CYAN, true);
        rowY += elapsedHeight + bytesHeight + 23;
        Component[] values = {header.patterns(), header.cycles(), Component.literal(Integer.toString(materialCount))};
        Component[] labels = {Component.translatable("gui.neoecoae.crafting_report.card.patterns"),
            Component.translatable("gui.neoecoae.crafting_report.card.cycles"),
            Component.translatable("gui.neoecoae.multiblock.preview.info.materials")};
        for (int i = 0; i < values.length; i++) {
            int tileLeft = left + i * (width - 12) / 3;
            int tileWidth = (width - 28) / 3;
            panel(background, graphics, tileLeft, rowY, tileWidth, 33);
            int center = tileLeft + tileWidth / 2;
            fitted(graphics, font, values[i].copy().withStyle(ChatFormatting.BOLD), center,
                rowY + 7, tileWidth - 8, 1.1F, GOLD, false, true);
            fitted(graphics, font, labels[i], center, rowY + 23, tileWidth - 6, 1, PANEL_TEXT, false, true);
        }
        rowY += 39;
        if (header.partialBytes()) {
            rowY += wrapped(graphics, font, Component.translatable("gui.neoecoae.crafting_report.card.incomplete_hint"),
                left, rowY, width - 16, REQUEST, false) + 4;
        }
        graphics.fill(left, rowY, right, rowY + 1, 0xFF514064);
        fitted(graphics, font, Component.translatable("gui.neoecoae.crafting_report.export_title"),
            left, rowY + 10, (width - 20) / 2, 1, SECONDARY, false);
        fitted(graphics, font, Component.translatable("gui.neoecoae.crafting_report.export_materials", materialCount),
            right, rowY + 10, (width - 20) / 2, 1, ACCENT, true);
    }

    private static int wrapped(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
            int width, int color, boolean right) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (var line : lines) {
            cn.dancingsnow.neoecoae.gui.common.GuiDraw.text(graphics, font, line, right ? x - font.width(line) : x, y, color, false);
            y += LINE_HEIGHT;
        }
        return lines.size() * LINE_HEIGHT;
    }

    private static void fitted(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
            int width, float scale, int color, boolean right) {
        fitted(graphics, font, text, x, y, width, scale, color, right, false);
    }

    private static void fitted(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
            int width, float scale, int color, boolean right, boolean centered) {
        scale = Math.min(scale, width / (float) Math.max(1, font.width(text)));
        float drawnWidth = font.width(text) * scale;
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x - (right ? drawnWidth : centered ? drawnWidth / 2 : 0), y);
            pose.scale(scale, scale);
            cn.dancingsnow.neoecoae.gui.common.GuiDraw.text(graphics, font, text, 0, 0, color, false);
        } finally {
            pose.popMatrix();
        }
    }

    private static void panel(Blitter background, GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        background.copy().src(background.getSrcX() + TABLE_X,
            background.getSrcY() + TABLE_TEXTURE_Y + ROW_HEIGHT, 1, 1)
            .dest(x, y, width, height).blit(graphics);
    }

    private static void renderBackground(Blitter background, GuiGraphicsExtractor graphics, Strip strip,
            int tableY, boolean first, boolean last, boolean leftEdge, boolean rightEdge) {
        int x = background.getSrcX();
        int y = background.getSrcY();
        // Extend the report's plain panel and outer edge, leaving out the scrollbar and cycle pane.
        background.copy().src(x, y + 189, WIDTH - 2, 1).dest(0, 0, WIDTH - 2, strip.height()).blit(graphics);
        background.copy().src(x + background.getSrcWidth() - 2, y + 27, 2, 1)
            .dest(WIDTH - 2, 0, 2, strip.height()).blit(graphics);
        if (first) {
            background.copy().src(x + TABLE_X - 1, y + TABLE_TEXTURE_Y - 1, TABLE_WIDTH, 1)
                .dest(TABLE_X - 1, tableY - 1).blit(graphics);
        }
        int rows = strip.endRow() - strip.firstRow();
        // Use a normal interior cell, not the first row's two-pixel top rim.
        // Every column, including the join between render targets, shares a one-pixel divider.
        background.copy().src(x + TABLE_X + 67, y + TABLE_TEXTURE_Y + ROW_HEIGHT, 1, 1)
            .dest(TABLE_X - 1, tableY, TABLE_WIDTH, rows * ROW_HEIGHT).blit(graphics);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                background.copy().src(x + TABLE_X, y + TABLE_TEXTURE_Y + ROW_HEIGHT, 67, 22)
                    .dest(TABLE_X + col * 68, tableY + row * ROW_HEIGHT).blit(graphics);
            }
        }
        if (leftEdge) background.copy().src(x + TABLE_X - 1, y + TABLE_TEXTURE_Y + 8, 1, 1)
            .dest(TABLE_X - 1, tableY, 1, rows * ROW_HEIGHT).blit(graphics);
        if (rightEdge) background.copy().src(x + TABLE_X - 1, y + TABLE_TEXTURE_Y + 8, 1, 1)
            .dest(TABLE_X - 1 + PANEL_STRIDE, tableY, 1, rows * ROW_HEIGHT).blit(graphics);
        if (last) {
            background.copy().src(x + TABLE_X - 1, y + TABLE_TEXTURE_Y + VISIBLE_ROWS * ROW_HEIGHT - 1,
                    TABLE_WIDTH, 1)
                .dest(TABLE_X - 1, tableY + rows * ROW_HEIGHT - 1).blit(graphics);
            background.copy().src(x, y + background.getSrcHeight() - 2, WIDTH - 2, 2)
                .dest(0, strip.height() - 2).blit(graphics);
        }
    }

    /** AE2 owns cell placement, text scaling, item rendering and overlays for the export too. */
    private static final class ExportTable extends AbstractTableRenderer<Entry> {
        private ExportTable(AEBaseScreen<?> screen, int y, int rows) {
            super(screen, TABLE_X, y, rows);
        }

        @Override protected List<Component> getEntryDescription(Entry entry) { return entry.description(); }
        @Override protected AEKey getEntryStack(Entry entry) { return entry.key(); }
        @Override protected List<Component> getEntryTooltip(Entry entry) { return List.of(); }
        @Override protected int getEntryOverlayColor(Entry entry) { return entry.overlay(); }
    }
}
