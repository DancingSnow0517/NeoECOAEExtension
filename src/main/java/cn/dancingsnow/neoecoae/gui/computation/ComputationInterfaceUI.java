package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Configuration surface for the computation host's fast-planning item match rules. */
public final class ComputationInterfaceUI {
    private static final int GRID_COLUMNS = 9;
    private static final int GRID_ROWS = 7;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_WIDTH = GRID_COLUMNS * SLOT_SIZE;
    private static final int GRID_HEIGHT = GRID_ROWS * SLOT_SIZE;
    private static final int PLAYER_INVENTORY_WIDTH = 9 * SLOT_SIZE;
    private static final int PLAYER_INVENTORY_HEIGHT = 4 * SLOT_SIZE;
    private static final int ROOT_WIDTH = 176;
    private static final int ROOT_HEIGHT = 253;
    // ItemSlot renders its contents one pixel inside its bounds; offset the interactive grid to
    // align the item art with the slot wells baked into nbtbench.png.
    private static final int GRID_LEFT = 7;
    private static final int GRID_TOP = 28;
    private static final int INVENTORY_LEFT = 7;
    private static final int INVENTORY_TOP = 167;

    private ComputationInterfaceUI() {
    }

    public static ModularUI create(
        ECOMachineInterfaceBlockEntity<NEComputationCluster> computationInterface,
        Player player
    ) {
        UIElement root = new UIElement().layout(layout -> layout
            .width(ROOT_WIDTH)
            .height(ROOT_HEIGHT)
        ).style(style -> style.backgroundTexture(NETextures.NBT_BENCH));
        root.addChild(title());
        root.addChild(fuzzyHint());
        root.addChild(fuzzyItemSlots(computationInterface));
        root.addChild(playerInventory());
        return new ModularUI(
            UI.of(root, java.util.List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))),
            player
        );
    }

    private static UIElement title() {
        return new TextElement()
            .setText(Component.translatable("block.neoecoae.computation_interface"))
            .textStyle(style -> style.adaptiveHeight(true).adaptiveWidth(true)
                .textWrap(TextWrap.NONE).textColor(0x3F3D52).textShadow(false))
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8).top(6).width(160).height(12));
    }

    private static UIElement fuzzyHint() {
        return new TextElement()
            .setText(Component.literal("标记物品用于忽略其组件差异"))
            .textStyle(style -> style.adaptiveHeight(true).adaptiveWidth(true)
                .textWrap(TextWrap.NONE).textColor(0x6D6A82).textShadow(false))
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8).top(18).width(160).height(10));
    }

    private static UIElement fuzzyItemSlots(
        ECOMachineInterfaceBlockEntity<NEComputationCluster> computationInterface
    ) {
        UIElement grid = new UIElement().layout(layout -> layout
            .width(GRID_WIDTH)
            .height(GRID_HEIGHT)
            .positionType(TaffyPosition.ABSOLUTE)
            .left(GRID_LEFT)
            .top(GRID_TOP)
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
                ItemSlot itemSlot = new FuzzyPlanningItemSlot(computationInterface, slot).xeiPhantom();
                itemSlot.layout(layout -> layout.width(SLOT_SIZE).height(SLOT_SIZE));
                line.addChild(itemSlot);
            }
            grid.addChild(line);
        }
        return grid;
    }

    private static UIElement playerInventory() {
        InventorySlots playerInventory = new InventorySlots();
        playerInventory.layout(layout -> layout
            .width(PLAYER_INVENTORY_WIDTH)
            .height(PLAYER_INVENTORY_HEIGHT)
            .positionType(TaffyPosition.ABSOLUTE)
            .left(INVENTORY_LEFT)
            .top(INVENTORY_TOP)
        );
        playerInventory.apply(slot -> slot.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        playerInventory.getChildren().forEach(
            child -> child.getStyle().backgroundTexture(IGuiTexture.EMPTY)
        );
        return playerInventory;
    }

    /** A JEI-targetable filter slot: its displayed stack is configuration only, never an item transfer. */
    private static final class FuzzyPlanningItemSlot extends ItemSlot {
        private final ECOMachineInterfaceBlockEntity<NEComputationCluster> computationInterface;
        private final int inventorySlot;

        private FuzzyPlanningItemSlot(
            ECOMachineInterfaceBlockEntity<NEComputationCluster> computationInterface,
            int inventorySlot
        ) {
            super(new FuzzyPlanningFilterSlot(computationInterface.getFuzzyPlanningItemHandler(), inventorySlot));
            this.computationInterface = computationInterface;
            this.inventorySlot = inventorySlot;
            // The workbench background already contains the slot grid; drawing a slot texture here
            // would create a visible double border.
            getStyle().backgroundTexture(IGuiTexture.EMPTY);
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 1 && !getValue().isEmpty()) {
                    setValue(ItemStack.EMPTY, true);
                    event.stopImmediatePropagation();
                }
            }, true);
        }

        @Override
        public ItemSlot setValue(ItemStack value, boolean notify) {
            ItemStack filter = value == null || value.isEmpty() ? ItemStack.EMPTY : value.copyWithCount(1);
            super.setValue(filter, notify);
            computationInterface.rpcToServer("setFuzzyPlanningFilter", inventorySlot, filter);
            return this;
        }
    }

    private static final class FuzzyPlanningFilterSlot extends ItemHandlerSlot {
        private FuzzyPlanningFilterSlot(IItemHandlerModifiable itemHandler, int index) {
            super(itemHandler, index);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty();
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 0;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 0;
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public void set(ItemStack stack) {
            super.set(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
    }
}
