package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.AlignItems;
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
    private static final int BUTTON_WIDTH = 18;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ICON_SIZE = 14;
    private static final int BUTTON_LEFT = 3;
    private static final int FIRST_BUTTON_TOP = 3;
    private static final int OTHER_BUTTON_TOP = 2;
    private static final int LEFT_BAR_TOP = 0;
    private static final int RIGHT_BAR_TOP = 1;
    private static final int LEFT_BAR_OFFSET = -21;
    private static final int RIGHT_BAR_OFFSET = -32;
    private static final int TOP_HEIGHT = 30;
    private static final int MIDDLE_HEIGHT = 24;
    private static final int BOTTOM_HEIGHT = 27;
    private static final int FIRST_SEGMENT_STEP = 23;
    private static final int MIDDLE_SEGMENT_STEP = 22;

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
            layout.top(side == Side.LEFT ? LEFT_BAR_TOP : RIGHT_BAR_TOP);
            layout.width(WIDTH);
            layout.height(totalHeight);
            if (side == Side.LEFT) {
                layout.left(LEFT_BAR_OFFSET);
            } else {
                layout.right(RIGHT_BAR_OFFSET);
            }
        });

        int buttonCount = buttons.size();
        for (int index = 0; index < buttonCount; index++) {
            int segmentTop = segmentTop(index);
            int segmentHeight = segmentHeight(index, buttonCount);
            IGuiTexture segmentTexture = texture(index, buttonCount, side);
            UIElement segment = new UIElement().layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(segmentTop);
                layout.width(WIDTH);
                layout.height(segmentHeight);
            }).style(style -> style.backgroundTexture(segmentTexture));
            applyButtonStyle(buttons.get(index), index == 0 ? FIRST_BUTTON_TOP : OTHER_BUTTON_TOP);
            segment.addChild(buttons.get(index));
            bar.addChild(segment);
        }
        return bar;
    }

    private static void applyButtonStyle(UIElement element, int top) {
        if (element instanceof Button button) {
            button.layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(BUTTON_LEFT);
                layout.top(top);
                layout.width(BUTTON_WIDTH);
                layout.height(BUTTON_HEIGHT);
            });
            button.setOverflowVisible(true);
            button.buttonStyle(style -> style
                    .baseTexture(NETextures.AE2_BUTTON)
                    .hoverTexture(NETextures.AE2_BUTTON_HIGHLIGHTED)
                    .pressedTexture(NETextures.AE2_BUTTON_HIGHLIGHTED));
            if (button.hasClass("eco-host-toolbar-button")) {
                for (UIElement child : button.getChildren()) {
                    if (child != button.text && child.getStyle().backgroundTexture() != null) {
                        child.layout(layout -> layout
                                .width(ICON_SIZE)
                                .height(ICON_SIZE)
                                .alignSelf(AlignItems.CENTER));
                    }
                }
            }
        }
    }

    private static int segmentTop(int index) {
        if (index == 0) {
            return 0;
        }
        return FIRST_SEGMENT_STEP + (index - 1) * MIDDLE_SEGMENT_STEP;
    }

    public static int heightFor(int buttonCount) {
        if (buttonCount <= 0) {
            throw new IllegalArgumentException("HostSideButtonBar requires at least one button");
        }
        if (buttonCount == 1) {
            return TOP_HEIGHT;
        }
        return FIRST_SEGMENT_STEP + (buttonCount - 2) * MIDDLE_SEGMENT_STEP + BOTTOM_HEIGHT;
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
