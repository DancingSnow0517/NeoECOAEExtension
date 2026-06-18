package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class NEAe2TextButtonWidget extends ButtonWidget {
    private final Supplier<Component> labelSupplier;
    private final BooleanSupplier selectedSupplier;
    private final BackgroundStyle style;
    private int normalColor = NELDLibStyle.DARK_TEXT_PRIMARY;
    private int selectedColor = NELDLibStyle.DARK_TEXT_SUCCESS;
    private int inactiveColor = NELDLibStyle.DARK_TEXT_MUTED;

    public NEAe2TextButtonWidget(
            int x,
            int y,
            int width,
            int height,
            Supplier<Component> labelSupplier,
            Consumer<ClickData> onPress,
            BooleanSupplier selectedSupplier) {
        this(x, y, width, height, labelSupplier, onPress, selectedSupplier, BackgroundStyle.INSET);
    }

    public NEAe2TextButtonWidget(
            int x,
            int y,
            int width,
            int height,
            Supplier<Component> labelSupplier,
            Consumer<ClickData> onPress,
            BooleanSupplier selectedSupplier,
            BackgroundStyle style) {
        super(x, y, width, height, IGuiTexture.EMPTY, onPress);
        this.labelSupplier = labelSupplier;
        this.selectedSupplier = selectedSupplier;
        this.style = style;
        setHoverTexture(IGuiTexture.EMPTY);
        setClickedTexture(IGuiTexture.EMPTY);
    }

    public NEAe2TextButtonWidget setTextColors(int normalColor, int selectedColor, int inactiveColor) {
        this.normalColor = normalColor;
        this.selectedColor = selectedColor;
        this.inactiveColor = inactiveColor;
        return this;
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (style == BackgroundStyle.TOOLBAR) {
            NELDLibClientStyle.drawAeToolbarButton(
                    graphics,
                    mouseX,
                    mouseY,
                    getPositionX(),
                    getPositionY(),
                    getSizeWidth(),
                    getSizeHeight(),
                    isClicked());
        } else {
            NELDLibClientStyle.drawInsetButton(
                    graphics,
                    getPositionX(),
                    getPositionY(),
                    getSizeWidth(),
                    getSizeHeight(),
                    isMouseOverElement(mouseX, mouseY),
                    isClicked(),
                    selectedSupplier.getAsBoolean());
        }
    }

    @Override
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        var font = Minecraft.getInstance().font;
        int color = !isActive() ? inactiveColor : selectedSupplier.getAsBoolean() ? selectedColor : normalColor;
        int labelY = getPositionY() + (getSizeHeight() - font.lineHeight) / 2;
        NELDLibClientStyle.drawCenteredFitted(
                graphics, font, labelSupplier.get(), getPositionX(), labelY, getSizeWidth(), color);
    }

    public enum BackgroundStyle {
        INSET,
        TOOLBAR
    }
}
