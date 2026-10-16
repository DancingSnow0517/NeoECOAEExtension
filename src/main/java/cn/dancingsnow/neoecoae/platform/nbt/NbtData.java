package cn.dancingsnow.neoecoae.platform.nbt;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.core.io.DataReader;
import cn.dancingsnow.neoecoae.core.io.DataWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Minecraft 1.21.1 structured persistence adapter. GenericStack key tags retain the saved-world schema. */
public final class NbtData implements DataReader<AEKey>, DataWriter<AEKey> {
    private final CompoundTag tag;
    private final HolderLookup.Provider registries;

    public NbtData(CompoundTag tag, HolderLookup.Provider registries) {
        this.tag = tag;
        this.registries = registries;
    }

    @Override public String readString(String name) { return tag.getString(name); }
    @Override public void writeString(String name, String value) { tag.putString(name, value); }
    @Override public boolean contains(String name) { return tag.contains(name); }
    @Override public int readInt(String name) { return tag.getInt(name); }
    @Override public long readLong(String name) { return tag.getLong(name); }
    @Override public boolean readBoolean(String name) { return tag.getBoolean(name); }
    @Override public int[] readIntArray(String name) { return tag.getIntArray(name); }
    @Override public long[] readLongArray(String name) { return tag.getLongArray(name); }
    @Override public DataReader<AEKey> readCompound(String name) { return new NbtData(tag.getCompound(name), registries); }
    @Override public void writeInt(String name, int value) { tag.putInt(name, value); }
    @Override public void writeLong(String name, long value) { tag.putLong(name, value); }
    @Override public void writeBoolean(String name, boolean value) { tag.putBoolean(name, value); }
    @Override public void writeIntArray(String name, int[] values) { tag.putIntArray(name, values); }
    @Override public void writeLongArray(String name, long[] values) { tag.putLongArray(name, values); }
    @Override public void writeCompound(String name, Consumer<DataWriter<AEKey>> contents) {
        var child = new CompoundTag();
        contents.accept(new NbtData(child, registries));
        tag.put(name, child);
    }

    /** Pattern definitions have no generic stack quantity or channel discriminator in the old schema. */
    public AEItemKey readItemKey(String name) { return AEItemKey.fromTag(registries, tag.getCompound(name)); }
    public void writeItemKey(String name, AEItemKey key) { tag.put(name, key.toTag(registries)); }
    /** The execution plan stores stack fields in the current compound, alongside domain metadata. */
    public GenericStack readStack() { return GenericStack.readTag(registries, tag); }
    public void writeStack(AEKey key, long amount) {
        tag.merge(GenericStack.writeTag(registries, new GenericStack(key, amount)));
    }
    @Override public AEKey readKey(String name) {
        var stack = GenericStack.readTag(registries, tag.getCompound(name));
        if (stack == null) throw new IllegalArgumentException("Invalid deferred material");
        return stack.what();
    }
    @Override public void writeKey(String name, AEKey key) {
        tag.put(name, GenericStack.writeTag(registries, new GenericStack(key, 1)));
    }
    @Override public List<DataReader<AEKey>> readList(String name) {
        var list = tag.get(name) instanceof ListTag present ? present : new ListTag();
        var entries = new ArrayList<DataReader<AEKey>>(list.size());
        for (int i = 0; i < list.size(); i++) entries.add(new NbtData(list.getCompound(i), registries));
        return entries;
    }
    @Override public List<DataReader<AEKey>> readCompoundList(String name) {
        var list = tag.getList(name, Tag.TAG_COMPOUND);
        var entries = new ArrayList<DataReader<AEKey>>(list.size());
        for (int i = 0; i < list.size(); i++) entries.add(new NbtData(list.getCompound(i), registries));
        return entries;
    }
    @Override public void writeList(String name, List<Consumer<DataWriter<AEKey>>> entries) {
        var list = new ListTag();
        for (var entry : entries) {
            var child = new CompoundTag();
            entry.accept(new NbtData(child, registries));
            list.add(child);
        }
        tag.put(name, list);
    }
}
