package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class NEAe2TextButtonWidget extends ButtonWidget {
    private final Supplier<Component> labelSupplier;
    private final List<Supplier<Component>> labelFallbacks;
    private final BooleanSupplier selectedSupplier;
    private final BackgroundStyle style;
    private int normalColor = NELDLibStyle.DARK_TEXT_PRIMARY;
    private int selectedColor = NELDLibStyle.DARK_TEXT_SUCCESS;
    private int inactiveColor = NELDLibStyle.DARK_TEXT_MUTED;
    private boolean pressed;

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
        this(x, y, width, height, labelSupplier, List.of(), onPress, selectedSupplier, style);
    }

    public NEAe2TextButtonWidget(
            int x,
            int y,
            int width,
            int height,
            Supplier<Component> labelSupplier,
            List<Supplier<Component>> labelFallbacks,
            Consumer<ClickData> onPress,
            BooleanSupplier selectedSupplier) {
        this(x, y, width, height, labelSupplier, labelFallbacks, onPress, selectedSupplier, BackgroundStyle.INSET);
    }

    public NEAe2TextButtonWidget(
            int x,
            int y,
            int width,
            int height,
            Supplier<Component> labelSupplier,
            List<Supplier<Component>> labelFallbacks,
            Consumer<ClickData> onPress,
            BooleanSupplier selectedSupplier,
            BackgroundStyle style) {
        super(x, y, width, height, IGuiTexture.EMPTY, onPress);
        this.labelSupplier = labelSupplier;
        this.labelFallbacks = List.copyOf(labelFallbacks);
        this.selectedSupplier = selectedSupplier;
        this.style = style;
        setHoverTexture(IGuiTexture.EMPTY);
    }

    public NEAe2TextButtonWidget setTextColors(int normalColor, int selectedColor, int inactiveColor) {
        this.normalColor = normalColor;
        this.selectedColor = selectedColor;
        this.inactiveColor = inactiveColor;
        return this;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && button == 0) {
            pressed = true;
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (style == BackgroundStyle.TOOLBAR) {
            NELDLibClientStyle.drawAeToolbarButton(
                    graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), pressed);
        } else {
            NELDLibClientStyle.drawInsetButton(
                    graphics,
                    getPositionX(),
                    getPositionY(),
                    getSizeWidth(),
                    getSizeHeight(),
                    isMouseOverElement(mouseX, mouseY),
                    pressed,
                    selectedSupplier.getAsBoolean());
        }
    }

    @Override
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        var font = Minecraft.getInstance().font;
        int color = !isActive() ? inactiveColor : selectedSupplier.getAsBoolean() ? selectedColor : normalColor;
        int labelY = getPositionY() + (getSizeHeight() - font.lineHeight) / 2;
        NELDLibClientStyle.drawCenteredClipped(
                graphics, font, fittedLabel(), getPositionX(), labelY, getSizeWidth(), color);
    }

    private Component fittedLabel() {
        var font = Minecraft.getInstance().font;
        int maxWidth = Math.max(1, getSizeWidth() - 4);
        Component label = labelSupplier.get();
        if (font.width(label) <= maxWidth) {
            return label;
        }
        for (Supplier<Component> fallback : labelFallbacks) {
            Component fallbackLabel = fallback.get();
            if (font.width(fallbackLabel) <= maxWidth) {
                return fallbackLabel;
            }
        }
        return labelFallbacks.isEmpty()
                ? label
                : labelFallbacks.get(labelFallbacks.size() - 1).get();
    }

    public enum BackgroundStyle {
        INSET,
        TOOLBAR
    }
}
