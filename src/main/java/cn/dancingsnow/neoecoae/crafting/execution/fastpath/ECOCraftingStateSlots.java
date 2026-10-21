package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingContainer;

/** Copies 1.20.1's full-grid recipe remainders without changing slot identity. */
public final class ECOCraftingStateSlots {
    private ECOCraftingStateSlots() {
    }

    public static List<ItemStack> expandRemainingItems(
        CraftingContainer input,
        List<ItemStack> remainingItems,
        int gridWidth,
        int gridHeight
    ) {
        if (input.getWidth() != gridWidth || input.getHeight() != gridHeight) {
            throw new IllegalArgumentException("Crafting remainder grid does not match input");
        }
        List<ItemStack> copiedRemainders = new ArrayList<>(remainingItems.size());
        for (ItemStack remainder : remainingItems) {
            copiedRemainders.add(remainder == null ? ItemStack.EMPTY : remainder.copy());
        }
        return expandSlots(
            copiedRemainders,
            0,
            0,
            input.getWidth(),
            input.getHeight(),
            gridWidth,
            gridHeight,
            ItemStack.EMPTY
        );
    }

    static <T> List<T> expandSlots(
        List<T> positionedSlots,
        int left,
        int top,
        int positionedWidth,
        int positionedHeight,
        int gridWidth,
        int gridHeight,
        T emptySlot
    ) {
        if (gridWidth <= 0 || gridHeight <= 0
                || left < 0 || top < 0
                || positionedWidth < 0 || positionedHeight < 0
                || left + positionedWidth > gridWidth
                || top + positionedHeight > gridHeight
                || positionedSlots.size() != positionedWidth * positionedHeight) {
            throw new IllegalArgumentException("Invalid positioned crafting remainder dimensions");
        }

        List<T> expanded = new ArrayList<>(
            Collections.nCopies(gridWidth * gridHeight, emptySlot)
        );
        for (int y = 0; y < positionedHeight; y++) {
            for (int x = 0; x < positionedWidth; x++) {
                int inputIndex = y * positionedWidth + x;
                int gridIndex = (top + y) * gridWidth + left + x;
                expanded.set(gridIndex, positionedSlots.get(inputIndex));
            }
        }
        return expanded;
    }
}
