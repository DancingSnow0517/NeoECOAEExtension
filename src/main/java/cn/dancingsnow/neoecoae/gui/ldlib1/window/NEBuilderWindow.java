package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import cn.dancingsnow.neoecoae.gui.ldlib1.MultiblockBuildUiAdapter;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Textures;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Widgets;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Compact floating window for multiblock structure preview and building.
 *
 * <p>Layout (relative coordinates inside the window):
 * <pre>
 * ┌──────────────────────────┐
 * │ Multiblock Builder    [✕]│
 * │ [-]  Length: N  [+]      │
 * │ [Preview]  [Build]       │
 * │ Reused: 0   Conflicts: 0 │
 * │ Missing: 0  Required: 0  │
 * │ Status: Idle             │
 * └──────────────────────────┘
 * </pre>
 *
 * <p>Uses {@link NEFloatingWindow} for the close button and visibility.
 * The window is initially hidden; call {@link #show()} after construction.</p>
 */
public class NEBuilderWindow extends NEFloatingWindow {

    private final MultiblockBuildUiAdapter adapter;
    private final Player player;

    // Spacing constants
    private static final int ROW_H = 12;
    private static final int BTN_H = 14;
    private static final int GAP = 2;

    /**
     * @param x       absolute x on screen
     * @param y       absolute y on screen
     * @param adapter build adapter from the controller block entity
     * @param player  the player viewing the UI
     * @param onClose called when the close (✕) button is clicked
     */
    public NEBuilderWindow(int x, int y,
                           MultiblockBuildUiAdapter adapter, Player player,
                           Runnable onClose) {
        super(x, y, 156, 120,
            Component.translatable("gui.neoecoae.common.multiblock_builder"),
            onClose);
        this.adapter = adapter;
        this.player = player;

        buildContent();
    }

    private void buildContent() {
        int cx = contentX;      // = 4
        int cy = contentY;      // = 12
        int cw = contentWidth;  // = 148

        // ── Row 0: Length controls ──
        int row0Y = cy + 2;
        // [-] button
        addWidget(makeButton(cx + 2, row0Y, 16, BTN_H, "-", () -> adapter.decreaseBuildLength()));
        // Length label
        addWidget(NELDLib1Widgets.dynamicLabel(cx + 22, row0Y + 2,
            () -> Component.translatable("gui.neoecoae.multiblock.length",
                adapter.getSelectedBuildLength()).getString()));
        // [+] button
        addWidget(makeButton(cx + 90, row0Y, 16, BTN_H, "+", () -> adapter.increaseBuildLength()));

        // ── Row 1: Preview / Build buttons ──
        int row1Y = row0Y + BTN_H + GAP + 4;
        addWidget(makeButton(cx + 2, row1Y, 44, BTN_H,
            Component.translatable("gui.neoecoae.multiblock.preview"),
            () -> adapter.previewStructure(player)));
        addWidget(makeButton(cx + 52, row1Y, 44, BTN_H,
            Component.translatable("gui.neoecoae.multiblock.build"),
            () -> adapter.autoBuild(player)));

        // ── Rows 2-5: Stats ──
        int statsY = row1Y + BTN_H + GAP + 4;
        int ss = ROW_H;

        addWidget(NELDLib1Widgets.dynamicLabel(cx + 2, statsY,
            () -> Component.translatable("gui.neoecoae.multiblock.reused",
                adapter.getPreviewReusedBlocks()).getString()));
        addWidget(NELDLib1Widgets.dynamicLabel(cx + 2, statsY + ss,
            () -> Component.translatable("gui.neoecoae.multiblock.missing",
                adapter.getPreviewMissingBlocks()).getString()));
        addWidget(NELDLib1Widgets.dynamicLabel(cx + 2, statsY + ss * 2,
            () -> Component.translatable("gui.neoecoae.multiblock.conflicts",
                adapter.getPreviewConflictBlocks()).getString()));
        addWidget(NELDLib1Widgets.dynamicLabel(cx + 2, statsY + ss * 3,
            () -> Component.translatable("gui.neoecoae.multiblock.required_items",
                adapter.getPreviewRequiredItems()).getString()));

        // ── Bottom: Status ──
        int statusY = contentY + contentHeight - 12;
        addWidget(NELDLib1Widgets.dynamicLabel(cx + 2, statusY,
            () -> adapter.getPreviewStatusComponent().getString()));
    }

    private ButtonWidget makeButton(int x, int y, int w, int h, String text, Runnable action) {
        return new ButtonWidget(x, y, w, h,
            NELDLib1Textures.text(text, 0xFFFFFFFF, Math.max(1, w - 4))
                .setBackgroundTexture(NELDLib1Textures.BUTTON),
            data -> {
                if (!data.isRemote) action.run();
            })
            .setHoverTexture(NELDLib1Textures.BUTTON_HOVER);
    }

    private ButtonWidget makeButton(int x, int y, int w, int h, Component text, Runnable action) {
        return makeButton(x, y, w, h, text.getString(), action);
    }
}
