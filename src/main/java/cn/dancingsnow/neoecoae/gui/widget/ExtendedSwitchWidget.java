package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import it.unimi.dsi.fastutil.booleans.Boolean2ObjectFunction;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.BiConsumer;

@Accessors(chain = true)
public class ExtendedSwitchWidget extends SwitchWidget {

    @Nullable
    private IGuiTexture pressedMouseDownTexture;
    @Nullable
    private IGuiTexture mouseDownTexture;

    @Setter
    private Boolean2ObjectFunction<List<Component>> tooltipSupplier;

    private boolean lastPressed;
    private boolean mouseDown;

    public ExtendedSwitchWidget(int xPosition, int yPosition, int width, int height, BiConsumer<ClickData, Boolean> onPressed) {
        super(xPosition, yPosition, width, height, onPressed);
    }

    public ExtendedSwitchWidget setPressedMouseDownTexture(IGuiTexture... pressedMouseDownTexture) {
        this.pressedMouseDownTexture = new GuiTextureGroup(pressedMouseDownTexture);
        return this;
    }

    public ExtendedSwitchWidget setMouseDownTexture(IGuiTexture... mouseDownTexture) {
        this.mouseDownTexture = new GuiTextureGroup(mouseDownTexture);
        return this;
    }

    @Override
    public void initWidget() {
        super.initWidget();
        if (isRemote()) {
            setHoverTooltips(tooltipSupplier.get(isPressed));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            this.mouseDown = true;
            return true;
        } else {
            return false;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.mouseDown = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        if (lastPressed != isPressed) {
            this.lastPressed = isPressed;
            setHoverTooltips(tooltipSupplier.get(isPressed));
        }

        if (pressedMouseDownTexture != null) {
            pressedMouseDownTexture.updateTick();
        }
        if (mouseDownTexture != null) {
            mouseDownTexture.updateTick();
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        Position position = getPosition();
        Size size = getSize();
        if (!isPressed) {
            drawTexture(graphics, mouseX, mouseY, position, size, baseTexture, mouseDownTexture);
        } else {
            drawTexture(graphics, mouseX, mouseY, position, size, pressedTexture, pressedMouseDownTexture);
        }
        if (isMouseOverElement(mouseX, mouseY) && hoverTexture != null) {
            hoverTexture.draw(graphics, mouseX, mouseY, position.x, position.y, size.width, size.height);
        }
        drawOverlay(graphics, mouseX, mouseY, partialTicks);
    }

    @OnlyIn(Dist.CLIENT)
    private void drawTexture(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, Position position, Size size, IGuiTexture baseTexture, IGuiTexture mouseDownTexture) {
        if (mouseDown && mouseDownTexture != null) {
            mouseDownTexture.draw(graphics, mouseX, mouseY, position.x, position.y, size.width, size.height);
        } else if (baseTexture != null) {
            baseTexture.draw(graphics, mouseX, mouseY, position.x, position.y, size.width, size.height);
        }
    }
}
