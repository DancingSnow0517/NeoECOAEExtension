package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.common.HostSubWindowControls;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.TextWrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextField;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.util.WindowDragHelper;
import com.google.gson.JsonObject;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyDisplay;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
    private static final ResourceLocation JSON_LAYOUT =
        new ResourceLocation("neoecoae", "screens/storage/storage_priority");

    private StoragePriorityUI() {
    }

    public record Config(
        IntSupplier priority,
        IntConsumer setPriority,
        IntConsumer changePriority
    ) {
    }

    public static UIElement createFloatingPanel(Config config) {
        JsonContext context = new JsonContext(config);
        UIElement window = NeoUiJsonLoader.load(JSON_LAYOUT, context);
        context.attach(window);
        return window;
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final Config config;
        private UIElement window;
        private UIElement dragBar;

        private JsonContext(Config config) {
            this.config = config;
        }

        @Override
        public UIElement createCustom(JsonObject definition) {
            String custom = definition.get("custom").getAsString();
            return switch (custom) {
                case "drag_bar" -> {
                    dragBar = new UIElement();
                    yield dragBar;
                }
                case "title" -> new TextElement()
                    .setText(Component.translatable("gui.ae2.Priority"))
                    .textStyle(StoragePriorityUI::titleTextStyle);
                case "back" -> HostSubWindowControls.backButton(() -> window.layout(layout -> layout.display(TaffyDisplay.NONE)))
                    .addEventListener(UIEvents.HOVER_TOOLTIPS,
                        event -> event.hoverTooltips = tooltip("gui.neoecoae.storage_priority.close"));
                case "priority_button" -> priorityButton(definition.get("step").getAsInt(), config.changePriority());
                case "priority_field" -> priorityField(config);
                case "help" -> helpText(definition.get("key").getAsString(), 0);
                default -> throw new IllegalArgumentException("Unknown storage priority custom element: " + custom);
            };
        }

        @Override
        public void configure(JsonObject definition, UIElement element) {
            if ("storage-priority-root".equals(definition.has("id") ? definition.get("id").getAsString() : "")) {
                element.setOverflowVisible(true);
                element.layout(layout -> layout.display(TaffyDisplay.NONE));
            }
        }

        private void attach(UIElement window) {
            this.window = window;
            if (dragBar != null) WindowDragHelper.setDragMove(dragBar, window, null, null);
        }
    }

    public static UIElement createOpenButton(UIElement window) {
        return HostSideButtonBar.left(createInlineOpenButton(window));
    }

    public static Button createInlineOpenButton(UIElement window) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPostIcon(AETextures.icon(ECOIcon.PRIORITY))
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.FLEX)));
        button.setId("storage-priority-open");
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

    private static Button priorityButton(int step, IntConsumer changePriority) {
        Button button = HostSubWindowControls.textButton(() ->
            Component.literal(step > 0 ? "+" + step : String.valueOf(step)));
        button.setOnServerClick(event -> changePriority.accept(step));
        return button;
    }

    private static TextField priorityField(Config config) {
        TextField field = HostSubWindowControls.integerField(Integer.MIN_VALUE, Integer.MAX_VALUE);
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

}
