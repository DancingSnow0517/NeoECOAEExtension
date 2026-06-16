package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class NEHostSnapshots {
    static final int MAX_STORAGE_TYPES = 256;
    static final int MAX_STORAGE_MATRIX = 256;
    static final int MAX_TASKS = 128;
    static final int MAX_MODULE_CELLS = 256;
    static final byte[] EMPTY = new byte[0];

    private NEHostSnapshots() {
    }

    static byte[] encode(Consumer<RegistryFriendlyByteBuf> writer) {
        try {
            return ByteBufUtil.writeCustomData(writer, Platform.getFrozenRegistry());
        } catch (RuntimeException ignored) {
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
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void writeTypeStats(RegistryFriendlyByteBuf buf, List<NEStorageTypeStat> stats) {
        int size = Math.min(stats.size(), MAX_STORAGE_TYPES);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            NEStorageTypeStat stat = stats.get(i);
            buf.writeResourceLocation(stat.typeId());
            buf.writeUtf(stat.displayName().getString());
            buf.writeVarLong(safeLong(stat.usedTypes()));
            buf.writeVarLong(safeLong(stat.totalTypes()));
            buf.writeVarLong(safeLong(stat.usedBytes()));
            buf.writeVarLong(safeLong(stat.totalBytes()));
        }
    }

    static List<NEStorageTypeStat> readTypeStats(RegistryFriendlyByteBuf buf) {
        int size = safeListSize(buf.readVarInt(), MAX_STORAGE_TYPES);
        List<NEStorageTypeStat> stats = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation typeId = buf.readResourceLocation();
            String displayName = buf.readUtf(256);
            long usedTypes = safeValue(buf.readVarLong());
            long totalTypes = safeValue(buf.readVarLong());
            long usedBytes = safeValue(buf.readVarLong());
            long totalBytes = safeValue(buf.readVarLong());
            stats.add(new NEStorageTypeStat(
                typeId,
                net.minecraft.network.chat.Component.literal(displayName),
                constant(usedTypes),
                constant(totalTypes),
                constant(usedBytes),
                constant(totalBytes)
            ));
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

    private static long safeLong(LongSupplier supplier) {
        return safeValue(supplier.getAsLong());
    }

    private static long safeValue(long value) {
        return Math.max(0L, value);
    }

    private static LongSupplier constant(long value) {
        return () -> value;
    }

    private static int safeListSize(int size, int max) {
        return Math.clamp(size, 0, max);
    }

}
