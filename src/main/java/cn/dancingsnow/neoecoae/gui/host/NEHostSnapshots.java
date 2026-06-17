package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;
import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class NEHostSnapshots {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    static final int MAX_STORAGE_TYPES = 256;
    static final int MAX_STORAGE_MATRIX = 256;
    static final int MAX_TASKS = 128;
    static final int MAX_MODULE_CELLS = 256;
    static final int MAX_ITEM_STACKS = 256;
    static final byte[] EMPTY = new byte[0];

    private NEHostSnapshots() {
    }

    static byte[] encode(Consumer<RegistryFriendlyByteBuf> writer) {
        try {
            return ByteBufUtil.writeCustomData(writer, Platform.getFrozenRegistry());
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to encode NeoECOAE host UI snapshot", e);
            return EMPTY;
        }
    }

    static boolean decode(byte[] snapshot, Consumer<RegistryFriendlyByteBuf> reader) {
        if (snapshot == null || snapshot.length == 0) {
            return false;
        }
        try {
            ByteBufUtil.readCustomData(snapshot, reader, Platform.getFrozenRegistry());
            return true;
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to decode NeoECOAE host UI snapshot ({} bytes)", snapshot.length, e);
            return false;
        }
    }

    static void writeTypeStats(RegistryFriendlyByteBuf buf, List<NEStorageTypeStat> stats) {
        int size = Math.min(stats.size(), MAX_STORAGE_TYPES);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            NEStorageTypeStat stat = stats.get(i);
            buf.writeResourceLocation(stat.typeId());
            ComponentSerialization.STREAM_CODEC.encode(buf, stat.displayName());
            buf.writeVarLong(safeValue(stat.usedTypes()));
            buf.writeVarLong(safeValue(stat.totalTypes()));
            buf.writeVarLong(safeValue(stat.usedBytes()));
            buf.writeVarLong(safeValue(stat.totalBytes()));
        }
    }

    static List<NEStorageTypeStat> readTypeStats(RegistryFriendlyByteBuf buf) {
        int size = safeListSize(buf.readVarInt(), MAX_STORAGE_TYPES);
        List<NEStorageTypeStat> stats = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation typeId = buf.readResourceLocation();
            var displayName = ComponentSerialization.STREAM_CODEC.decode(buf);
            long usedTypes = safeValue(buf.readVarLong());
            long totalTypes = safeValue(buf.readVarLong());
            long usedBytes = safeValue(buf.readVarLong());
            long totalBytes = safeValue(buf.readVarLong());
            stats.add(new NEStorageTypeStat(typeId, displayName, usedTypes, totalTypes, usedBytes, totalBytes));
        }
        return List.copyOf(stats);
    }

    static void writeMatrixCells(RegistryFriendlyByteBuf buf, List<NEStorageMatrixCell> cells) {
        int size = Math.min(cells.size(), MAX_STORAGE_MATRIX);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            NEStorageMatrixCell cell = cells.get(i);
            buf.writeVarInt(cell.row());
            buf.writeVarInt(cell.column());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, cell.stack());
            buf.writeVarInt(cell.tier());
            buf.writeVarLong(safeValue(cell.usedTypes()));
            buf.writeVarLong(safeValue(cell.totalTypes()));
            buf.writeVarLong(safeValue(cell.usedBytes()));
            buf.writeVarLong(safeValue(cell.totalBytes()));
        }
    }

    static List<NEStorageMatrixCell> readMatrixCells(RegistryFriendlyByteBuf buf) {
        int size = safeListSize(buf.readVarInt(), MAX_STORAGE_MATRIX);
        List<NEStorageMatrixCell> cells = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int row = buf.readVarInt();
            int column = buf.readVarInt();
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int tier = buf.readVarInt();
            long usedTypes = safeValue(buf.readVarLong());
            long totalTypes = safeValue(buf.readVarLong());
            long usedBytes = safeValue(buf.readVarLong());
            long totalBytes = safeValue(buf.readVarLong());
            cells.add(new NEStorageMatrixCell(row, column, stack, tier, usedTypes, totalTypes, usedBytes, totalBytes));
        }
        return List.copyOf(cells);
    }

    static void writeTasks(RegistryFriendlyByteBuf buf, List<NECraftingTaskEntry> tasks) {
        int size = Math.min(tasks.size(), MAX_TASKS);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            NECraftingTaskEntry task = tasks.get(i);
            buf.writeUtf(task.id(), 256);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, task.output());
            buf.writeVarLong(safeValue(task.outputAmount()));
            buf.writeVarLong(safeValue(task.craftCount()));
            buf.writeVarLong(safeValue(task.totalTicks()));
            buf.writeVarLong(safeValue(task.remainingTicks()));
            buf.writeVarInt(task.status().ordinal());
        }
    }

    static List<NECraftingTaskEntry> readTasks(RegistryFriendlyByteBuf buf) {
        int size = safeListSize(buf.readVarInt(), MAX_TASKS);
        List<NECraftingTaskEntry> tasks = new ArrayList<>(size);
        NECraftingTaskEntry.Status[] statuses = NECraftingTaskEntry.Status.values();
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf(256);
            ItemStack output = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            long outputAmount = safeValue(buf.readVarLong());
            long craftCount = safeValue(buf.readVarLong());
            long totalTicks = safeValue(buf.readVarLong());
            long remainingTicks = safeValue(buf.readVarLong());
            int statusIndex = Math.clamp(buf.readVarInt(), 0, statuses.length - 1);
            tasks.add(new NECraftingTaskEntry(id, output, outputAmount, craftCount, totalTicks, remainingTicks, statuses[statusIndex]));
        }
        return List.copyOf(tasks);
    }

    static void writeModuleCells(RegistryFriendlyByteBuf buf, List<NECraftingModuleCell> cells) {
        int size = Math.min(cells.size(), MAX_MODULE_CELLS);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            NECraftingModuleCell cell = cells.get(i);
            buf.writeVarInt(cell.column());
            buf.writeVarInt(cell.row().ordinal());
            buf.writeVarInt(cell.tier());
            buf.writeBlockPos(cell.pos());
        }
    }

    static List<NECraftingModuleCell> readModuleCells(RegistryFriendlyByteBuf buf) {
        int size = safeListSize(buf.readVarInt(), MAX_MODULE_CELLS);
        List<NECraftingModuleCell> cells = new ArrayList<>(size);
        NECraftingModuleCell.Row[] rows = NECraftingModuleCell.Row.values();
        for (int i = 0; i < size; i++) {
            int column = buf.readVarInt();
            int rowIndex = Math.clamp(buf.readVarInt(), 0, rows.length - 1);
            int tier = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            cells.add(new NECraftingModuleCell(column, rows[rowIndex], tier, pos));
        }
        return List.copyOf(cells);
    }

    static void writeItemStacks(RegistryFriendlyByteBuf buf, List<ItemStack> stacks) {
        int size = Math.min(stacks.size(), MAX_ITEM_STACKS);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = stacks.get(i);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack == null ? ItemStack.EMPTY : stack);
        }
    }

    static List<ItemStack> readItemStacks(RegistryFriendlyByteBuf buf) {
        int size = safeListSize(buf.readVarInt(), MAX_ITEM_STACKS);
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return List.copyOf(stacks);
    }

    private static long safeValue(long value) {
        return Math.max(0L, value);
    }

    private static int safeListSize(int size, int max) {
        return Math.clamp(size, 0, max);
    }

}
