package cn.dancingsnow.neoecoae.gui.ldlib1;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * LDLib2-style floating builder window recreated with LDLib1 fixed-coordinate
 * widgets. Layout driven by {@link NELDLib1UiSpecs.BuilderPanelSpec}.
 *
 * <p>The builder is now a compact independent floating window positioned to the
 * right of the main status terminal, NOT embedded inside it.</p>
 */
public final class NELDLib1BuilderPanel {

    private NELDLib1BuilderPanel() {
    }

    /**
     * Add a floating builder panel using the default light background and
     * spec-driven compact layout.
     *
     * @return list of builder widgets so the caller can toggle visibility
     */
    public static List<Widget> addFloat(
        ModularUI ui,
        Player player,
        MultiblockBuildUiAdapter adapter,
        int x,
        int y,
        int width,
        int height
    ) {
        return addFloat(ui, player, adapter, x, y, width, height,
            NELDLib1Textures.CRAFTING_BACKGROUND_LIGHT);
    }

    /**
     * Add a floating builder panel with a custom background texture.
     *
     * @return list of builder widgets for optional visibility toggling
     */
    public static List<Widget> addFloat(
        ModularUI ui,
        Player player,
        MultiblockBuildUiAdapter adapter,
        int x,
        int y,
        int width,
        int height,
        IGuiTexture background
    ) {
        List<Widget> widgets = new ArrayList<>();

        // Background panel
        var bg = NELDLib1Widgets.image(x, y, width, height, background);
        ui.widget(bg);
        widgets.add(bg);

        // Title label — top-left
        var title = NELDLib1Widgets.label(
            x + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_TITLE_X,
            y + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_TITLE_Y,
            Component.translatable("gui.neoecoae.common.multiblock_builder"),
            NELDLib1Widgets.TITLE_COLOR
        );
        ui.widget(title);
        widgets.add(title);

        // Close / hammer button — top-right corner
        int closeX = x + width + NELDLib1UiSpecs.BuilderPanelSpec.CLOSE_BTN_X_OFFSET;
        int closeY = y + NELDLib1UiSpecs.BuilderPanelSpec.CLOSE_BTN_Y;
        var closeBtn = NELDLib1Widgets.squareButton(
            closeX, closeY, NELDLib1UiSpecs.BuilderPanelSpec.CLOSE_BTN_SIZE,
            Component.literal("\u2715"), // ✕
            data -> { /* decorative — toggle handled by hammer button in main UI */ }
        );
        ui.widget(closeBtn);
        widgets.add(closeBtn);

        // ── Length row: [-] Label [+] ──
        int lenRowY = y + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_LENGTH_ROW_Y;

        var decBtn = NELDLib1Widgets.button(
            x + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_LENGTH_DEC_X, lenRowY,
            NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_LENGTH_BUTTON_W,
            NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_LENGTH_BUTTON_H,
            Component.literal("-"), data -> {
                if (!data.isRemote) adapter.decreaseBuildLength();
            }
        );
        ui.widget(decBtn);
        widgets.add(decBtn);

        var lenLabel = NELDLib1Widgets.dynamicLabel(
            x + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_LENGTH_LABEL_X,
            lenRowY + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_LENGTH_LABEL_Y_OFF,
            () -> String.valueOf(adapter.getSelectedBuildLength())
        );
        ui.widget(lenLabel);
        widgets.add(lenLabel);

        var incBtn = NELDLib1Widgets.button(
            x + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_LENGTH_INC_X, lenRowY,
            NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_LENGTH_BUTTON_W,
            NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_LENGTH_BUTTON_H,
            Component.literal("+"), data -> {
                if (!data.isRemote) adapter.increaseBuildLength();
            }
        );
        ui.widget(incBtn);
        widgets.add(incBtn);

        // ── Preview / Build buttons ──
        int btnRowY = y + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_BTN_ROW_Y;

        var previewBtn = NELDLib1Widgets.button(
            x + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_PREVIEW_BTN_X, btnRowY,
            NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_BTN_W,
            NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_BTN_H,
            Component.translatable("gui.neoecoae.multiblock.preview"), data -> {
                if (!data.isRemote) adapter.previewStructure(player);
            }
        );
        ui.widget(previewBtn);
        widgets.add(previewBtn);

        var buildBtn = NELDLib1Widgets.button(
            x + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_BUILD_BTN_X, btnRowY,
            NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_BTN_W,
            NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_BTN_H,
            Component.translatable("gui.neoecoae.multiblock.build"), data -> {
                if (!data.isRemote) adapter.autoBuild(player);
            }
        );
        ui.widget(buildBtn);
        widgets.add(buildBtn);

        // ── Stats: compact single-column rows ──
        int statsY = y + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_STATS_START_Y;
        int sx = x + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_STATS_X;
        int ss = NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_STATS_ROW_SPACING;

        var reusedLabel = NELDLib1Widgets.dynamicLabel(sx, statsY,
            () -> Component.translatable("gui.neoecoae.multiblock.reused",
                adapter.getPreviewReusedBlocks()).getString());
        ui.widget(reusedLabel);
        widgets.add(reusedLabel);

        var missingLabel = NELDLib1Widgets.dynamicLabel(sx, statsY + ss,
            () -> Component.translatable("gui.neoecoae.multiblock.missing",
                adapter.getPreviewMissingBlocks()).getString());
        ui.widget(missingLabel);
        widgets.add(missingLabel);

        var conflictsLabel = NELDLib1Widgets.dynamicLabel(sx, statsY + ss * 2,
            () -> Component.translatable("gui.neoecoae.multiblock.conflicts",
                adapter.getPreviewConflictBlocks()).getString());
        ui.widget(conflictsLabel);
        widgets.add(conflictsLabel);

        var requiredLabel = NELDLib1Widgets.dynamicLabel(sx, statsY + ss * 3,
            () -> Component.translatable("gui.neoecoae.multiblock.required_items",
                adapter.getPreviewRequiredItems()).getString());
        ui.widget(requiredLabel);
        widgets.add(requiredLabel);

        // ── Status text at bottom ──
        int statusY = y + height + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_STATUS_TEXT_Y_OFFSET;
        var statusLabel = NELDLib1Widgets.dynamicLabel(
            x + NELDLib1UiSpecs.BuilderPanelSpec.FLOAT_STATUS_TEXT_X, statusY,
            () -> adapter.getPreviewStatusComponent().getString()
        );
        ui.widget(statusLabel);
        widgets.add(statusLabel);

        return widgets;
    }

