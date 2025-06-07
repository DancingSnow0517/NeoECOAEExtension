package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

public class TranslucentBackgroundWidgetGroup extends WidgetGroup {

    public TranslucentBackgroundWidgetGroup() {
    }

    public TranslucentBackgroundWidgetGroup(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public TranslucentBackgroundWidgetGroup(Position position) {
        super(position);
    }

    public TranslucentBackgroundWidgetGroup(Position position, Size size) {
        super(position, size);
    }

    @Override
    protected void drawBackgroundTexture(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        super.drawBackgroundTexture(graphics, mouseX, mouseY);
        RenderSystem.disableBlend();
    }
}
