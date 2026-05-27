package cn.dancingsnow.neoecoae.gui.ldlib1;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public interface MultiblockBuildUiAdapter {
    int getSelectedBuildLength();

    int getPreviewMissingBlocks();

    int getPreviewConflictBlocks();

    int getPreviewReusedBlocks();

    int getPreviewRequiredItems();

    Component getPreviewStatusComponent();

    void decreaseBuildLength();

    void increaseBuildLength();

    void previewStructure(Player player);

    void autoBuild(Player player);

    boolean isBuildInProgress();

    boolean isFormed();
}
