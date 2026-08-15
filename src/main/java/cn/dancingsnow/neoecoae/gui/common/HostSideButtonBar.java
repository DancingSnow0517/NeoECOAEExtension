package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.Arrays;
import java.util.List;

/** A reusable vertical button rail attached to the side of a host UI. */
public final class HostSideButtonBar {
    public enum Side {
        LEFT,
        RIGHT
    }

    private static final int WIDTH = 23;
    private static final int TOP_HEIGHT = 30;
    private static final int MIDDLE_HEIGHT = 24;
    private static final int BOTTOM_HEIGHT = 27;

    private HostSideButtonBar() {
    }

    public static UIElement left(UIElement... buttons) {
        return create(Side.LEFT, buttons);
    }

    public static UIElement left(List<? extends UIElement> buttons) {
        return create(Side.LEFT, buttons);
    }

    public static UIElement right(UIElement... buttons) {
        return create(Side.RIGHT, buttons);
    }

    public static UIElement right(List<? extends UIElement> buttons) {
        return create(Side.RIGHT, buttons);
    }

    public static UIElement create(Side side, UIElement... buttons) {
        return create(side, Arrays.asList(buttons));
    }

    public static UIElement create(Side side, List<? extends UIElement> buttons) {
        if (buttons.isEmpty()) {
            throw new IllegalArgumentException("HostSideButtonBar requires at least one button");
        }

        int totalHeight = heightFor(buttons.size());
        UIElement bar = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.top(0);
            layout.width(WIDTH);
            layout.height(totalHeight);
            if (side == Side.LEFT) {
                layout.left(-WIDTH);
            } else {
                layout.right(-WIDTH);
            }
        }).setAllowHitTest(false);

        int top = 0;
        int buttonCount = buttons.size();
        for (int index = 0; index < buttonCount; index++) {
            int segmentHeight = segmentHeight(index, buttonCount);
            int segmentTop = top;
            IGuiTexture segmentTexture = texture(index, buttonCount, side);
            UIElement segment = new UIElement().layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(segmentTop);
                layout.width(WIDTH);
                layout.height(segmentHeight);
                layout.flexDirection(FlexDirection.ROW);
                layout.alignItems(AlignItems.CENTER);
                layout.justifyContent(AlignContent.CENTER);
            }).style(style -> style.backgroundTexture(segmentTexture))
                .setAllowHitTest(false);
            segment.addChild(buttons.get(index));
            bar.addChild(segment);
            top += segmentHeight;
        }
        return bar;
    }

    public static int heightFor(int buttonCount) {
        if (buttonCount <= 0) {
            throw new IllegalArgumentException("HostSideButtonBar requires at least one button");
        }
        if (buttonCount == 1) {
            return TOP_HEIGHT;
        }
        return TOP_HEIGHT + (buttonCount - 2) * MIDDLE_HEIGHT + BOTTOM_HEIGHT;
    }

    private static int segmentHeight(int index, int buttonCount) {
        if (index == 0) {
            return TOP_HEIGHT;
        }
        if (index == buttonCount - 1) {
            return BOTTOM_HEIGHT;
        }
        return MIDDLE_HEIGHT;
    }

    private static IGuiTexture texture(int index, int buttonCount, Side side) {
        String fileName;
        if (index == 0) {
            fileName = "button_slot_up.png";
        } else if (index == buttonCount - 1) {
            fileName = "button_slot_down.png";
        } else {
            fileName = "button_slot_middle.png";
        }

        SpriteTexture texture = SpriteTexture.of(NeoECOAE.id("textures/gui/" + fileName));
        if (side == Side.RIGHT) {
            texture.scale(-1, 1);
        }
        return texture;
    }
}
