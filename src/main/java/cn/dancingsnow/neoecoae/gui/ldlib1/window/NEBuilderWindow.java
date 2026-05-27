package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import cn.dancingsnow.neoecoae.gui.ldlib1.MultiblockBuildUiAdapter;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Textures;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Theme;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Widgets;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Compact floating window for multiblock structure preview and building.
 * Light background with dark text for readability; close button on title bar.
 */
public class NEBuilderWindow extends NEFloatingWindow {

    private final MultiblockBuildUiAdapter adapter;
    private final Player player;

    private static final int ROW_H = 11;
    private static final int BTN_H = 14;
    private static final int GAP = 2;

    public NEBuilderWindow(int x, int y,
                           MultiblockBuildUiAdapter adapter, Player player,
                           Runnable onClose) {
        super(x, y, 156, 120,
            Component.translatable("gui.neoecoae.common.multiblock_builder"),
            onClose);
        this.adapter = adapter;
        this.player = player;
        // Builder uses light background → dark text for readability
        setTitleColor(NELDLib1Theme.TEXT_DARK);
        buildContent();
    }

    private void buildContent() {
        int cx = contentX;
        int cy = contentY;

        // ── Title-bar separator line ──
        addWidget(new ImageWidget(cx, cy - 1, contentWidth, 1,
            NELDLib1Textures.SCROLLBAR_TRACK));

        // ── Row 0: Length controls ──
        int row0Y = cy + 2;
        addWidget(makeButton(cx + 2, row0Y, 16, BTN_H, "-", () -> adapter.decreaseBuildLength()));
        addWidget(NELDLib1Widgets.dynamicLabel(cx + 22, row0Y + 2,
            () -> Component.translatable("gui.neoecoae.multiblock.length",
                adapter.getSelectedBuildLength()).getString()));
        addWidget(makeButton(cx + 90, row0Y, 16, BTN_H, "+", () -> adapter.increaseBuildLength()));

        // ── Row 1: Preview / Build ──
        int row1Y = row0Y + BTN_H + GAP + 4;
        addWidget(makeButton(cx + 2, row1Y, 44, BTN_H,
            Component.translatable("gui.neoecoae.multiblock.preview"),
            () -> adapter.previewStructure(player)));
        addWidget(makeButton(cx + 52, row1Y, 44, BTN_H,
            Component.translatable("gui.neoecoae.multiblock.build"),
            () -> adapter.autoBuild(player)));

        // ── Stats (dark text on light background) ──
        int statsY = row1Y + BTN_H + GAP + 4;
        int ss = ROW_H;
        int labelColor = NELDLib1Theme.TEXT_DARK;

        addWidget(new LabelWidget(cx + 2, statsY,
            () -> Component.translatable("gui.neoecoae.multiblock.reused",
                adapter.getPreviewReusedBlocks()).getString())
            .setTextColor(labelColor).setDropShadow(false));
        addWidget(new LabelWidget(cx + 2, statsY + ss,
            () -> Component.translatable("gui.neoecoae.multiblock.missing",
                adapter.getPreviewMissingBlocks()).getString())
            .setTextColor(labelColor).setDropShadow(false));
        addWidget(new LabelWidget(cx + 2, statsY + ss * 2,
            () -> Component.translatable("gui.neoecoae.multiblock.conflicts",
                adapter.getPreviewConflictBlocks()).getString())
            .setTextColor(labelColor).setDropShadow(false));
        addWidget(new LabelWidget(cx + 2, statsY + ss * 3,
            () -> Component.translatable("gui.neoecoae.multiblock.required_items",
                adapter.getPreviewRequiredItems()).getString())
            .setTextColor(labelColor).setDropShadow(false));

        // ── Status ──
        int statusY = contentY + contentHeight - 12;
        addWidget(new LabelWidget(cx + 2, statusY,
            () -> adapter.getPreviewStatusComponent().getString())
            .setTextColor(labelColor).setDropShadow(false));
    }

    /** Create a button with dark text + shadow on the default BUTTON background. */
    private ButtonWidget makeButton(int x, int y, int w, int h, String text, Runnable action) {
        var texture = NELDLib1Textures
            .textShadow(text, NELDLib1Theme.BUTTON_TEXT_DARK, Math.max(1, w - 4))
            .setBackgroundTexture(NELDLib1Textures.BUTTON);
        return new ButtonWidget(x, y, w, h, texture,
            data -> { if (!data.isRemote) action.run(); })
            .setHoverTexture(NELDLib1Textures.BUTTON_HOVER)
            .setClickedTexture(NELDLib1Textures.BUTTON_HIGHLIGHTED);
    }

    private ButtonWidget makeButton(int x, int y, int w, int h, Component text, Runnable action) {
        return makeButton(x, y, w, h, text.getString(), action);
    }
}
