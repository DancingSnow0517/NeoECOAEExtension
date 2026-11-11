package cn.dancingsnow.neoecoae.client.gui.crafting;

import appeng.api.stacks.AEKey;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.PaletteColor;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
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

    record Entry(AEKey key, List<Component> description, int overlay) {}
    record Strip(int firstRow, int endRow, int y, int height) {}
    record Layout(int height, List<Strip> strips) {}

    private ECOCraftingListImageExport() {}

    static void save(AEBaseScreen<?> screen, List<Entry> entries, List<Component> header) {
        if (entries.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        try {
            int rows = Math.toIntExact(((long) entries.size() + COLUMNS - 1) / COLUMNS);
            int panels = panelCount(rows);
            int headingWidth = (panels == 1 ? WIDTH : TABLE_X - 1 + PANEL_STRIDE) - MARGIN * 2;
            List<FormattedCharSequence> heading = new ArrayList<>();
            for (Component line : header) heading.addAll(client.font.split(line, headingWidth));
            heading.addAll(client.font.split(Component.translatable(
                "gui.neoecoae.crafting_report.export_materials", entries.size()), headingWidth));
            int headerHeight = MARGIN + heading.size() * LINE_HEIGHT + 5;
            int maxHeight = Math.min(MAX_STRIP_HEIGHT, RenderSystem.maxSupportedTextureSize() / SCALE);
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
            renderImage(client, screen, entries, heading, headerHeight, layout, panels, entriesPerPanel, file);
            Component link = Component.literal(file.getFileName().toString())
                .withStyle(ChatFormatting.UNDERLINE).withStyle(style -> style.withClickEvent(
                    new ClickEvent(ClickEvent.Action.OPEN_FILE, file.toAbsolutePath().toString())));
            client.gui.getChat().addMessage(Component.translatable(
                "gui.neoecoae.crafting_report.export_saved", link));
        } catch (Exception exception) {
            LoggerFactory.getLogger("neoecoae").warn("Could not export crafting list image", exception);
            client.gui.getChat().addMessage(Component.translatable("screenshot.failure", exception.getMessage()));
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

    private static void renderImage(Minecraft client, AEBaseScreen<?> screen, List<Entry> entries,
            List<FormattedCharSequence> heading, int headerHeight, Layout layout,
            int panels, int entriesPerPanel, Path file) throws IOException {
        // Preserve the caller's matrices without borrowing RenderSystem's shared backup slot.
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        graphics.flush();
        int imageWidth = Math.addExact(WIDTH, Math.multiplyExact(panels - 1, PANEL_STRIDE));
        try (NativeImage image = new NativeImage(Math.multiplyExact(imageWidth, SCALE),
                Math.multiplyExact(layout.height(), SCALE), false)) {
            modelView.identity();
            modelView.translate(0, 0, -2000);
            RenderSystem.applyModelViewMatrix();
            for (int panel = 0; panel < panels; panel++) {
                int firstEntry = Math.min(entries.size(), Math.multiplyExact(panel, entriesPerPanel));
                int endEntry = (int) Math.min(entries.size(), (long) firstEntry + entriesPerPanel);
                List<Entry> panelEntries = entries.subList(firstEntry, endEntry);
                // Keep only the image's outer margins; splice the groups at their cell dividers.
                int sourceX = panel == 0 ? 0 : TABLE_X - 1;
                int sourceEndX = panel == panels - 1 ? WIDTH : TABLE_X - 1 + PANEL_STRIDE;
                int destinationX = panel * PANEL_STRIDE + sourceX;
                for (Strip strip : layout.strips()) {
                    try (NativeImage rendered = renderStrip(client, screen, graphics, panelEntries,
                            panel == 0 ? heading : List.of(), headerHeight, strip,
                            strip.y() + strip.height() == layout.height())) {
                        rendered.copyRect(image, sourceX * SCALE, 0, destinationX * SCALE, strip.y() * SCALE,
                            (sourceEndX - sourceX) * SCALE, rendered.getHeight(), false, false);
                    }
                }
            }
            image.writeToFile(file);
        } finally {
            client.getMainRenderTarget().bindWrite(true);
            RenderSystem.setProjectionMatrix(projection, sorting);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    private static NativeImage renderStrip(Minecraft client, AEBaseScreen<?> screen, GuiGraphics graphics,
            List<Entry> entries, List<FormattedCharSequence> heading, int headerHeight,
            Strip strip, boolean last) {
        TextureTarget target = null;
        try {
            target = new TextureTarget(WIDTH * SCALE, strip.height() * SCALE, true, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(
                0, WIDTH, strip.height(), 0, 1000, 3000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            boolean first = strip.firstRow() == 0;
            int tableY = first ? headerHeight : 0;
            renderBackground(screen.getStyle().getBackground(), graphics, strip, tableY, first, last);
            if (first) {
                int color = screen.getStyle().getColor(PaletteColor.MUTED_TEXT_COLOR).toARGB();
                int y = MARGIN;
                for (var line : heading) {
                    graphics.drawString(client.font, line, MARGIN, y, color, false);
                    y += LINE_HEIGHT;
                }
            }
            new ExportTable(screen, tableY, strip.endRow() - strip.firstRow())
                .render(graphics, -1_000_000, -1_000_000, entries, strip.firstRow());
            graphics.flush();
            return Screenshot.takeScreenshot(target);
        } finally {
            // Flush before destroying the target even if an addon item renderer fails.
            try {
                graphics.flush();
            } finally {
                if (target != null) target.destroyBuffers();
            }
        }
    }

    private static void renderBackground(Blitter background, GuiGraphics graphics, Strip strip,
            int tableY, boolean first, boolean last) {
        int x = background.getSrcX();
        int y = background.getSrcY();
        // Extend the report's plain panel and outer edge, leaving out the scrollbar and cycle pane.
        background.copy().src(x, y + 189, WIDTH - 2, 1).dest(0, 0, WIDTH - 2, strip.height()).blit(graphics);
        background.copy().src(x + background.getSrcWidth() - 2, y + 27, 2, 1)
            .dest(WIDTH - 2, 0, 2, strip.height()).blit(graphics);
        if (first) {
            background.copy().src(x, y, WIDTH - 2, 2).dest(0, 0).blit(graphics);
            background.copy().src(x + background.getSrcWidth() - 2, y, 2, 2).dest(WIDTH - 2, 0).blit(graphics);
            background.copy().src(x + TABLE_X - 1, y + TABLE_TEXTURE_Y - 1, TABLE_WIDTH, 1)
                .dest(TABLE_X - 1, tableY - 1).blit(graphics);
        }
        int rows = strip.endRow() - strip.firstRow();
        for (int row = 0; row < rows; row++) {
            background.copy().src(x + TABLE_X - 1, y + TABLE_TEXTURE_Y, TABLE_WIDTH, ROW_HEIGHT)
                .dest(TABLE_X - 1, tableY + row * ROW_HEIGHT).blit(graphics);
        }
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
