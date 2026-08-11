package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Configuration surface for the computation host's fast-planning item match rules. */
public final class ComputationInterfaceUI {
    private static final int GRID_COLUMNS = 9;
    private static final int GRID_ROWS = 4;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_WIDTH = GRID_COLUMNS * SLOT_SIZE;
    private static final int GRID_HEIGHT = GRID_ROWS * SLOT_SIZE;
    private static final int PLAYER_INVENTORY_WIDTH = 9 * SLOT_SIZE;
    private static final int CONTENT_PADDING = 8;
    private static final int CONTENT_HEIGHT = CONTENT_PADDING * 2 + 11 + 4 + GRID_HEIGHT;

    private ComputationInterfaceUI() {
    }

    public static ModularUI create(
        ECOMachineInterfaceBlockEntity<NEComputationCluster> computationInterface,
        Player player
    ) {
        UIElement root = new UIElement().layout(layout -> layout
            .width(PLAYER_INVENTORY_WIDTH + CONTENT_PADDING * 4)
            .paddingLeft(8)
            .paddingRight(8)
            .paddingTop(8)
            .paddingBottom(8)
            .gapAll(5)
            .flexDirection(FlexDirection.COLUMN)
        ).addClass("panel_bg");
        root.addChild(title());

        UIElement content = new UIElement().layout(layout -> layout
            .widthPercent(100)
            .height(CONTENT_HEIGHT)
            .paddingAll(CONTENT_PADDING)
            .gapAll(4)
            .flexDirection(FlexDirection.COLUMN)
        ).style(style -> style.backgroundTexture(Sprites.BORDER_THICK_RT1));
        content.addChild(sectionLabel());
        content.addChild(fuzzyItemSlots(computationInterface));
        root.addChild(content);

        InventorySlots playerInventory = new InventorySlots();
        playerInventory.layout(layout -> layout.width(PLAYER_INVENTORY_WIDTH).marginTop(2));
        root.addChild(playerInventory);
        return new ModularUI(
            UI.of(root, java.util.List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))),
            player
        );
    }

    private static UIElement title() {
        return new TextElement()
            .setText(Component.literal("计算子系统通讯接口"))
            .textStyle(style -> style.adaptiveHeight(true).adaptiveWidth(true)
                .textWrap(TextWrap.HOVER_ROLL).textColor(0x3F3D52).textShadow(false))
            .layout(layout -> layout.widthPercent(100).height(12));
    }

    private static UIElement sectionLabel() {
        return new TextElement()
            .setText(Component.literal("模糊匹配物品"))
            .textStyle(style -> style.adaptiveHeight(true).textColor(0x3F3D52).textShadow(false))
            .layout(layout -> layout.widthPercent(100).height(11));
    }

    private static UIElement fuzzyItemSlots(
        ECOMachineInterfaceBlockEntity<NEComputationCluster> computationInterface
    ) {
        UIElement grid = new UIElement().layout(layout -> layout
            .width(GRID_WIDTH)
            .height(GRID_HEIGHT)
            .flexDirection(FlexDirection.COLUMN)
        );
        for (int row = 0; row < GRID_ROWS; row++) {
            UIElement line = new UIElement().layout(layout -> layout
                .width(GRID_WIDTH)
                .height(SLOT_SIZE)
                .flexDirection(FlexDirection.ROW)
            );
            for (int column = 0; column < GRID_COLUMNS; column++) {
                int slot = row * GRID_COLUMNS + column;
                ItemSlot itemSlot = new ItemSlot(new ItemHandlerSlot(
                    computationInterface.getFuzzyPlanningItemHandler(), slot
                ));
                itemSlot.layout(layout -> layout.width(SLOT_SIZE).height(SLOT_SIZE));
                line.addChild(itemSlot);
            }
            grid.addChild(line);
        }
        return grid;
    }
}
