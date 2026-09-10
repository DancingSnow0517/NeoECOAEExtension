package cn.dancingsnow.neoecoae.gui.storage;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Attached selector and partition editor for ECO MEGA long bulk cells. */
public final class StorageMegaPanelUI {
    private static final int WIDTH = 103;
    private static final int HEIGHT = 130;
    private static final int LEFT = -WIDTH + 3;
    private static final int TOP = StorageHostUI.ROOT_HEIGHT - HEIGHT - 2;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_LEFT = 7;
    private static final int GRID_TOP = 31;
    private static final int GRID_COLUMNS = 5;
    private static final int GRID_ROWS = 5;
    private static final int CONTROLS_LEFT = 31;
    private static final int FIRST_ROW_TOP = 3;
    private static final int SECOND_ROW_TOP = 16;
    private static final int CONTROL_HEIGHT = 10;
    private static final int ARROW_BUTTON_WIDTH = 10;
    private static final int LABEL_WIDTH = 20;
    private static final int CONTROL_GAP = 0;
    private static final int ACTION_BUTTON_LEFT = 82;
    private static final int ACTION_BUTTON_TOP = 4;
    private static final int TEXT_COLOR = 0x3F3D52;

    private static final SpriteTexture BACKGROUND = SpriteTexture.of(
        NeoECOAE.id("textures/gui/storage/eco_mega_storage.png")
    ).setSprite(0, 0, WIDTH, HEIGHT);

    private StorageMegaPanelUI() {
    }

