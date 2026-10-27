package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.storage.cells.ISaveProvider;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Handlers are registered once during mod setup and never change afterwards; lookups are lock-free reads of an
 * immutable array. The handlers own their state: ECO and creative disks build a fresh view over the item stack they
 * are given, and Omni UUID claims are made and released on the server thread only.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ECOStorageCells {
    private static volatile IECOCellHandler[] handlers = new IECOCellHandler[0];

    public static synchronized void register(IECOCellHandler handler) {
        if (Arrays.asList(handlers).contains(handler)) {
            throw new IllegalArgumentException("Tried to register the same handler instance twice.");
        }
        IECOCellHandler[] next = Arrays.copyOf(handlers, handlers.length + 1);
        next[handlers.length] = handler;
        handlers = next;
    }

    public static boolean isCellHandled(ItemStack stack) {
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
    public static IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
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

    public static void releaseCellInventory(@Nullable ItemStack stack, @Nullable ISaveProvider host) {
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

    public static void clearRuntimeState() {
        for (IECOCellHandler handler : handlers) {
            handler.clearRuntimeState();
        }
    }
}
