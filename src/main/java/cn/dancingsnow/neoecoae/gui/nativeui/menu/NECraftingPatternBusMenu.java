package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
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
 * Slot coordinates must stay in sync with {@code NECraftingPatternBusScreen}.
 * </p>
 */
public class NECraftingPatternBusMenu extends NEBaseMachineMenu {

    private static final int COLS = 9;
    private static final int ROWS = 7;
    public static final int PATTERN_SLOTS = COLS * ROWS; // 63
    public static final int PLAYER_INV_SLOTS = 36;

    // ── Screen-aligned slot origins ──────────────────────────
    private static final int PATTERN_X = 5;
    private static final int PATTERN_Y = 29;
    private static final int INV_X     = 5;
    private static final int INV_Y     = 164;
    private static final int HOTBAR_X  = 5;
    private static final int HOTBAR_Y  = 225;

    public NECraftingPatternBusMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.CRAFTING_PATTERN_BUS.get(), containerId, playerInv, machinePos);

        BlockEntity be = playerInv.player.level().getBlockEntity(machinePos);
        if (be instanceof ECOCraftingPatternBusBlockEntity bus) {
            IItemHandler handler = bus.itemHandler;
            // Pattern slots 9×7
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    addSlot(new SlotItemHandler(handler, col + row * COLS,
                        PATTERN_X + col * 18, PATTERN_Y + row * 18));
                }
            }
        }

        // Player inventory 3×9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                    INV_X + col * 18, INV_Y + row * 18));
            }
        }
        // Player hotbar 1×9
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col,
                HOTBAR_X + col * 18, HOTBAR_Y));
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
