package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.fluid.IFluidStorage;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A {@link TankWidget} that paints its own AE2-style slot frame instead of relying on
 * {@code ldlib:textures/gui/fluid_slot.png}. Resource packs that ship a blank or differently sized
 * replacement for that LDLib texture would otherwise make the tank invisible.
 */
public class NEAeTankWidget extends TankWidget {
    private final IFluidStorage fluidStorage;

    public NEAeTankWidget(IFluidStorage storage, int x, int y, int width, int height) {
        super(storage, x, y, width, height, true, true);
        this.fluidStorage = storage;
        setBackground(IGuiTexture.EMPTY);
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var position = getPosition();
        var size = getSize();
        NELDLibAe2StyleRenderer.drawAeSlotFrame(graphics, position.x, position.y, size.width, size.height);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public List<Component> getAdditionalToolTips(List<Component> list) {
        var fluid = fluidStorage.getFluid();
        long capacity = fluidStorage.getCapacity();
        if (fluid != null && !fluid.isEmpty()) {
            list.add(fluid.getDisplayName());
        } else {
            list.add(Component.translatable("gui.neoecoae.fluid_tank.empty"));
        }
        long amount = fluid == null ? 0L : fluid.getAmount();
        list.add(Component.translatable("gui.neoecoae.fluid_tank.amount", format(amount), format(capacity)));
        return list;
    }

    private static String format(long amount) {
        return String.format("%,d", amount);
    }
}
