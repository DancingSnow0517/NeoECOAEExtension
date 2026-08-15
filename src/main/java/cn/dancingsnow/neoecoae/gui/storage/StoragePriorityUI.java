package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;

import appeng.client.gui.Icon;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class StoragePriorityUI {
    private static final int[] POSITIVE_STEPS = {1, 10, 100, 1000};
    private static final int[] NEGATIVE_STEPS = {-1, -10, -100, -1000};
    private static final int WINDOW_WIDTH = 176;
    private static final int WINDOW_HEIGHT = 125;
    private static final int BUTTON_TOP = 30;
    private static final int BUTTON_BOTTOM = 72;
    private static final int[] BUTTON_X = {20, 48, 82, 120};
    private static final int[] BUTTON_WIDTH = {22, 28, 32, 38};

    private StoragePriorityUI() {
    }

    public record Config(
        IntSupplier priority,
        IntConsumer setPriority,
        IntConsumer changePriority
    ) {
    }

    public static UIElement createFloatingPanel(Config config) {
        UIElement window = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(6);
            layout.top(6);
            layout.display(TaffyDisplay.NONE);
            layout.width(WINDOW_WIDTH);
            layout.height(WINDOW_HEIGHT);
        }).setOverflowVisible(true)
            .style(style -> style.backgroundTexture(NETextures.PRIORITY_BACKGROUND));

        UIElement titleBar = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(WINDOW_WIDTH);
            layout.height(20);
        });
        titleBar.addChild(new TextElement()
            .setText(Component.translatable("gui.ae2.Priority"))
            .textStyle(StoragePriorityUI::titleTextStyle)
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8)
                .top(6)
                .width(140)
                .height(12)));
        titleBar.addChild(new PriorityBackButton()
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.NONE)))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = tooltip("gui.neoecoae.storage_priority.close"))
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(152)
                .top(-5)
                .width(20)
                .height(20)));
        WindowDragHelper.setDragMove(titleBar, window, null, null);
        window.addChild(titleBar);

        addAmountButtons(window, config, POSITIVE_STEPS, BUTTON_TOP);
        window.addChild(priorityField(config));
        addAmountButtons(window, config, NEGATIVE_STEPS, BUTTON_BOTTOM);
        window.addChild(helpText("gui.ae2.PriorityInsertionHint", 98));
        window.addChild(helpText("gui.ae2.PriorityExtractionHint", 110));
        return window;
    }

    public static UIElement createOpenButton(UIElement window) {
        return HostSideButtonBar.left(createInlineOpenButton(window));
    }

    public static Button createInlineOpenButton(UIElement window) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPostIcon(AETextures.icon(Icon.PRIORITY))
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.FLEX)));
        button.getChildren().get(button.getChildren().size() - 1).layout(layout -> layout
            .width(14)
            .height(14));
        button.addEventListener(UIEvents.HOVER_TOOLTIPS,
            event -> event.hoverTooltips = tooltip("gui.neoecoae.storage_priority.open"));
        button.layout(layout -> {
            layout.width(18);
            layout.height(20);
        });
        return button;
    }

    private static void addAmountButtons(UIElement window, Config config, int[] steps, int top) {
        for (int index = 0; index < steps.length; index++) {
            int buttonX = BUTTON_X[index];
            int buttonWidth = BUTTON_WIDTH[index];
            window.addChild(priorityButton(steps[index], config.changePriority())
                .layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(buttonX)
                    .top(top)
                    .width(buttonWidth)
                    .height(20)));
        }
    }

    private static PriorityAmountButton priorityButton(int step, IntConsumer changePriority) {
        PriorityAmountButton button = new PriorityAmountButton(step > 0 ? "+" + step : String.valueOf(step));
        button.setOnServerClick(event -> changePriority.accept(step));
        return button;
    }

    private static TextField priorityField(Config config) {
        TextField field = new PriorityTextField();
        field.setNumbersOnlyInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
        field.setTextResponder(text -> parsePriority(text, config.setPriority()));
        field.bind(DataBindingBuilder.string(
            () -> String.valueOf(config.priority().getAsInt()),
            text -> parsePriority(text, config.setPriority())
        ).build());
        field.textFieldStyle(style -> style
            .textColor(0xf2f2f2)
            .textShadow(false)
            .focusOverlay(IGuiTexture.EMPTY)
            .placeholder(Component.literal("0")));
        field.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(60);
            layout.top(55);
            layout.width(61);
            layout.height(12);
        });
        return field;
    }

    private static void parsePriority(String text, IntConsumer setPriority) {
        if (text == null || text.isEmpty() || "-".equals(text) || "+".equals(text)) {
            return;
        }
        try {
            setPriority.accept(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
        }
    }

    private static UIElement helpText(String key, int top) {
        return new TextElement()
            .setText(Component.translatable(key))
            .textStyle(StoragePriorityUI::helpTextStyle)
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8)
                .top(top)
                .width(160)
                .height(12));
    }

    private static HoverTooltips tooltip(String key) {
        return new HoverTooltips(List.of(Component.translatable(key)), null, null, null);
    }

    private static void titleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(false).textWrap(TextWrap.NONE).textColor(0x413f54).textShadow(false);
    }

    private static void helpTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(false).textWrap(TextWrap.NONE).textColor(0x413f54).textShadow(false);
    }

    private static final class PriorityAmountButton extends Button {
        private final String label;

        private PriorityAmountButton(String label) {
            this.label = label;
            noText();
        }

        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            IGuiTexture texture;
            if (!isActive()) {
                texture = NETextures.AE2_BUTTON_DISABLED;
            } else {
                texture = getState() == State.DEFAULT
                    ? NETextures.AE2_BUTTON
                    : NETextures.AE2_BUTTON_HIGHLIGHTED;
            }
            guiContext.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            super.drawContents(guiContext);

            Font font = Minecraft.getInstance().font;
            int x = Math.round(getPositionX());
            int y = Math.round(getPositionY());
            int width = Math.round(getSizeWidth());
            int height = Math.round(getSizeHeight());
            int textWidth = font.width(label);
            int textX = x + width / 2 - textWidth / 2;
            int textY = y + (height - 9) / 2 + 1;
            int yOffset;
            int color;
            if (!isActive()) {
                yOffset = -1;
                color = 0xFF413F54;
            } else if (getState() == State.DEFAULT) {
                yOffset = 1;
                color = 0xFFF2F2F2;
            } else {
                yOffset = 0;
                color = 0xFF517497;
            }
            guiContext.graphics.drawString(font, label, textX, textY - yOffset, color, false);
        }
    }

    private static final class PriorityBackButton extends Button {
        private PriorityBackButton() {
            noText();
        }

        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            IGuiTexture texture = isFocused()
                ? AETextures.icon(Icon.TAB_BUTTON_BACKGROUND_FOCUS)
                : AETextures.icon(Icon.TAB_BUTTON_BACKGROUND);
            float size = isFocused() ? 22 : 20;
            guiContext.drawTexture(texture, getPositionX(), getPositionY(), size, size);
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            super.drawContents(guiContext);
            guiContext.drawTexture(AETextures.icon(Icon.BACK),
                getPositionX() + 2,
                getPositionY() + 1,
                16,
                16);
        }
    }

    private static final class PriorityTextField extends TextField {
        @Override
        public void drawBackgroundTexture(GUIContext guiContext) {
            IGuiTexture texture = !isActive()
                ? NETextures.PRIORITY_TEXT_FIELD_DISABLED
                : isFocused() ? NETextures.PRIORITY_TEXT_FIELD_FOCUS : NETextures.PRIORITY_TEXT_FIELD;
            guiContext.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }
    }
}
