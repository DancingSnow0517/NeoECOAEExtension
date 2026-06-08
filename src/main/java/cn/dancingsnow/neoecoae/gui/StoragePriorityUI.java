package cn.dancingsnow.neoecoae.gui;

import appeng.client.gui.Icon;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class StoragePriorityUI {
    private static final int[] POSITIVE_STEPS = {1, 10, 100, 1000};
    private static final int[] NEGATIVE_STEPS = {-1, -10, -100, -1000};

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
            layout.paddingAll(4);
            layout.gapAll(4);
            layout.width(214);
        }).addClass("panel_bg");

        UIElement titleBar = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
        });
        titleBar.addChild(new TextElement()
            .setText(Component.translatable("gui.neoecoae.storage_priority.title"))
            .textStyle(StoragePriorityUI::titleTextStyle));
        titleBar.addChild(new Button()
            .setText("X")
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.NONE)))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = tooltip("gui.neoecoae.storage_priority.close"))
            .layout(layout -> layout.width(16).height(16)));
        WindowDragHelper.setDragMove(titleBar, window, null, null);
        window.addChild(titleBar);

        window.addChild(buttonRow(config, POSITIVE_STEPS));
        window.addChild(priorityField(config));
        window.addChild(buttonRow(config, NEGATIVE_STEPS));
        window.addChild(helpText("gui.neoecoae.storage_priority.insert_hint"));
        window.addChild(helpText("gui.neoecoae.storage_priority.extract_hint"));
        return window;
    }

    public static UIElement createOpenButton(UIElement window) {
        UIElement priorityButtonPanel = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(-22);
            layout.top(26);
            layout.paddingAll(2);
            layout.paddingBottom(4);
        }).style(style -> style.background(NETextures.BACKGROUND));
        priorityButtonPanel.addChild(new Button()
            .noText()
            .addPostIcon(AETextures.icon(Icon.PRIORITY))
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.FLEX)))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = tooltip("gui.neoecoae.storage_priority.open"))
            .layout(layout -> {
                layout.width(18);
                layout.height(20);
            }));
        return priorityButtonPanel;
    }

    private static UIElement buttonRow(Config config, int[] steps) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
        });
        for (int step : steps) {
            row.addChild(priorityButton(step, config.changePriority()));
        }
        return row;
    }

    private static Button priorityButton(int step, IntConsumer changePriority) {
        Button button = new Button();
        button.setText(step > 0 ? "+" + step : String.valueOf(step));
        button.textStyle(StoragePriorityUI::buttonTextStyle);
        button.setOnServerClick(event -> changePriority.accept(step));
        button.layout(layout -> layout.width(step == 1000 || step == -1000 ? 50 : 36).height(18));
        return button;
    }

    private static TextField priorityField(Config config) {
        TextField field = new TextField();
        field.setNumbersOnlyInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
        field.setTextResponder(text -> parsePriority(text, config.setPriority()));
        field.bind(DataBindingBuilder.string(
            () -> String.valueOf(config.priority().getAsInt()),
            text -> parsePriority(text, config.setPriority())
        ).build());
        field.textFieldStyle(style -> style
            .textColor(0xffffff)
            .textShadow(false)
            .placeholder(Component.literal("0")));
        field.style(style -> style.backgroundTexture(NETextures.CARD_BACKGROUND));
        field.layout(layout -> {
            layout.width(96);
            layout.height(16);
            layout.alignSelf(AlignItems.CENTER);
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

    private static TextElement helpText(String key) {
        return new TextElement()
            .setText(Component.translatable(key))
            .textStyle(StoragePriorityUI::helpTextStyle);
    }

    private static HoverTooltips tooltip(String key) {
        return new HoverTooltips(List.of(Component.translatable(key)), null, null, null);
    }

    private static void titleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52).textShadow(false);
    }

    private static void buttonTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0xffffff).textShadow(false);
    }

    private static void helpTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52).textShadow(false);
    }
}
