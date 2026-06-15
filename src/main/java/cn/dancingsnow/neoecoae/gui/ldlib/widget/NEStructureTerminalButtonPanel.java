package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEStructureTerminalConfigState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class NEStructureTerminalButtonPanel {
    private final NEStructureTerminalWidget owner;
    private final Supplier<NEStructureTerminalConfigState> stateSupplier;
    private final Consumer<NEStructureTerminalWidget.Action> actionSender;
    private final List<RenderedButton> renderedButtons = new ArrayList<>();
    private final List<Widget> patternWidgets = new ArrayList<>();

    NEStructureTerminalButtonPanel(
            NEStructureTerminalWidget owner,
            Supplier<NEStructureTerminalConfigState> stateSupplier,
            Consumer<NEStructureTerminalWidget.Action> actionSender) {
        this.owner = owner;
        this.stateSupplier = stateSupplier;
        this.actionSender = actionSender;
    }

    void init(NEMultiblockPatternViewerWidget patternViewer) {
        renderedButtons.clear();
        patternWidgets.clear();
        patternWidgets.add(patternViewer);

        addLocalButton(
                NEStructureTerminalLayout.PATTERN_TAB_X,
                NEStructureTerminalLayout.TAB_Y,
                NEStructureTerminalLayout.PATTERN_TAB_W,
                NEStructureTerminalLayout.TAB_H,
                () -> Component.translatable("gui.neoecoae.multiblock.pattern"),
                () -> true,
                () -> {},
                () -> true);
        addHostButton(StructureTerminalHostType.CRAFTING, 0, NEStructureTerminalWidget.Action.SELECT_CRAFTING);
        addHostButton(StructureTerminalHostType.STORAGE, 1, NEStructureTerminalWidget.Action.SELECT_STORAGE);
        addHostButton(StructureTerminalHostType.COMPUTATION, 2, NEStructureTerminalWidget.Action.SELECT_COMPUTATION);
        addTierButton(1, 0, NEStructureTerminalWidget.Action.SELECT_TIER_1);
        addTierButton(2, 1, NEStructureTerminalWidget.Action.SELECT_TIER_2);
        addTierButton(3, 2, NEStructureTerminalWidget.Action.SELECT_TIER_3);
        addServerButton(
                NEStructureTerminalLayout.LENGTH_X,
                NEStructureTerminalLayout.CONTROL_Y,
                NEStructureTerminalLayout.LENGTH_BUTTON_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.literal("-"),
                NEStructureTerminalWidget.Action.DECREASE,
                () -> false,
                () -> true,
                null);
        addServerButton(
                NEStructureTerminalLayout.LENGTH_X
                        + NEStructureTerminalLayout.LENGTH_BUTTON_W
                        + NEStructureTerminalLayout.LENGTH_VALUE_W,
                NEStructureTerminalLayout.CONTROL_Y,
                NEStructureTerminalLayout.LENGTH_BUTTON_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.literal("+"),
                NEStructureTerminalWidget.Action.INCREASE,
                () -> false,
                () -> true,
                null);
        addServerButton(
                NEStructureTerminalLayout.MIRROR_X,
                NEStructureTerminalLayout.CONTROL_Y,
                NEStructureTerminalLayout.MIRROR_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.translatable("gui.neoecoae.structure_terminal.preview_mirrored"),
                NEStructureTerminalWidget.Action.TOGGLE_PREVIEW_MIRRORED,
                () -> state().previewMirrored(),
                () -> true,
                null);
        addPatternLayerButton(
                NEStructureTerminalLayout.LAYER_PREV_X,
                Component.literal("<"),
                NEStructureTerminalWidget.Action.PREVIOUS_LAYER);
        addPatternLayerButton(
                NEStructureTerminalLayout.LAYER_NEXT_X,
                Component.literal(">"),
                NEStructureTerminalWidget.Action.NEXT_LAYER);
        addServerButton(
                NEStructureTerminalLayout.FORMED_PREVIEW_X,
                NEStructureTerminalLayout.FORMED_PREVIEW_Y,
                NEStructureTerminalLayout.FORMED_PREVIEW_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.translatable("gui.neoecoae.structure_terminal.preview_formed"),
                NEStructureTerminalWidget.Action.TOGGLE_PREVIEW_FORMED,
                () -> state().previewFormed(),
                () -> true,
                patternWidgets);
        addFooterActionButton(
                0,
                NEStructureTerminalLayout.FOOTER_BUTTON_W,
                Component.translatable("gui.neoecoae.structure_terminal.mode.build"),
                NEStructureTerminalWidget.Action.BUILD_LINKED);
        addFooterActionButton(
                1,
                NEStructureTerminalLayout.FOOTER_MIRROR_BUTTON_W,
                Component.translatable("gui.neoecoae.structure_terminal.mode.mirrored_build"),
                NEStructureTerminalWidget.Action.BUILD_MIRRORED_LINKED);
        addFooterActionButton(
                2,
                NEStructureTerminalLayout.FOOTER_BUTTON_W,
                Component.translatable("gui.neoecoae.structure_terminal.mode.dismantle"),
                NEStructureTerminalWidget.Action.DISMANTLE_LINKED);
        refreshWidgetVisibility();
    }

    void refreshWidgetVisibility() {
        for (Widget widget : patternWidgets) {
            widget.setVisible(true);
            widget.setActive(true);
        }
        for (RenderedButton renderedButton : renderedButtons) {
            boolean visible = renderedButton.visible().getAsBoolean();
            renderedButton.button().setVisible(visible);
            renderedButton.button().setActive(visible);
        }
    }

    void drawBackgrounds(NEStructureTerminalRenderContext context, GuiGraphics graphics, int mouseX, int mouseY) {
        for (RenderedButton button : renderedButtons) {
            if (!button.visible().getAsBoolean()) {
                continue;
            }
            int x = context.absX(button.x());
            int y = context.absY(button.y());
            boolean hover = mouseX >= x && mouseX < x + button.w() && mouseY >= y && mouseY < y + button.h();
            NELDLibClientStyle.drawInsetButton(
                    graphics,
                    x,
                    y,
                    button.w(),
                    button.h(),
                    hover,
                    false,
                    button.selected().getAsBoolean());
        }
    }

    void drawLabels(NEStructureTerminalRenderContext context, GuiGraphics graphics) {
        for (RenderedButton button : renderedButtons) {
            if (!button.visible().getAsBoolean()) {
                continue;
            }
            int color =
                    button.selected().getAsBoolean() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_MUTED;
            context.drawCenteredFitted(
                    graphics,
                    button.label().get(),
                    button.x(),
                    button.y() + (button.h() - context.font().lineHeight) / 2,
                    button.w(),
                    color);
        }
    }

    private void addHostButton(StructureTerminalHostType hostType, int index, NEStructureTerminalWidget.Action action) {
        addServerButton(
                NEStructureTerminalLayout.HOST_X
                        + index * (NEStructureTerminalLayout.HOST_W + NEStructureTerminalLayout.HOST_GAP),
                NEStructureTerminalLayout.CONTROL_Y,
                NEStructureTerminalLayout.HOST_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.translatable(hostTypeKey(hostType)),
                action,
                () -> state().hostType() == hostType,
                () -> true,
                null);
    }

    private void addTierButton(int tier, int index, NEStructureTerminalWidget.Action action) {
        addServerButton(
                NEStructureTerminalLayout.TIER_X
                        + index * (NEStructureTerminalLayout.TIER_W + NEStructureTerminalLayout.TIER_GAP),
                NEStructureTerminalLayout.CONTROL_Y,
                NEStructureTerminalLayout.TIER_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> Component.literal(tierLabel(state().hostType(), tier)),
                action,
                () -> state().tier() == tier,
                () -> true,
                null);
    }

    private void addPatternLayerButton(int x, Component label, NEStructureTerminalWidget.Action action) {
        addServerButton(
                x,
                NEStructureTerminalLayout.PATTERN_PANEL_Y + 3,
                NEStructureTerminalLayout.LAYER_BUTTON_W,
                NEStructureTerminalLayout.CONTROL_H,
                () -> label,
                action,
                () -> false,
                () -> true,
                patternWidgets);
    }

    private void addFooterActionButton(int index, int width, Component label, NEStructureTerminalWidget.Action action) {
        addServerButton(
                NEStructureTerminalLayout.footerButtonX(index),
                NEStructureTerminalLayout.FOOTER_BUTTON_Y,
                width,
                NEStructureTerminalLayout.CONTROL_H,
                () -> label,
                action,
                () -> switch (action) {
                    case BUILD_LINKED -> state().operationModePending()
                            && state().operationMode() == StructureTerminalMode.BUILD;
                    case BUILD_MIRRORED_LINKED -> state().operationModePending()
                            && state().operationMode() == StructureTerminalMode.MIRRORED_BUILD;
                    case DISMANTLE_LINKED -> state().operationModePending()
                            && state().operationMode() == StructureTerminalMode.DISMANTLE;
                    default -> false;
                },
                () -> true,
                null);
    }

    private void addLocalButton(
            int x,
            int y,
            int w,
            int h,
            Supplier<Component> label,
            BooleanSupplier selected,
            Runnable action,
            BooleanSupplier visible) {
        addLocalButton(x, y, w, h, label, selected, action, visible, null);
    }

    private void addLocalButton(
            int x,
            int y,
            int w,
            int h,
            Supplier<Component> label,
            BooleanSupplier selected,
            Runnable action,
            BooleanSupplier visible,
            List<Widget> group) {
        ButtonWidget button = (ButtonWidget) new ButtonWidget(x, y, w, h, IGuiTexture.EMPTY, click -> {
            if (click.isRemote) {
                action.run();
                refreshWidgetVisibility();
            }
        });
        owner.addWidget(button);
        if (group != null) {
            group.add(button);
        }
        renderedButtons.add(new RenderedButton(x, y, w, h, button, label, selected, visible));
    }

    private void addServerButton(
            int x,
            int y,
            int w,
            int h,
            Supplier<Component> label,
            NEStructureTerminalWidget.Action action,
            BooleanSupplier selected,
            BooleanSupplier visible,
            List<Widget> group) {
        ButtonWidget button = (ButtonWidget) new ButtonWidget(x, y, w, h, IGuiTexture.EMPTY, click -> {
            if (click.isRemote) {
                actionSender.accept(action);
            }
        });
        owner.addWidget(button);
        if (group != null) {
            group.add(button);
        }
        renderedButtons.add(new RenderedButton(x, y, w, h, button, label, selected, visible));
    }

    private NEStructureTerminalConfigState state() {
        return stateSupplier.get();
    }

    private static String hostTypeKey(StructureTerminalHostType hostType) {
        return switch (hostType) {
            case CRAFTING -> "gui.neoecoae.structure_terminal.target.crafting";
            case STORAGE -> "gui.neoecoae.structure_terminal.target.storage";
            case COMPUTATION -> "gui.neoecoae.structure_terminal.target.computation";
        };
    }

    private static String tierLabel(StructureTerminalHostType hostType, int tier) {
        int level =
                switch (StructureTerminalHostType.clampTier(tier)) {
                    case 2 -> 6;
                    case 3 -> 9;
                    default -> 4;
                };
        String prefix =
                switch (hostType) {
                    case STORAGE -> "L";
                    case COMPUTATION -> "C";
                    case CRAFTING -> "F";
                };
        return prefix + level;
    }

    private record RenderedButton(
            int x,
            int y,
            int w,
            int h,
            ButtonWidget button,
            Supplier<Component> label,
            BooleanSupplier selected,
            BooleanSupplier visible) {}
}
