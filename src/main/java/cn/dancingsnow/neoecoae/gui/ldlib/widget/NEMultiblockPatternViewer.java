package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternSnapshot;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import org.jetbrains.annotations.Nullable;

interface NEMultiblockPatternViewer {
    Widget asWidget();

    @Nullable MultiblockPatternSnapshot snapshot();

    int selectedLayer();
}