    public static UIElement create(
        ECOStorageSystemBlockEntity host,
        IItemHandlerModifiable upgradeInventory,
        IItemHandlerModifiable filterInventory,
        @Nullable Button bulkMarkingButton
    ) {
        UIElement panel = HostElements.syncedDisplay(host::hasEcoMegaBulkCell);
        HostElements.absolute(panel, LEFT, TOP, WIDTH, HEIGHT);
        panel.style(style -> style.backgroundTexture(BACKGROUND));
        panel.addChild(upgradeSlot(upgradeInventory));
        if (bulkMarkingButton != null) {
            HostSideButtonBar.placeButton(bulkMarkingButton, ACTION_BUTTON_LEFT, ACTION_BUTTON_TOP);
            panel.addChild(bulkMarkingButton);
        }
        panel.addChild(cellControls(host));
        UIElement pageControls = HostElements.syncedDisplay(host::hasEcoMegaUpgradeCard);
        pageControls.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(CONTROLS_LEFT)
            .top(SECOND_ROW_TOP)
            .width(ARROW_BUTTON_WIDTH * 2 + LABEL_WIDTH + CONTROL_GAP * 2)
            .height(CONTROL_HEIGHT));
        pageControls.addChild(pageControls(host));
        panel.addChild(pageControls);
        panel.addChild(filterGrid(host, filterInventory));
        return panel;
    }

    private static UIElement upgradeSlot(IItemHandlerModifiable inventory) {
        UIElement wrapper = HostElements.absolute(new UIElement(), 7, 6, SLOT_SIZE, SLOT_SIZE);
        ItemSlot slot = new ItemSlot(new ItemHandlerSlot(inventory, 0));
        slot.getStyle().backgroundTexture(IGuiTexture.EMPTY);
        slot.layout(layout -> layout.width(SLOT_SIZE).height(SLOT_SIZE));
        wrapper.addChild(slot);
        return wrapper;
    }

    private static UIElement cellControls(ECOStorageSystemBlockEntity host) {
        return controlsRow(
            () -> Component.literal(cellLabel(host)),
            () -> host.changeSelectedEcoMegaBulkCell(-1),
            () -> host.changeSelectedEcoMegaBulkCell(1)
        ).layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(CONTROLS_LEFT)
            .top(FIRST_ROW_TOP)
            .width(ARROW_BUTTON_WIDTH * 2 + LABEL_WIDTH + CONTROL_GAP * 2)
            .height(CONTROL_HEIGHT));
    }

    private static UIElement pageControls(ECOStorageSystemBlockEntity host) {
        return controlsRow(
            () -> Component.literal((host.getSelectedEcoMegaPage() + 1) + "/2"),
            () -> host.changeSelectedEcoMegaPage(-1),
            () -> host.changeSelectedEcoMegaPage(1)
        );
    }

    private static String cellLabel(ECOStorageSystemBlockEntity host) {
        int count = host.getEcoMegaBulkCellCount();
        return count <= 0 ? "0/0" : (host.getSelectedEcoMegaBulkCell() + 1) + "/" + count;
    }

    private static UIElement controlsRow(
        Supplier<Component> labelSupplier,
        Runnable previous,
        Runnable next
    ) {
        UIElement row = new UIElement().layout(layout -> layout
            .width(ARROW_BUTTON_WIDTH * 2 + LABEL_WIDTH + CONTROL_GAP * 2)
            .height(CONTROL_HEIGHT));
        row.addChild(arrowButton(Icon.ARROW_LEFT, previous)
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0)
                .width(ARROW_BUTTON_WIDTH).height(CONTROL_HEIGHT)));
        row.addChild(syncedLabel(labelSupplier)
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(ARROW_BUTTON_WIDTH + CONTROL_GAP).top(0)
                .width(LABEL_WIDTH).height(CONTROL_HEIGHT)));
        row.addChild(arrowButton(Icon.ARROW_RIGHT, next)
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(ARROW_BUTTON_WIDTH + CONTROL_GAP + LABEL_WIDTH + CONTROL_GAP).top(0)
                .width(ARROW_BUTTON_WIDTH).height(CONTROL_HEIGHT)));
        return row;
    }

    private static Button arrowButton(Icon icon, Runnable action) {
        Button button = new Button().noText().setOnServerClick(event -> action.run());
        button.addChild(new UIElement()
            .style(style -> style.backgroundTexture(AETextures.icon(icon)))
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(1).top(1).width(8).height(8)));
        return button;
    }

    private static Label syncedLabel(Supplier<Component> value) {
        Label label = HostElements.textSegment(value, () -> TEXT_COLOR);
        label.textStyle(style -> style
            .fontSize(6)
            .adaptiveWidth(false)
            .adaptiveHeight(false)
            .textWrap(TextWrap.NONE)
            .textAlignHorizontal(Horizontal.CENTER)
            .textAlignVertical(Vertical.CENTER)
            .textColor(TEXT_COLOR)
            .textShadow(false));
        return label;
    }

    private static UIElement filterGrid(
        ECOStorageSystemBlockEntity host,
        IItemHandlerModifiable filterInventory
    ) {
        UIElement grid = new UIElement().layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(GRID_LEFT)
            .top(GRID_TOP)
            .width(GRID_COLUMNS * SLOT_SIZE)
            .height(GRID_ROWS * SLOT_SIZE)
            .flexDirection(FlexDirection.COLUMN));
        for (int rowIndex = 0; rowIndex < GRID_ROWS; rowIndex++) {
            UIElement row = new UIElement().layout(layout -> layout
                .width(GRID_COLUMNS * SLOT_SIZE)
                .height(SLOT_SIZE)
                .flexDirection(FlexDirection.ROW));
            for (int column = 0; column < GRID_COLUMNS; column++) {
                int slotIndex = rowIndex * GRID_COLUMNS + column;
                ItemSlot slot = new MegaFilterItemSlot(host, filterInventory, slotIndex).xeiPhantom();
                slot.getStyle().backgroundTexture(IGuiTexture.EMPTY);
                slot.slotStyle(style -> style.hoverOverlay(NETextures.AE2_SLOT_HIGHLIGHT));
                slot.layout(layout -> layout.width(SLOT_SIZE).height(SLOT_SIZE));
                row.addChild(slot);
            }
            grid.addChild(row);
        }
        return grid;
    }

    private static final class MegaFilterItemSlot extends ItemSlot {
        private final ECOStorageSystemBlockEntity host;
        private final int visualSlot;

        private MegaFilterItemSlot(
            ECOStorageSystemBlockEntity host,
            IItemHandlerModifiable filterInventory,
            int visualSlot
        ) {
            super();
            this.host = host;
            this.visualSlot = visualSlot;
            // Server refreshes (including page/cell changes) must not be sent back as edits.
            // The default binding calls setValue(value, true), which would invoke our RPC.
            bind(DataBindingBuilder.itemStackS2C(() -> {
                ItemStack stack = filterInventory.getStackInSlot(visualSlot);
                return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            }).remoteSetter(value -> setValue(value, false)).build());
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 1 && !getValue().isEmpty()) {
                    setValue(ItemStack.EMPTY, true);
                    event.stopImmediatePropagation();
                    return;
                }
                if (event.button == 0) {
                    var player = Minecraft.getInstance().player;
                    ItemStack carried = player == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
                    if (!carried.isEmpty()) {
                        setValue(carried, true);
                        event.stopImmediatePropagation();
                    }
                }
            }, true);
        }

        @Override
        public ItemSlot setValue(ItemStack value, boolean notify) {
            ItemStack filter = value == null || value.isEmpty()
                ? ItemStack.EMPTY
                : value.copyWithCount(1);
            super.setValue(filter, notify);
            if (notify) {
                host.setEcoMegaFilterFromClient(visualSlot, filter);
            }
            return this;
        }
    }

}
