package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.Supplier;

/** Control surface for browsing network patterns and moving compatible ones into ECO crafting buses. */
public final class CraftingInterfaceUI {
    private static final int STATUS_CONNECTED = 0x55CC77;
    private static final int STATUS_DISCONNECTED = 0xDD5555;
    private static final int PREVIEW_COLUMNS = 9;
    private static final int PREVIEW_ROWS = 5;
    private static final int PREVIEW_WIDTH = PREVIEW_COLUMNS * 18;
    private static final int PREVIEW_HEIGHT = PREVIEW_ROWS * 18;
    private static final int PLAYER_INVENTORY_WIDTH = 9 * 18;
    private static final int ROOT_SIDE_MARGIN = 7;
    private static final int TOOL_BUTTON_SIZE = 16;

    private CraftingInterfaceUI() {
    }

    public static ModularUI create(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            Player player) {
        craftingInterface.ensurePatternPreview();

        UIElement root = new UIElement().layout(layout -> layout
                .width(PLAYER_INVENTORY_WIDTH + ROOT_SIDE_MARGIN * 2)
                .paddingLeft(ROOT_SIDE_MARGIN)
                .paddingRight(ROOT_SIDE_MARGIN)
                .paddingTop(8)
                .paddingBottom(8)
                .gapAll(5)
                .flexDirection(FlexDirection.COLUMN))
                .addClass("panel_bg");
        root.addChild(boundLabel(() -> Component.translatable("gui.neoecoae.crafting_interface.title")));

        UIElement contentFrame = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .paddingLeft(8)
                .paddingRight(8)
                .paddingTop(8)
                .paddingBottom(7)
                .gapAll(4)
                .flexDirection(FlexDirection.COLUMN))
                .style(style -> style.backgroundTexture(Sprites.BORDER_THICK_RT1));
        contentFrame.addChild(statusLabel(() -> Component.translatable("gui.neoecoae.storage_interface.network")
                .append(": ")
                .append(Component.translatable(craftingInterface.isTargetOnline()
                        ? "gui.neoecoae.storage_interface.connected"
                        : "gui.neoecoae.storage_interface.disconnected")
                        .withColor(craftingInterface.isTargetOnline() ? STATUS_CONNECTED : STATUS_DISCONNECTED))));
        contentFrame.addChild(transferButton(craftingInterface));
        contentFrame.addChild(statusLabel(craftingInterface::getPatternTransferPrimaryStatus));
        root.addChild(contentFrame);
        root.addChild(previewSection(craftingInterface));
        root.addChild(new InventorySlots().layout(layout -> layout
                .width(PLAYER_INVENTORY_WIDTH)
                .marginTop(2)));

        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), player);
    }

    private static Button transferButton(ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface) {
        Button button = new Button()
                .setText(Component.translatable("gui.neoecoae.host.crafting.pattern_transfer"))
                .setOnServerClick(event -> craftingInterface.startNetworkPatternTransfer());
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.layout(layout -> layout.widthPercent(100).height(18));
        return button;
    }

    private static UIElement previewHeader(ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface) {
        UIElement header = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .height(TOOL_BUTTON_SIZE)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(2));
        Label title = boundLabel(() -> Component.translatable("gui.neoecoae.crafting_interface.preview"));
        title.layout(layout -> layout.flex(1).height(TOOL_BUTTON_SIZE));
        header.addChild(title);
        header.addChild(iconButton(Icon.PATTERN_TERMINAL_ALL, "gui.neoecoae.crafting_interface.preview.refresh",
                () -> craftingInterface.refreshPatternPreview()));
        return header;
    }

    private static UIElement previewSection(ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface) {
        UIElement section = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .gapAll(4)
                .flexDirection(FlexDirection.COLUMN));
        section.addChild(previewHeader(craftingInterface));
        section.addChild(patternPreviewRow(craftingInterface));
        section.addChild(statusLabel(craftingInterface::getPatternPreviewStatus));
        section.addChild(statusLabel(craftingInterface::getPatternPreviewScrollStatus));
        return section;
    }

    private static UIElement patternPreviewRow(ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface) {
        UIElement row = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .height(PREVIEW_HEIGHT)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER));
        row.addChild(patternPreview(craftingInterface));
        return row;
    }

    private static UIElement patternPreview(ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface) {
        UIElement preview = new UIElement().layout(layout -> layout
                .width(PREVIEW_WIDTH)
                .height(PREVIEW_HEIGHT)
                .alignItems(AlignItems.CENTER))
                .addClass("panel_border");
        // Consume the wheel on the client before AE2 sees it. Without this, AE2's
        // inventory-scroll shortcut moves the pattern under the cursor into the
        // player inventory while this panel is being scrolled.
        preview.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0) {
                event.stopImmediatePropagation();
            }
        });
        preview.addServerEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0) {
                craftingInterface.scrollPatternPreview(event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
            }
        });
        for (int row = 0; row < PREVIEW_ROWS; row++) {
            UIElement previewRow = new UIElement().layout(layout -> layout
                    .width(PREVIEW_WIDTH)
                    .height(18)
                    .flexDirection(FlexDirection.ROW));
            for (int column = 0; column < PREVIEW_COLUMNS; column++) {
                int visualSlot = row * PREVIEW_COLUMNS + column;
                previewRow.addChild(new PatternItemSlot(
                        new ItemHandlerSlot(craftingInterface.getPatternPreviewItemHandler(), visualSlot))
                        .slotStyle(style -> style.slotOverlay(NETextures.PATTERN_OVERLAY)));
            }
            preview.addChild(previewRow);
        }
        return preview;
    }

    private static Button iconButton(Icon icon, String tooltip, Runnable action) {
        Button button = new Button()
                .noText()
                .addPreIcon(AETextures.icon(icon))
                .setOnServerClick(event -> action.run());
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.layout(layout -> layout.width(TOOL_BUTTON_SIZE).height(TOOL_BUTTON_SIZE));
        return HostElements.tooltips(button, Component.translatable(tooltip));
    }

    private static Label boundLabel(Supplier<Component> text) {
        Label label = new Label();
        label.setText(text.get());
        label.bind(DataBindingBuilder.componentS2C(text).build());
        label.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).adaptiveWidth(false).adaptiveHeight(true));
        label.layout(layout -> layout.widthPercent(100).height(12));
        return label;
    }

    private static Label statusLabel(Supplier<Component> text) {
        Label label = boundLabel(text);
        label.layout(layout -> layout.widthPercent(100).height(12).marginLeft(2));
        return label;
    }
}
