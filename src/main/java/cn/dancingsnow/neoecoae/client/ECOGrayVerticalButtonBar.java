package cn.dancingsnow.neoecoae.client;

import appeng.client.Point;
import appeng.client.gui.widgets.VerticalButtonBar;
import cn.dancingsnow.neoecoae.NeoECOAE;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

/** Keeps AE2's button layout and lifecycle while using ECO's gray side rail. */
public final class ECOGrayVerticalButtonBar extends VerticalButtonBar {
    private static final int WIDTH = 23;
    private static final int TOP_HEIGHT = 30;
    private static final int MIDDLE_HEIGHT = 24;
    private static final int BOTTOM_HEIGHT = 27;
    private static final ResourceLocation TOP = texture("up");
    private static final ResourceLocation MIDDLE = texture("middle");
    private static final ResourceLocation BOTTOM = texture("down");

    private final List<Button> buttons = new ArrayList<>();

    @Override
    public void add(Button button) {
        super.add(button);
        buttons.add(button);
    }

    @Override
    public Rect2i getBounds() {
        Rect2i bounds = super.getBounds();
        if (bounds.getWidth() == 0) return bounds;
        return new Rect2i(bounds.getX() - 2, bounds.getY() - 1, WIDTH,
            Math.max(TOP_HEIGHT, bounds.getHeight() + 4));
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i screenBounds, Point mouse) {
        int visibleCount = (int) buttons.stream().filter(button -> button.visible).count();
        int index = 0;
        for (Button button : buttons) {
            if (!button.visible) continue;
            ResourceLocation texture = index == 0 ? TOP : index == visibleCount - 1 ? BOTTOM : MIDDLE;
            int height = index == 0 ? TOP_HEIGHT : index == visibleCount - 1 ? BOTTOM_HEIGHT : MIDDLE_HEIGHT;
            graphics.blit(texture, button.getX() - 4, button.getY() - (index == 0 ? 3 : 2),
                1, 0, 0, WIDTH, height, WIDTH, height);
            index++;
        }
    }

    private static ResourceLocation texture(String segment) {
        return NeoECOAE.id("textures/gui/common/side_bar/gray_button_slot_" + segment + ".png");
    }
}
