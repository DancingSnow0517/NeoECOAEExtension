package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostNetworkStatusElement;
import cn.dancingsnow.neoecoae.gui.common.NetworkFrequencyButton;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Package-private header composition for the crafting host page. */
final class CraftingHeaderUI {
    private static final int HEADER_HEIGHT = 28;
    private static final int TOOLBAR_BUTTON_SIZE = 16;

    private CraftingHeaderUI() {
    }

    static UIElement create(CraftingHostPanelUI.Config config) {
        UIElement header = new UIElement()
                .addClass("eco-host-header")
                .layout(layout -> layout
                        .widthPercent(100)
                        .height(HEADER_HEIGHT)
                        .flexDirection(FlexDirection.ROW)
                        .alignItems(AlignItems.CENTER));

        Label title = HostElements.textSegment(config.title(), () -> CraftingHostStyles.ROOT_TEXT);
        title.addClass("eco-host-title");
        title.textStyle(CraftingHostStyles::compact);
        title.layout(layout -> layout.widthPercent(100).height(10));
        UIElement networkStatus = HostNetworkStatusElement.create(config.networkMultiplier(), config.networkConnected());
        UIElement statusRow = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .height(12)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(5));
        statusRow.addChildren(networkStatus, runStatusLabel(config.runStatus()));

        UIElement titleBlock = new UIElement().layout(layout -> layout
                .flex(1)
                .height(24)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(2));
        titleBlock.addChildren(title, statusRow);

        UIElement toolbar = new UIElement()
                .addClass("eco-host-toolbar")
                .layout(layout -> layout.height(TOOLBAR_BUTTON_SIZE).flexDirection(FlexDirection.ROW));
        toolbar.addChildren(
                NetworkFrequencyButton.create(config.networkFrequency(), config.adjustNetworkFrequency(), 16, 16),
                toolbarButton(config.toggleOverclocked(), Icon.POWER_UNIT_AE, config.overclocked(),
                        "gui.neoecoae.crafting.overclock.on", "gui.neoecoae.crafting.overclock.off"),
                toolbarButton(config.toggleActiveCooling(), Icon.TYPE_FILTER_ALL, config.activeCooling(),
                        "gui.neoecoae.crafting.active_cooling.on", "gui.neoecoae.crafting.active_cooling.off"));

        header.addChildren(titleBlock, toolbar);
        return header;
    }

    private static Label runStatusLabel(IntSupplier status) {
        Label label = new Label();
        label.setText(runStatusText(status.getAsInt()));
        label.textStyle(style -> style
                .adaptiveHeight(true)
                .adaptiveWidth(false)
                .fontSize(CraftingHostStyles.COMPACT_FONT_SIZE)
                .textAlignHorizontal(Horizontal.LEFT)
                .textWrap(TextWrap.HOVER_ROLL)
                .textShadow(false));
        label.layout(layout -> layout.flex(1).height(10));

        BindableValue<Integer> syncedStatus = HostElements.syncedInt(status);
        syncedStatus.registerValueListener(value -> label.setText(runStatusText(value == null ? 0 : value)));
        label.addChild(syncedStatus);
        return label;
    }

    private static Component runStatusText(int status) {
        String key = switch (status) {
            case 1 -> "gui.neoecoae.crafting.run_status.missing_coolant";
            case 2 -> "gui.neoecoae.crafting.run_status.missing_energy";
            case 3 -> "gui.neoecoae.crafting.run_status.overclock_mismatch";
            case 4 -> "gui.neoecoae.crafting.run_status.network_coolant_incompatible";
            default -> "gui.neoecoae.crafting.run_status.normal";
        };
        return Component.translatable(key).withColor(status == 0 ? CraftingHostStyles.PANEL_SUCCESS : CraftingHostStyles.PANEL_WARNING);
    }

    private static Button toolbarButton(
            Runnable action,
            Icon icon,
            BooleanSupplier enabled,
            String enabledTooltipKey,
            String disabledTooltipKey) {
        Button button = new Button()
                .noText()
                .addPreIcon(AETextures.icon(icon))
                .setOnServerClick(event -> action.run());
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.addClass("eco-host-toolbar-button");
        button.layout(layout -> layout.width(TOOLBAR_BUTTON_SIZE).height(TOOLBAR_BUTTON_SIZE));

        BindableValue<Boolean> syncedEnabled = HostElements.syncedBoolean(enabled);
        button.addChild(syncedEnabled);
        HostElements.tooltips(button, () -> java.util.List.of(Component.translatable(
                Boolean.TRUE.equals(syncedEnabled.getValue()) ? enabledTooltipKey : disabledTooltipKey)));
        return button;
    }
}
