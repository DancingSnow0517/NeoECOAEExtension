package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.ColorBorderTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;

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
    private static final int ICON_SIZE = 16;
    private static final int BUTTON_LEFT = 4;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_BAR_PADDING_TOP = 5;
    private static final int SLOT_BAR_PADDING_BOTTOM = 7;
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

    public static UIElement grayLeft(UIElement... buttons) {
        return create(Side.LEFT, Arrays.asList(buttons), "gray_");
    }

    public static UIElement right(UIElement... buttons) {
        return create(Side.RIGHT, buttons);
    }

    public static UIElement right(List<? extends UIElement> buttons) {
        return create(Side.RIGHT, buttons);
    }

    /** Matches AE2's contiguous upgrade column, including its shared white frame. */
    public static UIElement rightSlots(List<? extends UIElement> slots) {
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("HostSideButtonBar requires at least one slot");
        }
        int slotHeight = slots.size() * SLOT_SIZE;
        UIElement bar = new UIElement().layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .top(LEFT_BAR_TOP)
            .right(RIGHT_SLOT_BAR_OFFSET)
            .width(WIDTH)
            .height(SLOT_BAR_PADDING_TOP + slotHeight + SLOT_BAR_PADDING_BOTTOM))
            .style(style -> style.backgroundTexture(SpriteTexture.of(
                NeoECOAE.id("textures/gui/common/side_bar/mirrored_button_slot_up.png"))
                .setSprite(0, 0, WIDTH, TOP_HEIGHT)
                .setBorder(0, SLOT_BAR_PADDING_TOP, 0, SLOT_BAR_PADDING_BOTTOM)));

        // AE2 draws the frame around the whole column, not around individual slots.
        UIElement column = new UIElement().layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(0)
            .top(SLOT_BAR_PADDING_TOP)
            .width(SLOT_SIZE)
            .height(slotHeight))
            .style(style -> style.backgroundTexture(new ColorBorderTexture(-1, 0xFFF2F2F2)));
        for (int index = 0; index < slots.size(); index++) {
            UIElement slot = slots.get(index);
            applySlotStyle(slot, index * SLOT_SIZE);
            column.addChild(slot);
        }
        return bar.addChild(column);
    }

    public static UIElement create(Side side, UIElement... buttons) {
        return create(side, Arrays.asList(buttons));
    }

    /** Creates a button with AE2's 16x16 hitbox and 18x20 visual rendering. */
    public static Button createButton() {
        return new AE2IconButton();
    }

    public static void placeButton(Button button, int left, int top) {
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(left);
            layout.top(top);
            layout.width(BUTTON_WIDTH);
            layout.height(BUTTON_HEIGHT);
            layout.paddingAll(0);
        });
        button.setOverflowVisible(true);
        for (UIElement child : button.getChildren()) {
            if (child != button.text && child.getStyle().backgroundTexture() != null) {
                child.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(0)
                    .top(1)
                    .width(ICON_SIZE)
                    .height(ICON_SIZE));
                child.style(style -> style.zIndex(3));
            }
        }
    }

    public static UIElement create(Side side, List<? extends UIElement> buttons) {
        return create(side, buttons, "");
    }

    private static UIElement create(Side side, List<? extends UIElement> elements, String texturePrefix) {
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("HostSideButtonBar requires at least one element");
        }
        int barTop = side == Side.LEFT ? LEFT_BAR_TOP : RIGHT_BAR_TOP;

        UIElement bar = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.top(barTop);
            layout.width(WIDTH);
            layout.height(heightFor(elements.size()));
            if (side == Side.LEFT) {
                layout.left(LEFT_BAR_OFFSET);
            } else {
                layout.right(RIGHT_BAR_OFFSET);
            }
        });

        int elementCount = elements.size();
        for (int index = 0; index < elementCount; index++) {
            int segmentIndex = index;
            int segmentTop = segmentTop(segmentIndex);
            int segmentHeight = segmentHeight(segmentIndex, elementCount);
            IGuiTexture segmentTexture = texture(segmentIndex, elementCount, texturePrefix);
            UIElement segment = new UIElement().layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(segmentTop);
                layout.width(WIDTH);
                layout.height(segmentHeight);
            }).style(style -> style.backgroundTexture(segmentTexture));
            UIElement element = elements.get(segmentIndex);
            int elementTop = segmentIndex == 0 ? FIRST_BUTTON_TOP : OTHER_BUTTON_TOP;
            applyButtonStyle(element, elementTop);
            segment.addChild(element);
            bar.addChild(segment);
        }
        return bar;
    }

    private static void applySlotStyle(UIElement slot, int top) {
        slot.style(style -> style.backgroundTexture(AETextures.icon(ECOIcon.SLOT_BACKGROUND)));
        slot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(top);
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
        });
    }

    private static void applyButtonStyle(UIElement element, int top) {
        if (!(element instanceof Button button)) {
            return;
        }
        placeButton(button, BUTTON_LEFT, top);
    }

    private static final class AE2IconButton extends Button {
        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            ECOIcon background = switch (getState()) {
                case DEFAULT -> isFocused()
                    ? ECOIcon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                    : ECOIcon.TOOLBAR_BUTTON_BACKGROUND;
                case HOVERED, PRESSED -> ECOIcon.TOOLBAR_BUTTON_BACKGROUND_HOVER;
            };
            // Match ECOCraftConfirmScreen.CraftingGraphButton's native atlas rendering.
            // drawContents applies the shared hover offset to the background and icon.
            guiContext.graphics.flush();
            background.blit(guiContext.graphics, Math.round(getPositionX()) - 1,
                Math.round(getPositionY()), 2);
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

    private static IGuiTexture texture(int index, int buttonCount, String texturePrefix) {
        String fileName;
        if (index == 0) {
            fileName = "button_slot_up.png";
        } else if (index == buttonCount - 1) {
            fileName = "button_slot_down.png";
        } else {
            fileName = "button_slot_middle.png";
        }
        return SpriteTexture.of(NeoECOAE.id("textures/gui/common/side_bar/" + texturePrefix + fileName));
    }
}
