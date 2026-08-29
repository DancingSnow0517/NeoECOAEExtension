package cn.dancingsnow.neoecoae.gui.common;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.Arrays;
import java.util.List;

/** A reusable vertical control rail attached to the side of a host UI. */
public final class HostSideButtonBar {
    public enum Side {
        LEFT,
        RIGHT
    }

    private static final int WIDTH = 23;
    private static final int BUTTON_WIDTH = 16;
    private static final int BUTTON_HEIGHT = 16;
    private static final int ICON_SIZE = 14;
    private static final int BUTTON_LEFT = 4;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_LEFT = 2;
    private static final int FIRST_BUTTON_TOP = 3;
    private static final int OTHER_BUTTON_TOP = 2;
    private static final int LEFT_BAR_TOP = 0;
    private static final int RIGHT_BAR_TOP = 1;
    private static final int LEFT_BAR_OFFSET = -21;
    private static final int RIGHT_BAR_OFFSET = -32;
    private static final int RIGHT_SLOT_BAR_OFFSET = -21;
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

    /** Creates a mirrored right-side rail while preserving standard 18x18 slot rendering. */
    public static UIElement rightSlots(List<? extends UIElement> slots) {
        return create(Side.RIGHT, slots, ContentType.SLOT);
    }

    public static UIElement create(Side side, UIElement... buttons) {
        return create(side, Arrays.asList(buttons));
    }

    /** Creates a button with AE2's 16x16 hitbox and 18x20 visual rendering. */
    public static Button createButton() {
        return new AE2IconButton();
    }

    public static UIElement create(Side side, List<? extends UIElement> buttons) {
        return create(side, buttons, ContentType.BUTTON);
    }

    private static UIElement create(Side side, List<? extends UIElement> elements, ContentType contentType) {
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("HostSideButtonBar requires at least one element");
        }
        int barTop = contentType == ContentType.SLOT
            ? LEFT_BAR_TOP
            : side == Side.LEFT ? LEFT_BAR_TOP : RIGHT_BAR_TOP;
        int rightOffset = contentType == ContentType.SLOT ? RIGHT_SLOT_BAR_OFFSET : RIGHT_BAR_OFFSET;

        UIElement bar = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.top(barTop);
            layout.width(WIDTH);
            layout.height(heightFor(elements.size()));
            if (side == Side.LEFT) {
                layout.left(LEFT_BAR_OFFSET);
            } else {
                layout.right(rightOffset);
            }
        });

        int elementCount = elements.size();
        for (int index = 0; index < elementCount; index++) {
            int segmentIndex = index;
            int segmentTop = segmentTop(segmentIndex);
            int segmentHeight = segmentHeight(segmentIndex, elementCount);
            IGuiTexture segmentTexture = texture(segmentIndex, elementCount, side, contentType);
            UIElement segment = new UIElement().layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(segmentTop);
                layout.width(WIDTH);
                layout.height(segmentHeight);
            }).style(style -> style.backgroundTexture(segmentTexture));
            UIElement element = elements.get(segmentIndex);
            int elementTop = segmentIndex == 0 ? FIRST_BUTTON_TOP : OTHER_BUTTON_TOP;
            if (contentType == ContentType.SLOT) {
                applySlotStyle(element, elementTop);
            } else {
                applyButtonStyle(element, elementTop);
            }
            segment.addChild(element);
            bar.addChild(segment);
        }
        return bar;
    }

    private static void applySlotStyle(UIElement slot, int top) {
        slot.style(style -> style.backgroundTexture(AETextures.slotWithFrame()));
        slot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(SLOT_LEFT);
            layout.top(top);
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
        });
    }

    private static void applyButtonStyle(UIElement element, int top) {
        if (!(element instanceof Button button)) {
            return;
        }
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(BUTTON_LEFT);
            layout.top(top);
            layout.width(BUTTON_WIDTH);
            layout.height(BUTTON_HEIGHT);
        });
        button.setOverflowVisible(true);
        for (UIElement child : button.getChildren()) {
            if (child != button.text && child.getStyle().backgroundTexture() != null) {
                child.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(1)
                    .top(3)
                    .width(ICON_SIZE)
                    .height(ICON_SIZE));
            }
        }
    }

    private static final class AE2IconButton extends Button {
        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            IGuiTexture background = switch (getState()) {
                case DEFAULT -> isFocused()
                    ? AETextures.icon(Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS)
                    : AETextures.icon(Icon.TOOLBAR_BUTTON_BACKGROUND);
                case HOVERED, PRESSED -> AETextures.icon(Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER);
            };
            guiContext.drawTexture(background, getPositionX() - 1, getPositionY(), 18, 20);
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            int yOffset = getState() == State.DEFAULT ? 0 : 1;
            if (yOffset == 0) {
                super.drawContents(guiContext);
                return;
            }
            guiContext.pose.pushPose();
            guiContext.pose.translate(0, yOffset, 0);
            super.drawContents(guiContext);
            guiContext.pose.popPose();
        }
    }

    private enum ContentType {
        BUTTON,
        SLOT
    }

    private static int segmentTop(int index) {
        return index == 0 ? 0 : FIRST_SEGMENT_STEP + (index - 1) * MIDDLE_SEGMENT_STEP;
    }

    public static int heightFor(int buttonCount) {
        if (buttonCount <= 0) {
            throw new IllegalArgumentException("HostSideButtonBar requires at least one button");
        }
        return buttonCount == 1
            ? TOP_HEIGHT
            : FIRST_SEGMENT_STEP + (buttonCount - 2) * MIDDLE_SEGMENT_STEP + BOTTOM_HEIGHT;
    }

    private static int segmentHeight(int index, int buttonCount) {
        if (index == 0) {
            return TOP_HEIGHT;
        }
        return index == buttonCount - 1 ? BOTTOM_HEIGHT : MIDDLE_HEIGHT;
    }

    private static IGuiTexture texture(int index, int buttonCount, Side side, ContentType contentType) {
        String fileName;
        if (index == 0) {
            fileName = "button_slot_up.png";
        } else if (index == buttonCount - 1) {
            fileName = "button_slot_down.png";
        } else {
            fileName = "button_slot_middle.png";
        }
        if (side == Side.RIGHT && contentType == ContentType.SLOT) {
            fileName = "mirrored_" + fileName;
        }
        return SpriteTexture.of(NeoECOAE.id("textures/gui/" + fileName));
    }
}
