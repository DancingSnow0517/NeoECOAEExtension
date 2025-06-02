package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

public class AEStyleTankWidget extends TankWidget {

    public AEStyleTankWidget(IFluidHandler fluidTank, int tank, int x, int y, boolean allowClickContainerFilling, boolean allowClickContainerEmptying) {
        super(fluidTank, tank, x, y, allowClickContainerFilling, allowClickContainerEmptying);
    }

    @Override
    public void initTemplate() {
        setBackground(GuiTextures.ITEM_SLOT);
        setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP);
        setDrawHoverOverlay(false);
    }

    @Override
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (isMouseOverElement(mouseX, mouseY) && getHoverElement(mouseX, mouseY) == this) {
            RenderSystem.colorMask(true, true, true, false);
            DrawerHelper.drawSolidRect(graphics, getPosition().x, getPosition().y, 18, 18, 0x80ace9ff);
            RenderSystem.colorMask(true, true, true, true);
        }
    }
}
