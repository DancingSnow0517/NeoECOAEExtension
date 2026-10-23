package cn.dancingsnow.neoecoae.client.gui.workstation;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** AE2 19.2.18 artwork with AE2 15's original menus, settings and packet handlers. */
public final class WorkstationInterfaceArtwork {
    private WorkstationInterfaceArtwork() {}

    public static boolean active() {
        var client = Minecraft.getInstance();
        return client != null && client.screen instanceof LargeIntegratedWorkingStationPatternProviderScreen;
    }

    public static Blitter icon(Icon icon) {
        // The other retained icon rectangles are identical between the pinned AE2 releases.
        return switch (icon) {
            case WRENCH -> sprite(ECOIcon.PRIORITY.x, ECOIcon.PRIORITY.y, 16, 16);
            case WRENCH_DISABLED -> sprite(ECOIcon.COG_DISABLED.x, ECOIcon.COG_DISABLED.y, 16, 16);
            case HORIZONTAL_TAB -> sprite(128, 128, 22, 22);
            case HORIZONTAL_TAB_SELECTED -> sprite(128, 150, 22, 22);
            case HORIZONTAL_TAB_FOCUS -> sprite(150, 128, 22, 22);
            case TAB_BUTTON_BACKGROUND -> sprite(160, 192, 20, 20);
            case TOOLBAR_BUTTON_BACKGROUND -> sprite(176, 128, 18, 20);
            default -> sprite(icon.x, icon.y, icon.width, icon.height);
        };
    }

    public static Blitter buttonBackground(boolean hovered, boolean focused) {
        return sprite(hovered ? 212 : focused ? 194 : 176, 128, 18, 20);
    }

    public static Blitter scrollbar(boolean enabled) {
        return Blitter.texture(NeoECOAE.id("textures/gui/vendor/ae2/v19_2_18/small_scroller"
            + (enabled ? "" : "_disabled") + ".png"), 7, 15).src(0, 0, 7, 15).blending(true);
    }

    public static Blitter toolbarWing(int buttons) {
        int count = Math.min(10, Math.max(1, buttons));
        return Blitter.texture("guis/large_integrated_working_station_interface_controls.png", 256, 256)
            .src((count - 1) * 24, 0, 24, count * 22 + 10).blending(true);
    }

    private static Blitter sprite(int x, int y, int width, int height) {
        return Blitter.texture(ECOIcon.ATLAS, 256, 256).src(x, y, width, height).blending(true);
    }

    public static void renderButton(IconButton button, Icon icon, Item item, GuiGraphics graphics, int mouseX, int mouseY) {
        if (!button.visible) return;
        boolean hovered = mouseX >= button.getX() && mouseY >= button.getY()
            && mouseX < button.getX() + button.getWidth() && mouseY < button.getY() + button.getHeight();
        int offset = hovered ? 1 : 0;
        if (!button.isDisableBackground()) {
            var background = buttonBackground(hovered, button.isFocused());
            background.dest(button.getX() - (button.isHalfSize() ? 0 : 1),
                button.getY() + (button.isHalfSize() ? 0 : offset), 18, 20);
            drawAtDepth(graphics, background, 2);
        }
        if (item != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 3);
            graphics.renderItem(new ItemStack(item), button.getX(), button.getY() + 1 + offset);
            graphics.pose().popPose();
        } else if (icon != null) {
            var foreground = icon(icon);
            if (!button.active) foreground.opacity(0.5F);
            foreground.dest(button.getX(), button.getY() + (button.isHalfSize() ? 0 : 1 + offset));
            drawAtDepth(graphics, foreground, 3);
        }
    }

    private static void drawAtDepth(GuiGraphics graphics, Blitter blitter, int depth) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, depth);
            blitter.blit(graphics);
        } finally { graphics.pose().popPose(); }
    }
}
