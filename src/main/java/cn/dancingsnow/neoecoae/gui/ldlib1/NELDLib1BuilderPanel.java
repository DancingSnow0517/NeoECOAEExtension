package cn.dancingsnow.neoecoae.gui.ldlib1;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * LDLib2-style builder panel recreated with LDLib1 fixed-coordinate widgets.
 * Layout-driven: positions read from {@link NELDLib1UiSpecs.BuilderPanelSpec}.
 *
 * <p>Supports variable panel width/height and optional background texture so
 * different controllers can share the same builder panel logic at different sizes.</p>
 */
public final class NELDLib1BuilderPanel {

    private NELDLib1BuilderPanel() {
    }

    /**
     * Add a builder panel using the default background and spec-driven layout.
     */
    public static void add(ModularUI ui, Player player, MultiblockBuildUiAdapter adapter, int x, int y, int width, int height) {
        add(ui, player, adapter, x, y, width, height, NELDLib1Textures.CRAFTING_BACKGROUND_LIGHT);
    }

    /**
     * Add a builder panel with a custom background texture.
     */
    public static void add(
        ModularUI ui,
        Player player,
        MultiblockBuildUiAdapter adapter,
        int x,
        int y,
        int width,
        int height,
        IGuiTexture background
    ) {
        // Background
        ui.widget(NELDLib1Widgets.image(x, y, width, height, background));

        // Title
        ui.widget(NELDLib1Widgets.label(
            x + NELDLib1UiSpecs.BuilderPanelSpec.TITLE_X,
            y + NELDLib1UiSpecs.BuilderPanelSpec.TITLE_Y,
            Component.translatable("gui.neoecoae.multiblock.builder")
        ));

        // [-] Length [+]
        ui.widget(NELDLib1Widgets.button(
            x + 6, y + 20,
            18, 16,
            Component.literal("-"), data -> {
                if (!data.isRemote) {
                    adapter.decreaseBuildLength();
                }
            }
        ));
        ui.widget(NELDLib1Widgets.dynamicLabel(
            x + 28, y + 24,
            () -> Component.translatable("gui.neoecoae.multiblock.length", adapter.getSelectedBuildLength()).getString()
        ));
        ui.widget(NELDLib1Widgets.button(
            x + 88, y + 20,
            18, 16,
            Component.literal("+"), data -> {
                if (!data.isRemote) {
                    adapter.increaseBuildLength();
                }
            }
        ));

        // Preview / Build buttons (auto-fit to panel width)
        int btnW = Math.min(48, (width - 6 - 12) / 2);
        ui.widget(NELDLib1Widgets.button(
            x + 6, y + 40,
            btnW, 16,
            Component.translatable("gui.neoecoae.multiblock.preview"), data -> {
                if (!data.isRemote) {
                    adapter.previewStructure(player);
                }
            }
        ));
        int buildBtnX = 6 + btnW + 4;
        ui.widget(NELDLib1Widgets.button(
            x + buildBtnX, y + 40,
            btnW, 16,
            Component.translatable("gui.neoecoae.multiblock.build"), data -> {
                if (!data.isRemote) {
                    adapter.autoBuild(player);
                }
            }
        ));

        // Stats: two-column layout
        ui.widget(NELDLib1Widgets.dynamicLabel(
            x + 6, y + 62,
            () -> Component.translatable("gui.neoecoae.multiblock.reused", adapter.getPreviewReusedBlocks()).getString()
        ));
        ui.widget(NELDLib1Widgets.dynamicLabel(
            x + 96, y + 62,
            () -> Component.translatable("gui.neoecoae.multiblock.conflicts", adapter.getPreviewConflictBlocks()).getString()
        ));
        ui.widget(NELDLib1Widgets.dynamicLabel(
            x + 6, y + 74,
            () -> Component.translatable("gui.neoecoae.multiblock.missing", adapter.getPreviewMissingBlocks()).getString()
        ));
        ui.widget(NELDLib1Widgets.dynamicLabel(
            x + 96, y + 74,
            () -> Component.translatable("gui.neoecoae.multiblock.required_items", adapter.getPreviewRequiredItems()).getString()
        ));

        // Status text at bottom
        ui.widget(NELDLib1Widgets.dynamicLabel(
            x + 6, y + 92,
            () -> adapter.getPreviewStatusComponent().getString()
        ));
    }

    /**
     * Legacy convenience overload: auto-sizes to the default width × height.
     */
    public static void add(ModularUI ui, Player player, MultiblockBuildUiAdapter adapter, int x, int y) {
        add(ui, player, adapter, x, y, 182, 110);
    }
}
