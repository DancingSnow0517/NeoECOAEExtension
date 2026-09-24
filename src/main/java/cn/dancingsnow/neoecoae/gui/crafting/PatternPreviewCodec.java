package cn.dancingsnow.neoecoae.gui.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        buf.writeVarInt(payload.getInt("menu"));
        buf.writeVarInt(payload.getInt("revision"));
        buf.writeVarInt(payload.getInt("base"));
        buf.writeVarInt(payload.getInt("size"));
        buf.writeByte((payload.getBoolean("full") ? 1 : 0)
                | (payload.getBoolean("first") ? 2 : 0) | (payload.getBoolean("last") ? 4 : 0));
        ListTag entries = payload.getList("entries", Tag.TAG_COMPOUND);
        Map<CompoundTag, Integer> dictionary = new HashMap<>();
        List<CompoundTag> contents = new ArrayList<>();
        List<Run> runs = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            CompoundTag content = entry.copy();
            content.remove("index");
            content.remove("bus");
            content.remove("slot");
            if (content.getCompound("stack").isEmpty()) content.remove("stack");
            if (content.getString("keywords").isEmpty()) content.remove("keywords");
            if (content.getByte("flags") == 0) content.remove("flags");
            if (content.getList("disk", Tag.TAG_COMPOUND).isEmpty()) content.remove("disk");
            if (!content.getBoolean("diskSlot")) content.remove("diskSlot");
            int id = -1;
            if (!content.isEmpty()) {
                id = dictionary.computeIfAbsent(content, key -> {
                    contents.add(key);
                    return contents.size() - 1;
                });
            }
            int index = entry.getInt("index");
            long bus = entry.getLong("bus");
            int slot = entry.getInt("slot");
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
        int size = bounded(buf.readVarInt(), MAX_SLOTS);
        payload.putInt("size", size);
        int flags = buf.readUnsignedByte();
        payload.putBoolean("full", (flags & 1) != 0);
        payload.putBoolean("first", (flags & 2) != 0);
        payload.putBoolean("last", (flags & 4) != 0);
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
