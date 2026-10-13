package cn.dancingsnow.neoecoae.gui.workstation;

import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import static cn.dancingsnow.neoecoae.gui.json.JsonAttrs.string;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.theme.Palette;
import cn.dancingsnow.neoecoae.gui.workstation.IntegratedWorkstationUI.Config;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Floating output-side editor and its open button. */
final class WorkstationOutputPanel {
    private WorkstationOutputPanel() {}
    private static final ResourceLocation JSON_LAYOUT =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "screens/workstation/output_panel");
    static Button createOpenButton(UIElement allowOutputWindow) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPostIcon(NETextures.OUTPUTS)
            .setOnClick(e -> allowOutputWindow.layout(layout -> layout.display(TaffyDisplay.FLEX)));
        HostElements.tooltips(button, List.of(Component.translatable("gui.neoecoae.integrated_working_station.allow_outputs").withStyle(ChatFormatting.WHITE)));
        return button;
    }

    static UIElement create(Config config) {
        return WorkstationUiJson.load(JSON_LAYOUT, new JsonContext(config));
    }

    private static UIElement createLegacy(Config config) {
        UIElement window = new UIElement().addClass("panel_bg");

        UIElement titleBar = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(2);
        });

        titleBar.addChild(new TextElement()
            .setText("gui.neoecoae.integrated_working_station.allow_outputs", true)
            .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL).adaptiveHeight(true).textShadow(false).textColor(Palette.INK_403E53)));

        titleBar.addChild(new Button()
            .noText()
            .addPostIcon(AETextures.icon(ECOIcon.CLEAR))
            .setOnClick(e -> window.layout(layout -> layout.display(TaffyDisplay.NONE)))
            .layout(layout -> layout.width(12).height(12).paddingAll(1)));

        window.addChild(titleBar);

        // Drag the window via title bar
        WindowDragHelper.setDragMove(titleBar, window, null, null);

        UIElement grid = new UIElement().layout(layout -> {
            layout.display(TaffyDisplay.GRID);
            layout.gridTemplateColumns("1fr 1fr 1fr");
            layout.gridTemplateRows("auto auto auto");
            // Keep horizontal compact; add extra vertical spacing via cell margins
            layout.gapAll(2);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyItems(AlignItems.CENTER);
        });

        window.addChild(grid);

        Function<RelativeSide, String> toRow = side -> switch (side) {
            case TOP -> "1";
            case LEFT, FRONT, RIGHT -> "2";
            case BOTTOM, BACK -> "3";
        };

        Function<RelativeSide, String> toColumn = side -> switch (side) {
            case LEFT -> "1";
            case TOP, FRONT, BOTTOM -> "2";
            case RIGHT, BACK -> "3";
        };

        Function<RelativeSide, RelativeSide> toInternalSide = uiSide -> switch (uiSide) {
            case LEFT -> RelativeSide.RIGHT;
            case RIGHT -> RelativeSide.LEFT;
            default -> uiSide;
        };

        Function<RelativeSide, IGuiTexture> sideIcon = uiSide -> {
            ItemStack item = config.adjacentItem().apply(toInternalSide.apply(uiSide));
            return item.isEmpty() ? IGuiTexture.EMPTY : new ItemStackTexture(item);
        };

        BiFunction<RelativeSide, String, UIElement> sideToggle = (uiSide, labelKey) -> {
            RelativeSide internalSide = toInternalSide.apply(uiSide);

            var applyBackground = (java.util.function.BiConsumer<Button, Boolean>) (button, enabled) -> button.buttonStyle(style -> style
                .baseTexture(enabled ? NETextures.BUTTON_HIGHLIGHTED : NETextures.BUTTON)
                .hoverTexture(NETextures.BUTTON_HIGHLIGHTED)
                .pressedTexture(NETextures.BUTTON_HIGHLIGHTED)
            );

            int extraRowSpacing = switch (uiSide) {
                case TOP, LEFT, FRONT, RIGHT -> 4;
                default -> 0;
            };

            UIElement cell = new UIElement().layout(layout -> {
                layout.gridRow(toRow.apply(uiSide));
                layout.gridColumn(toColumn.apply(uiSide));
                layout.alignItems(AlignItems.CENTER);
                layout.justifyContent(AlignContent.CENTER);
                layout.marginBottom(extraRowSpacing);
            });

            IGuiTexture icon = sideIcon.apply(uiSide);
            Toggle toggle = new Toggle();
            toggle.noText();
            // Mark icon: always render the adjacent-block item icon
            toggle.toggleStyle(style -> style.markTexture(icon).unmarkTexture(icon));
            toggle.toggleButton(button -> {
                button.layout(layout -> layout.height(20).width(20).paddingAll(2));
                button.setOnServerClick(e -> config.toggleOutput().accept(internalSide));
            });
            toggle.bind(DataBindingBuilder.boolS2C(() -> config.outputAllowed().test(internalSide)).build());
            toggle.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                var enabled = config.outputAllowed().test(internalSide);
                event.hoverTooltips = HostElements.hoverTooltips(
                    List.of(
                        Component.translatable(labelKey).withStyle(ChatFormatting.WHITE),
                        Component.translatable(enabled
                            ? "gui.neoecoae.integrated_working_station.allow_outputs.enabled"
                            : "gui.neoecoae.integrated_working_station.allow_outputs.disabled").withStyle(ChatFormatting.GRAY)
                    ));
            });

            toggle.layout(layout -> layout.width(20).height(20).paddingAll(0));

            // Keep visuals in-sync immediately on the client, while server remains authoritative
            toggle.setOnToggleChanged(on -> {
                config.syncOutput().accept(internalSide, on);
                applyBackground.accept(toggle.toggleButton, on);
            });

            // Initialize background based on current synced value
            applyBackground.accept(toggle.toggleButton, config.outputAllowed().test(internalSide));

            cell.addChild(toggle);

            return cell;
        };

        //    上
        // 左 正 右
        //    下 背
        grid.addChild(sideToggle.apply(RelativeSide.TOP, "gui.neoecoae.relative_side.top"));
        grid.addChild(sideToggle.apply(RelativeSide.LEFT, "gui.neoecoae.relative_side.left"));
        grid.addChild(sideToggle.apply(RelativeSide.FRONT, "gui.neoecoae.relative_side.front"));
        grid.addChild(sideToggle.apply(RelativeSide.RIGHT, "gui.neoecoae.relative_side.right"));
        grid.addChild(sideToggle.apply(RelativeSide.BOTTOM, "gui.neoecoae.relative_side.bottom"));
        grid.addChild(sideToggle.apply(RelativeSide.BACK, "gui.neoecoae.relative_side.back"));

        return window;
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final Config config;

        private JsonContext(Config config) {
            this.config = config;
        }

        @Override
        public UIElement createCustom(JsonObject node) {
            if ("content".equals(string(node, "custom", ""))) return createLegacy(config);
            throw new IllegalArgumentException("Unknown workstation output panel custom element: "
                + string(node, "custom", ""));
        }

        @Override
        public void configure(JsonObject node, UIElement element) {
            if ("workstation_output_panel".equals(string(node, "id", ""))) {
                element.layout(layout -> layout.display(TaffyDisplay.NONE));
            }
        }
    }
}
