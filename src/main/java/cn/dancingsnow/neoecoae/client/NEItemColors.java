package cn.dancingsnow.neoecoae.client;

import appeng.api.storage.cells.CellState;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Tints the status-light layer of ECO cell icons with the cell's AE2 state colour. Standard cells
 * use layer 2; the infinite resource cell has no tier-light layer, so its status light is layer 1.
 *
 * <p>ECO cells are not registered with AE2's {@code StorageCells} registry, so
 * {@code StorageCells.getCellInventory} always returns null for them. Looking the state up that way
 * pinned every ECO cell to the same colour regardless of its real contents; the ECO registry is the
 * correct source instead.
 *
 * <p>This runs on the render path, and resolving an ECO inventory rebuilds a cell instance, so the
 * resolved colour is memoised per stack identity. Identity is the right key because the tint is
 * requested for one stack object many times per frame; a content change re-tags or replaces that
 * object, which reads as a miss and picks up the new state. The map is bounded because anonymous
 * stacks would otherwise accumulate for the lifetime of the client.
 */
public final class NEItemColors {
    private static final int STATUS_LIGHT_TINT_INDEX = 2;

    /** Beyond this many tracked stacks the memo is dropped rather than growing without bound. */
    private static final int MAX_CACHED_STACKS = 512;

    private static final Map<ItemStack, Integer> stateColorCache = new IdentityHashMap<>();

    private NEItemColors() {
    }

    public static int getCellColor(ItemStack stack, int tintIndex) {
        int statusLightTintIndex = stack.getItem() instanceof ECOInfiniteResourceCellItem
            ? 1
            : STATUS_LIGHT_TINT_INDEX;
        if (tintIndex != statusLightTintIndex) {
            return 0xFFFFFFFF;
        }
        return FastColor.ARGB32.opaque(stateColor(stack));
    }

    private static int stateColor(ItemStack stack) {
        Integer cached = stateColorCache.get(stack);
        if (cached != null) {
            return cached;
        }

        int color = resolveStateColor(stack);
        if (stateColorCache.size() >= MAX_CACHED_STACKS) {
            stateColorCache.clear();
        }
        stateColorCache.put(stack, color);
        return color;
    }

    private static int resolveStateColor(ItemStack stack) {
        IECOStorageCell cell = ECOStorageCells.getCellInventory(stack, null);
        if (cell == null) {
            // Without an ECO inventory there is no state to report.
            return CellState.ABSENT.getStateColor();
        }
        CellState state = cell.getStatus();
        return state != null ? state.getStateColor() : CellState.ABSENT.getStateColor();
    }

    /** Drops memoised colours so no stack identity outlives a client session. */
    public static void clearCache() {
        stateColorCache.clear();
    }
}
