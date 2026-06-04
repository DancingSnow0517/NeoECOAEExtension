package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import static cn.dancingsnow.neoecoae.gui.nativeui.layout.NECraftingPatternBusLayout.*;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.gui.nativeui.layout.NECraftingPatternBusLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Menu for the ECO Crafting Pattern Bus — 9×7 pattern slots (63) + player inventory + hotbar.
 * <p>
 * Slot coordinates are imported from {@link NECraftingPatternBusLayout}.
 * </p>
 */
public class NECraftingPatternBusMenu extends NEBaseMachineMenu {

    public static final int PATTERN_SLOTS = PATTERN_COLS * PATTERN_ROWS; // 63
    public static final int PLAYER_INV_SLOTS = 36;

    public NECraftingPatternBusMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.CRAFTING_PATTERN_BUS.get(), containerId, playerInv, machinePos);

        BlockEntity be = playerInv.player.level().getBlockEntity(machinePos);
        if (be instanceof ECOCraftingPatternBusBlockEntity bus) {
            IItemHandler handler = bus.itemHandler;
            // Pattern slots 9×7
            for (int row = 0; row < PATTERN_ROWS; row++) {
                for (int col = 0; col < PATTERN_COLS; col++) {
                    addSlot(new SlotItemHandler(
                            handler,
                            col + row * PATTERN_COLS,
                            PATTERN_SLOT_X + col * SLOT_SIZE,
                            PATTERN_SLOT_Y + row * SLOT_SIZE));
                }
            }
        }

        // Player inventory 3×9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(
                        playerInv, col + row * 9 + 9, INV_SLOT_X + col * SLOT_SIZE, INV_SLOT_Y + row * SLOT_SIZE));
            }
        }
        // Player hotbar 1×9
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, HOTBAR_SLOT_X + col * SLOT_SIZE, HOTBAR_SLOT_Y));
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = PATTERN_SLOTS;
        int playerEnd = playerStart + PLAYER_INV_SLOTS;
        if (index < PATTERN_SLOTS) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, PATTERN_SLOTS, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
