package cn.dancingsnow.neoecoae.gui.ldlib1;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class NELDLib1BuilderPanel {
    private NELDLib1BuilderPanel() {
    }

    public static void add(ModularUI ui, Player player, MultiblockBuildUiAdapter adapter, int x, int y) {
        ui.widget(NELDLib1Widgets.image(x, y, 182, 110, NELDLib1Textures.CRAFTING_BACKGROUND_LIGHT));
        ui.widget(NELDLib1Widgets.label(x + 6, y + 6, Component.translatable("gui.neoecoae.multiblock.builder")));

        ui.widget(NELDLib1Widgets.button(x + 6, y + 20, 18, 16, Component.literal("-"), data -> {
            if (!data.isRemote) {
                adapter.decreaseBuildLength();
            }
        }));
        ui.widget(NELDLib1Widgets.dynamicLabel(x + 28, y + 24, () -> Component
            .translatable("gui.neoecoae.multiblock.length", adapter.getSelectedBuildLength())
            .getString()));
        ui.widget(NELDLib1Widgets.button(x + 88, y + 20, 18, 16, Component.literal("+"), data -> {
            if (!data.isRemote) {
                adapter.increaseBuildLength();
            }
        }));

        ui.widget(NELDLib1Widgets.button(x + 6, y + 40, 48, 16, Component.translatable("gui.neoecoae.multiblock.preview"), data -> {
            if (!data.isRemote) {
                adapter.previewStructure(player);
            }
        }));
        ui.widget(NELDLib1Widgets.button(x + 58, y + 40, 48, 16, Component.translatable("gui.neoecoae.multiblock.build"), data -> {
            if (!data.isRemote) {
                adapter.autoBuild(player);
            }
        }));

        ui.widget(NELDLib1Widgets.dynamicLabel(x + 6, y + 62, () -> Component
            .translatable("gui.neoecoae.multiblock.reused", adapter.getPreviewReusedBlocks())
            .getString()));
        ui.widget(NELDLib1Widgets.dynamicLabel(x + 6, y + 74, () -> Component
            .translatable("gui.neoecoae.multiblock.missing", adapter.getPreviewMissingBlocks())
            .getString()));
        ui.widget(NELDLib1Widgets.dynamicLabel(x + 92, y + 62, () -> Component
            .translatable("gui.neoecoae.multiblock.conflicts", adapter.getPreviewConflictBlocks())
            .getString()));
        ui.widget(NELDLib1Widgets.dynamicLabel(x + 92, y + 74, () -> Component
            .translatable("gui.neoecoae.multiblock.required_items", adapter.getPreviewRequiredItems())
            .getString()));
        ui.widget(NELDLib1Widgets.dynamicLabel(x + 6, y + 92, () -> adapter.getPreviewStatusComponent().getString()));
    }
}
