package cn.dancingsnow.neoecoae.gui.workstation;

import appeng.api.inventories.InternalInventory;
import appeng.items.contents.NetworkToolMenuHost;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.slot.ItemHandlerSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory.InventorySlots;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import com.google.gson.JsonObject;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.FlexDirection;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Player inventory locking and the nine network-tool upgrade slots. */
final class WorkstationInventoryUI {
    private WorkstationInventoryUI() {}
    static UIElement createNetworkToolbox(NetworkToolMenuHost networkTool) {
        return WorkstationUiJson.load(new ResourceLocation("neoecoae", "screens/workstation/network_toolbox"),
            new JsonContext(networkTool));
    }

    private static UIElement createLegacyNetworkToolbox(NetworkToolMenuHost networkTool) {
        var itemHandler = new NetworkToolItemHandler(networkTool);
        UIElement toolbox = new UIElement();

        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            UIElement row = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW));
            for (int columnIndex = 0; columnIndex < 3; columnIndex++) {
                int slot = rowIndex * 3 + columnIndex;
                row.addChild(new ItemSlot(new ItemHandlerSlot(itemHandler, slot))
                    .slotStyle(style -> style
                        .slotOverlay(AETextures.icon(ECOIcon.BACKGROUND_UPGRADE))
                        .showSlotOverlayOnlyEmpty(true))
                    .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
            }
            toolbox.addChild(row);
        }
        return toolbox;
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final NetworkToolMenuHost networkTool;

        private JsonContext(NetworkToolMenuHost networkTool) {
            this.networkTool = Objects.requireNonNull(networkTool, "networkTool");
        }

        @Override
        public UIElement createCustom(JsonObject node) {
            if ("content".equals(string(node, "custom", ""))) {
                return createLegacyNetworkToolbox(networkTool);
            }
            throw new IllegalArgumentException("Unknown workstation inventory custom element: "
                + string(node, "custom", ""));
        }

        private static String string(JsonObject node, String key, String fallback) {
            return node.has(key) && node.get(key).isJsonPrimitive() ? node.get(key).getAsString() : fallback;
        }
    }

    static InventorySlots createPlayerInventory(Inventory inventory, @Nullable Integer lockedSlot) {
        var slots=new InventorySlots();
        for(int row=0;row<slots.rows.length;row++)for(int column=0;column<slots.rows[row].slots.length;column++)
            slots.rows[row].slots[column].bind(playerSlot(inventory,row*9+column+9,lockedSlot));
        for(int slot=0;slot<slots.hotbar.slots.length;slot++)slots.hotbar.slots[slot].bind(playerSlot(inventory,slot,lockedSlot));
        return slots;
    }

    private static Slot playerSlot(Inventory inventory, int slotIndex, @Nullable Integer lockedSlot) {
        if (lockedSlot == null || lockedSlot != slotIndex) {
            return new Slot(inventory, slotIndex, 0, 0);
        }
        return new Slot(inventory, slotIndex, 0, 0) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(@NotNull Player player) {
                return false;
            }
        };
    }

    private record NetworkToolItemHandler(NetworkToolMenuHost networkTool) implements IItemHandlerModifiable {
        private InternalInventory inventory() {
            return (networkTool.getSlot()==null || net.minecraft.world.item.ItemStack.isSameItem(networkTool.getPlayer().getInventory().getItem(networkTool.getSlot()),networkTool.getItemStack())) ? networkTool.getInventory() : InternalInventory.empty();
        }

        @Override
        public int getSlots() {
            return 9;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inventory().getStackInSlot(slot);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            var inventory = inventory();
            if (inventory.size() == 9 && (stack.isEmpty() || inventory.isItemValid(slot, stack))) {
                inventory.setItemDirect(slot, stack);
            }
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return inventory().insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inventory().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return inventory().isItemValid(slot, stack);
        }
    }

}
