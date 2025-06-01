package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

public class AEStyleWidgetGroup extends WidgetGroup {
    public AEStyleWidgetGroup() {

    }

    @Override
    public void initTemplate() {
        setOverlay(GuiTextures.INVENTORY_BORDER);
    }

    @Override
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        drawOverlay(graphics, mouseX, mouseY, partialTicks);
    }
}