    // ── Legacy API (used by Crafting Controller, unchanged) ──

    /**
     * @deprecated Use {@link #addFloat} for the new LDLib2-style floating layout.
     * Kept for Crafting Controller compatibility.
     */
    @Deprecated
    public static void add(ModularUI ui, Player player, MultiblockBuildUiAdapter adapter,
                           int x, int y, int width, int height) {
        add(ui, player, adapter, x, y, width, height, NELDLib1Textures.CRAFTING_BACKGROUND_LIGHT);
    }

    /**
     * @deprecated Use {@link #addFloat} for the new LDLib2-style floating layout.
     * Kept for Crafting Controller compatibility.
     */
    @Deprecated
    public static void add(ModularUI ui, Player player, MultiblockBuildUiAdapter adapter,
                           int x, int y, int width, int height, IGuiTexture background) {
        // Legacy layout — used only by Crafting Controller
        ui.widget(NELDLib1Widgets.image(x, y, width, height, background));

        ui.widget(NELDLib1Widgets.label(
            x + NELDLib1UiSpecs.BuilderPanelSpec.TITLE_X,
            y + NELDLib1UiSpecs.BuilderPanelSpec.TITLE_Y,
            Component.translatable("gui.neoecoae.multiblock.builder")
        ));

        ui.widget(NELDLib1Widgets.button(x + 6, y + 20, 18, 16,
            Component.literal("-"), data -> {
                if (!data.isRemote) adapter.decreaseBuildLength();
            }));
        ui.widget(NELDLib1Widgets.dynamicLabel(x + 28, y + 24,
            () -> Component.translatable("gui.neoecoae.multiblock.length",
                adapter.getSelectedBuildLength()).getString()));
        ui.widget(NELDLib1Widgets.button(x + 88, y + 20, 18, 16,
            Component.literal("+"), data -> {
                if (!data.isRemote) adapter.increaseBuildLength();
            }));

        int btnW = Math.min(48, (width - 6 - 12) / 2);
        ui.widget(NELDLib1Widgets.button(x + 6, y + 40, btnW, 16,
            Component.translatable("gui.neoecoae.multiblock.preview"), data -> {
                if (!data.isRemote) adapter.previewStructure(player);
            }));
        ui.widget(NELDLib1Widgets.button(x + 6 + btnW + 4, y + 40, btnW, 16,
            Component.translatable("gui.neoecoae.multiblock.build"), data -> {
                if (!data.isRemote) adapter.autoBuild(player);
            }));

        ui.widget(NELDLib1Widgets.dynamicLabel(x + 6, y + 62,
            () -> Component.translatable("gui.neoecoae.multiblock.reused",
                adapter.getPreviewReusedBlocks()).getString()));
        ui.widget(NELDLib1Widgets.dynamicLabel(x + 96, y + 62,
            () -> Component.translatable("gui.neoecoae.multiblock.conflicts",
                adapter.getPreviewConflictBlocks()).getString()));
        ui.widget(NELDLib1Widgets.dynamicLabel(x + 6, y + 74,
            () -> Component.translatable("gui.neoecoae.multiblock.missing",
                adapter.getPreviewMissingBlocks()).getString()));
        ui.widget(NELDLib1Widgets.dynamicLabel(x + 96, y + 74,
            () -> Component.translatable("gui.neoecoae.multiblock.required_items",
                adapter.getPreviewRequiredItems()).getString()));

        ui.widget(NELDLib1Widgets.dynamicLabel(x + 6, y + 92,
            () -> adapter.getPreviewStatusComponent().getString()));
    }

    /**
     * @deprecated Legacy convenience overload.
     */
    @Deprecated
    public static void add(ModularUI ui, Player player, MultiblockBuildUiAdapter adapter,
                           int x, int y) {
        add(ui, player, adapter, x, y, 182, 110);
    }
}
