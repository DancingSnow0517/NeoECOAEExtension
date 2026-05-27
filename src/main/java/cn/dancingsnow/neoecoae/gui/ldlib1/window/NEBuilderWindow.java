package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import cn.dancingsnow.neoecoae.gui.ldlib1.MultiblockBuildUiAdapter;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Theme;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Compact floating builder window using NEButton for all controls.
 * Light background with dark text; close button on visible title bar.
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
        setTitleColor(NELDLib1Theme.TEXT_DARK);
        buildContent();
    }

    private void buildContent() {
        int cx = contentX;
        int cy = contentY;

        // ── Row 0: Length controls ──
        int row0Y = cy + 2;
        addWidget(NEButton.text(cx + 2, row0Y, 16, BTN_H, "-",
            NELDLib1Theme.BUTTON_TEXT_DARK,
            data -> { if (!data.isRemote) adapter.decreaseBuildLength(); }));
        addWidget(new LabelWidget(cx + 22, row0Y + 2,
            () -> Component.translatable("gui.neoecoae.multiblock.length",
                adapter.getSelectedBuildLength()).getString())
            .setTextColor(NELDLib1Theme.TEXT_DARK).setDropShadow(false));
        addWidget(NEButton.text(cx + 90, row0Y, 16, BTN_H, "+",
            NELDLib1Theme.BUTTON_TEXT_DARK,
            data -> { if (!data.isRemote) adapter.increaseBuildLength(); }));

        // ── Row 1: Preview / Build ──
        int row1Y = row0Y + BTN_H + GAP + 4;
        addWidget(NEButton.text(cx + 2, row1Y, 44, BTN_H,
            Component.translatable("gui.neoecoae.multiblock.preview").getString(),
            NELDLib1Theme.BUTTON_TEXT_DARK,
            data -> { if (!data.isRemote) adapter.previewStructure(player); }));
        addWidget(NEButton.text(cx + 52, row1Y, 44, BTN_H,
            Component.translatable("gui.neoecoae.multiblock.build").getString(),
            NELDLib1Theme.BUTTON_TEXT_DARK,
            data -> { if (!data.isRemote) adapter.autoBuild(player); }));

        // ── Stats (dark text on light bg) ──
        int statsY = row1Y + BTN_H + GAP + 4;
        int ss = ROW_H;
        int lc = NELDLib1Theme.TEXT_DARK;

        addWidget(new LabelWidget(cx + 2, statsY,
            () -> Component.translatable("gui.neoecoae.multiblock.reused",
                adapter.getPreviewReusedBlocks()).getString())
            .setTextColor(lc).setDropShadow(false));
        addWidget(new LabelWidget(cx + 2, statsY + ss,
            () -> Component.translatable("gui.neoecoae.multiblock.missing",
                adapter.getPreviewMissingBlocks()).getString())
            .setTextColor(lc).setDropShadow(false));
        addWidget(new LabelWidget(cx + 2, statsY + ss * 2,
            () -> Component.translatable("gui.neoecoae.multiblock.conflicts",
                adapter.getPreviewConflictBlocks()).getString())
            .setTextColor(lc).setDropShadow(false));
        addWidget(new LabelWidget(cx + 2, statsY + ss * 3,
            () -> Component.translatable("gui.neoecoae.multiblock.required_items",
                adapter.getPreviewRequiredItems()).getString())
            .setTextColor(lc).setDropShadow(false));

        // ── Status ──
        int statusY = contentY + contentHeight - 12;
        addWidget(new LabelWidget(cx + 2, statusY,
            () -> adapter.getPreviewStatusComponent().getString())
            .setTextColor(lc).setDropShadow(false));
    }
}
