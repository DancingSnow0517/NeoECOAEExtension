package cn.dancingsnow.neoecoae.items;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.localization.PlayerMessages;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Shared disassembly of an empty ECO storage cell held in the player's selected slot.
 */
public final class CellDisassembly {

    private CellDisassembly() {
    }

    /**
     * Replaces the held cell with its recipe result and drops its upgrades. Callers handle the
     * alternate-use gate and any item-specific guards before calling this.
     *
     * @param isEmptyCell whether the cell holds nothing; false when its contents are unavailable
     * @return true on the client, where the server decides the outcome; otherwise whether the cell was disassembled
     */
    public static boolean disassemble(ItemStack stack, Level level, Player player,
                                      IUpgradeInventory upgrades, BooleanSupplier isEmptyCell) {
        // Only the server can authoritatively inspect contents and replace the held cell.
        // Omni's SavedData inventory is unavailable on the client, not necessarily empty.
        if (level.isClientSide()) {
            return true;
        }

        List<ItemStack> disassembledStacks = StorageCellDisassemblyRecipe.getDisassemblyResult(level, stack.getItem());
        if (disassembledStacks.isEmpty()) {
            return false;
        }

        Inventory playerInventory = player.getInventory();
        if (playerInventory.getSelected() != stack) {
            return false;
        }

        if (!isEmptyCell.getAsBoolean()) {
            player.displayClientMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
            return false;
        }

        playerInventory.setItem(playerInventory.selected, ItemStack.EMPTY);

        // Drop items from the recipe.
        for (var disassembledStack : disassembledStacks) {
            playerInventory.placeItemBackInInventory(disassembledStack.copy());
        }

        // Drop upgrades
        upgrades.forEach(playerInventory::placeItemBackInInventory);

        return true;
    }
}
