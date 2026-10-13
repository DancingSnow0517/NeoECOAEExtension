package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ECOStorageCells {
    private static final List<IECOCellHandler> handlers = new ArrayList<>();


    public static void register(IECOCellHandler handler) {
        if (handlers.contains(handler)) {
            throw new IllegalArgumentException("Tried to register the same handler instance twice.");
        }
        handlers.add(handler);
    }

    public static synchronized boolean isCellHandled(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (IECOCellHandler handler : handlers) {
            if (handler.isCell(stack)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static synchronized IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (stack.isEmpty()) {
            return null;
        }
        for (IECOCellHandler handler : handlers) {
            IECOStorageCell inv = handler.getCellInventory(stack, host);
            if (inv != null) {
                return inv;
            }
        }
        return null;
    }

    /** Returns AE2's inventory when present, otherwise the ECO cell inventory for the same stack. */
    @Nullable
    public static StorageCell orEcoCellInventory(@Nullable StorageCell original, ItemStack stack, @Nullable ISaveProvider host) {
        if (original != null) {
            return original;
        }
        return getCellInventory(stack, host);
    }

    public static synchronized void releaseCellInventory(@Nullable ItemStack stack, @Nullable ISaveProvider host) {
        if (stack == null || stack.isEmpty() || host == null) {
            return;
        }
        for (IECOCellHandler handler : handlers) {
            if (handler.isCell(stack)) {
                handler.releaseCellInventory(stack, host);
                return;
            }
        }
    }

    public static synchronized void clearRuntimeState() {
        for (IECOCellHandler handler : handlers) {
            handler.clearRuntimeState();
        }
    }
}
