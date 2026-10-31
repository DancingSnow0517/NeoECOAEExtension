package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.gson.JsonObject;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import cn.dancingsnow.neoecoae.gui.GuiTitleProvider;

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
        UIElement root = NeoUiJsonLoader.load(NeoECOAE.id("screens/computation_interface"),
            new NeoUiJsonContext() {
                @Override
                public UIElement createCustom(JsonObject node) {
                    return switch (node.has("name") ? node.get("name").getAsString() : "") {
                        case "computation_interface_title" -> title(computationInterface);
                        case "computation_interface_hint" -> fuzzyHint();
                        case "computation_fuzzy_grid" -> fuzzyItemSlots(computationInterface);
                        default -> null;
                    };
                }

                @Override
                public void configure(JsonObject node, UIElement element) {
                    if ("player_inventory".equals(node.has("type") ? node.get("type").getAsString() : "")
                        && element instanceof InventorySlots inventory) {
                        inventory.apply(slot -> slot.getStyle().backgroundTexture(IGuiTexture.EMPTY));
                        inventory.getChildren().forEach(child -> child.getStyle().backgroundTexture(IGuiTexture.EMPTY));
                        for (InventorySlots.Row row : inventory.rows) {
                            row.apply(slot -> slot.transform(transform -> transform.translate(0, 1)));
                        }
                    }
                }
            });
        return ECOModularUI.create(root, player);
    }

    private static UIElement title(ECOMachineInterfaceBlockEntity<?> blockEntity) {
        return new TextElement()
            .setText(GuiTitleProvider.title(blockEntity.getBlockState(),
                Component.translatable("block.neoecoae.computation_interface")))
            .textStyle(style -> style.adaptiveHeight(true).adaptiveWidth(true)
                .textWrap(TextWrap.NONE).textColor(0x3F3D52).textShadow(false))
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8).top(6).width(160).height(12));
    }

    private static UIElement fuzzyHint() {
        return new TextElement()
            .setText(Component.translatable("gui.neoecoae.computation_interface.hint"))
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
                ItemSlot itemSlot = new FuzzyPlanningItemSlot(computationInterface, slot);
                // Phantom handlers load client-only recipe-viewer classes.
                if (computationInterface.getLevel() != null && computationInterface.getLevel().isClientSide()) {
                    itemSlot.xeiPhantom();
                }
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
        for (InventorySlots.Row row : playerInventory.rows) {
            row.apply(slot -> slot.transform(transform -> transform.translate(0, 1)));
        }
        return playerInventory;
    }

    /** A filter slot: its displayed stack is configuration only, never an item transfer. */
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
                    event.hasHandler = true;
                    event.stopImmediatePropagation();
                    return;
                }
                if (event.button == 0) {
                    var player = Minecraft.getInstance().player;
                    ItemStack carried = player == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
                    if (!carried.isEmpty()) {
                        setValue(carried, true);
                        event.hasHandler = true;
                        event.stopImmediatePropagation();
                    }
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

        /*
         * 过滤样本只能经由 setFuzzyPlanningFilter RPC 写入。原版点击、拖拽、数字键交换与
         * Shift 快速移动都不得把该槽视作真实容器，否则会把玩家物品放入或与样本合并。
         */
        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        /*
         * 上限必须不小于槽内样本数量 1。原版 Slot.safeInsert 与拖拽分配按
         * “上限 - 现有数量”计算转移量，上限为 0 时得到 -1，会反向增长手持物品，
         * 造成刷物品并让不可堆叠物品叠加。
         */
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
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
