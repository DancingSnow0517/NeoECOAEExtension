package cn.dancingsnow.neoecoae.gui.workstation;

import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.workstation.IntegratedWorkstationUI.Config;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.IBindable;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.ColorRectTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.ItemStackTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.Horizontal;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.TextWrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.util.WindowDragHelper;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyDisplay;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Floating output-side editor, using the same native geometry and tab as the priority panel. */
final class WorkstationOutputPanel {
    private static final ResourceLocation JSON_LAYOUT =
        new ResourceLocation("neoecoae", "screens/workstation/output_panel");
    private static final String TITLE_KEY = "gui.neoecoae.integrated_working_station.allow_outputs";
    private static final IGuiTexture ENABLED_MARK = new ColorRectTexture(0xFF55C66B);
    private static final IGuiTexture DISABLED_MARK = new ColorRectTexture(0xFF5B596D);

    private WorkstationOutputPanel() {}

    static Button createOpenButton(UIElement window) {
        return HostSideButtonBar.createButton()
            .noText()
            .addPostIcon(NETextures.OUTPUTS)
            .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.FLEX)))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable(TITLE_KEY).withStyle(ChatFormatting.WHITE)), null, null, null));
    }

    static UIElement create(Config config) {
        JsonContext context = new JsonContext(config);
        UIElement window = WorkstationUiJson.load(JSON_LAYOUT, context);
        context.attach(window);
        return window;
    }

    private static UIElement sideControl(Config config, RelativeSide uiSide) {
        String labelKey = "gui.neoecoae.relative_side." + uiSide.name().toLowerCase(Locale.ROOT);
        UIElement cell = new UIElement();
        cell.addChild(new OutputSideButton(config, internalSide(uiSide), labelKey)
            .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(10).top(0).width(20).height(20).paddingAll(0)));
        cell.addChild(new TextElement()
            .setText(labelKey, true)
            .textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false)
                .textWrap(TextWrap.NONE).textAlignHorizontal(Horizontal.CENTER)
                .textColor(0x413f54).textShadow(false))
            .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(22).width(40).height(10)));
        return cell;
    }

    private static RelativeSide internalSide(RelativeSide uiSide) {
        // AE2's left/right are relative to the machine, while the panel faces the player.
        return switch (uiSide) {
            case LEFT -> RelativeSide.RIGHT;
            case RIGHT -> RelativeSide.LEFT;
            default -> uiSide;
        };
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final Config config;
        private UIElement window;
        private UIElement dragBar;

        private JsonContext(Config config) {
            this.config = config;
        }

        @Override
        public UIElement createCustom(JsonObject node) {
            return switch (node.get("custom").getAsString()) {
                case "drag_bar" -> {
                    dragBar = new UIElement();
                    yield dragBar;
                }
                case "title" -> new TextElement().setText(TITLE_KEY, true)
                    .textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false)
                        .textWrap(TextWrap.HOVER_ROLL).textColor(0x413f54).textShadow(false));
                case "back" -> new OutputBackButton()
                    .setOnClick(event -> window.layout(layout -> layout.display(TaffyDisplay.NONE)))
                    .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                        List.of(Component.translatable("gui.back")), null, null, null));
                case "output_side" -> sideControl(config,
                    RelativeSide.valueOf(node.get("side").getAsString().toUpperCase(Locale.ROOT)));
                default -> throw new IllegalArgumentException("Unknown workstation output panel custom element: "
                    + node.get("custom").getAsString());
            };
        }

        private void attach(UIElement window) {
            this.window = window;
            window.layout(layout -> layout.display(TaffyDisplay.NONE));
            if (dragBar != null) WindowDragHelper.setDragMove(dragBar, window, null, null);
        }
    }

    private static final class OutputSideButton extends Button implements IBindable<Boolean> {
        private final Config config;
        private final RelativeSide side;
        private boolean allowed;

        private OutputSideButton(Config config, RelativeSide side, String labelKey) {
            this.config = config;
            this.side = side;
            allowed = config.outputAllowed().test(side);
            noText();
            addClass("workstation-output-side");
            setOnClick(event -> setValue(!allowed));
            setOnServerClick(event -> config.toggleOutput().accept(side));
            bind(DataBindingBuilder.boolS2C(() -> config.outputAllowed().test(side)).build());
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable(labelKey).withStyle(ChatFormatting.WHITE),
                    Component.translatable(TITLE_KEY + (allowed ? ".enabled" : ".disabled"))
                        .withStyle(ChatFormatting.GRAY)), null, null, null));
        }

        @Override
        public Boolean getValue() {
            return allowed;
        }

        @Override
        public OutputSideButton setValue(Boolean value) {
            allowed = Boolean.TRUE.equals(value);
            config.syncOutput().accept(side, allowed);
            return this;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            IGuiTexture background = !isActive() ? NETextures.AE2_BUTTON_DISABLED
                : allowed || getState() != State.DEFAULT ? NETextures.AE2_BUTTON_HIGHLIGHTED : NETextures.AE2_BUTTON;
            context.drawTexture(background, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());

            ItemStack neighbor = config.adjacentItem().apply(side);
            IGuiTexture icon = neighbor.isEmpty()
                ? AETextures.icon(allowed ? ECOIcon.AUTO_EXPORT_ON : ECOIcon.AUTO_EXPORT_OFF)
                : new ItemStackTexture(neighbor);
            context.drawTexture(icon, getPositionX() + 2, getPositionY() + 1, 16, 16);
            // Keep the selected state visible even while hovering or displaying a neighbor item.
            context.drawTexture(allowed ? ENABLED_MARK : DISABLED_MARK,
                getPositionX() + 14, getPositionY() + 13, 3, 3);
        }
    }

    private static final class OutputBackButton extends Button {
        private OutputBackButton() {
            noText();
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            context.drawTexture(AETextures.icon(isFocused()
                    ? ECOIcon.TAB_BUTTON_BACKGROUND_FOCUS : ECOIcon.TAB_BUTTON_BACKGROUND),
                getPositionX(), getPositionY(), isFocused() ? 22 : 20, isFocused() ? 22 : 20);
            context.drawTexture(AETextures.icon(ECOIcon.BACK), getPositionX() + 2, getPositionY() + 1, 16, 16);
        }
    }
}
