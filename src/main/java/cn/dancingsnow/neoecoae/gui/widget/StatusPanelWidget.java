package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import org.jetbrains.annotations.Nullable;

public class StatusPanelWidget extends WidgetGroup {

    public static final int MAX_COL = 12;

    public StatusPanelWidget(int x, int y) {
        super(x, y, 229, 58);
        setBackground(GuiTextures.Crafting.STATUS_BACKGROUND);
    }

    @Override
    public void initWidget() {
        super.initWidget();
        for (int c = 0; c < MAX_COL; c++) {
            addWidget(new ParallelStatus(1 + c * 19, 1));
        }

        for (int c = 0; c < MAX_COL; c++) {
            addWidget(new CraftingStatus(1 + c * 19, 20));
        }

        for (int c = 0; c < MAX_COL; c++) {
            addWidget(new ParallelStatus(1 + c * 19, 39));
        }
    }

    class ParallelStatus extends ImageWidget {

        public ParallelStatus(int xPosition, int yPosition) {
            super(xPosition, yPosition, 18, 18, GuiTextures.Crafting.UNAVAILABLE_STATUS);
        }

        public ParallelStatus setTier(@Nullable IECOTier tier) {
            if (tier == null) {
                setImage(GuiTextures.Crafting.UNAVAILABLE_STATUS);
            }
            return this;
        }
    }

    class CraftingStatus extends WidgetGroup {

        public CraftingStatus(int x, int y) {
            super(x, y, 18, 18);
            setBackground(GuiTextures.Crafting.UNAVAILABLE_STATUS);
        }
    }
}
