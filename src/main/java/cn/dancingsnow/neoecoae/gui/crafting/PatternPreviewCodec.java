package cn.dancingsnow.neoecoae.gui.crafting;

import java.util.ArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Compact physical-slot pages: shared contents and runs of consecutive slots, including empty capacity. */
public final class PatternPreviewCodec {
    public static final int MAX_SLOTS = 1_048_576;
    private static final int MAX_PAGE_ENTRIES = 256;

    private PatternPreviewCodec() {}

    public static void write(RegistryFriendlyByteBuf buf, CompoundTag payload) {
        buf.writeVarInt(payload.getIntOr("menu", 0));
        buf.writeVarInt(payload.getIntOr("revision", 0));
        buf.writeVarInt(payload.getIntOr("base", 0));
        buf.writeLong(payload.getLongOr("epoch", 0L));
        buf.writeVarInt(payload.getIntOr("size", 0));
        buf.writeByte((payload.getBooleanOr("full", false) ? 1 : 0)
                | (payload.getBooleanOr("first", false) ? 2 : 0) | (payload.getBooleanOr("last", false) ? 4 : 0)
                | (payload.getBooleanOr("reuse", false) ? 8 : 0) | (payload.contains("layout") ? 16 : 0));
        if (payload.contains("layout")) {
            ListTag layout = payload.getListOrEmpty("layout");
            buf.writeVarInt(layout.size());
            for (int i = 0; i < layout.size(); i++) {
                buf.writeLong(layout.getCompoundOrEmpty(i).getLongOr("bus", 0L));
                buf.writeVarInt(layout.getCompoundOrEmpty(i).getIntOr("count", 0));
            }
            int[] occupied = payload.getIntArray("occupied").orElseGet(() -> new int[0]);
            buf.writeVarInt(occupied.length);
            for (int index : occupied) buf.writeVarInt(index);
        }
        ListTag entries = payload.getListOrEmpty("entries");
        Object2IntOpenHashMap<CompoundTag> dictionary = new Object2IntOpenHashMap<>();
        dictionary.defaultReturnValue(-1);
        List<CompoundTag> contents = new ArrayList<>();
        List<Run> runs = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            CompoundTag content = entry.copy();
            content.remove("index");
            content.remove("bus");
            content.remove("slot");
            if (content.getCompoundOrEmpty("stack").isEmpty()) content.remove("stack");
            if (content.getStringOr("inputs", "").isEmpty()) content.remove("inputs");
            if (content.getStringOr("output", "").isEmpty()) content.remove("output");
            if (content.getListOrEmpty("disk").isEmpty()) content.remove("disk");
            if (!content.getBooleanOr("diskSlot", false)) content.remove("diskSlot");
            int id = -1;
            if (!content.isEmpty()) {
                id = dictionary.computeIntIfAbsent(content, key -> {
                    contents.add(key);
                    return contents.size() - 1;
                });
            }
            int index = entry.getIntOr("index", 0);
            long bus = entry.getLongOr("bus", 0L);
            int slot = entry.getIntOr("slot", 0);
            Run last = runs.isEmpty() ? null : runs.getLast();
            if (last != null && last.content == id && last.bus == bus
                    && last.index + last.count == index && last.slot + last.count == slot) last.count++;
            else runs.add(new Run(index, bus, slot, id));
        }
        buf.writeVarInt(contents.size());
        for (CompoundTag content : contents) buf.writeNbt(content);
        buf.writeVarInt(runs.size());
        for (Run run : runs) {
            buf.writeVarInt(run.index);
            buf.writeLong(run.bus);
            buf.writeVarInt(run.slot);
            buf.writeVarInt(run.count);
            buf.writeVarInt(run.content + 1);
        }
    }

    public static CompoundTag read(RegistryFriendlyByteBuf buf) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("menu", buf.readVarInt());
        payload.putInt("revision", buf.readVarInt());
        payload.putInt("base", buf.readVarInt());
        payload.putLong("epoch", buf.readLong());
        int size = bounded(buf.readVarInt(), MAX_SLOTS);
        payload.putInt("size", size);
        int flags = buf.readUnsignedByte();
        payload.putBoolean("full", (flags & 1) != 0);
        payload.putBoolean("first", (flags & 2) != 0);
        payload.putBoolean("last", (flags & 4) != 0);
        payload.putBoolean("reuse", (flags & 8) != 0);
        if ((flags & 16) != 0) {
            if ((flags & 3) != 3) throw new IllegalArgumentException("Layout outside first full page");
            ListTag layout = new ListTag();
            int buses = bounded(buf.readVarInt(), MAX_SLOTS);
            int total = 0;
            for (int i = 0; i < buses; i++) {
                CompoundTag bus = new CompoundTag();
                bus.putLong("bus", buf.readLong());
                int slots = bounded(buf.readVarInt(), size - total);
                bus.putInt("count", slots);
                total += slots;
                layout.add(bus);
            }
            if (total != size) throw new IllegalArgumentException("Preview layout size mismatch");
            int[] occupied = new int[bounded(buf.readVarInt(), size)];
            int previous = -1;
            for (int i = 0; i < occupied.length; i++) {
                int index = bounded(buf.readVarInt(), size - 1);
                if (index <= previous) throw new IllegalArgumentException("Unordered occupied slots");
                occupied[i] = previous = index;
            }
            payload.put("layout", layout);
            payload.putIntArray("occupied", occupied);
        }
        int count = bounded(buf.readVarInt(), MAX_PAGE_ENTRIES);
        List<CompoundTag> contents = new ArrayList<>(count);
        NbtAccounter accounting = NbtAccounter.create(32L * 1024 * 1024);
        for (int i = 0; i < count; i++) {
            if (!(buf.readNbt(accounting) instanceof CompoundTag content))
                throw new IllegalArgumentException("Missing preview content");
            contents.add(content);
        }
        int runs = bounded(buf.readVarInt(), MAX_PAGE_ENTRIES);
        ListTag entries = new ListTag();
        int previous = -1;
        for (int i = 0; i < runs; i++) {
            int index = bounded(buf.readVarInt(), size);
            long bus = buf.readLong();
            int slot = bounded(buf.readVarInt(), MAX_SLOTS);
            int length = bounded(buf.readVarInt(), MAX_PAGE_ENTRIES);
            int content = bounded(buf.readVarInt(), contents.size());
            if (length == 0 || index <= previous || index > size - length
                    || slot > MAX_SLOTS - length || entries.size() + length > MAX_PAGE_ENTRIES)
                throw new IllegalArgumentException("Invalid preview run");
            for (int offset = 0; offset < length; offset++) {
                CompoundTag entry = content == 0 ? new CompoundTag() : contents.get(content - 1).copy();
                entry.putInt("index", index + offset);
                entry.putLong("bus", bus);
                entry.putInt("slot", slot + offset);
                entries.add(entry);
            }
            previous = index + length - 1;
        }
        payload.put("entries", entries);
        return payload;
    }

    private static int bounded(int value, int max) {
        if (value < 0 || value > max) throw new IllegalArgumentException("Invalid preview size: " + value);
        return value;
    }

    private static final class Run {
        final int index, slot, content;
        final long bus;
        int count = 1;
        Run(int index, long bus, int slot, int content) {
            this.index = index;
            this.bus = bus;
            this.slot = slot;
            this.content = content;
        }
    }
}
