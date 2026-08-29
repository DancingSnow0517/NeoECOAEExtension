package cn.dancingsnow.neoecoae.api.me;

import appeng.api.stacks.AEKey;
import appeng.menu.guisync.PacketWritable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Cycle members synchronized with the crafting confirmation screen. */
public record ECOCycleItemList(List<Entry> items) implements PacketWritable {
    public static final ECOCycleItemList EMPTY = new ECOCycleItemList(List.of());

    public ECOCycleItemList {
        items = List.copyOf(items);
    }

    public ECOCycleItemList(RegistryFriendlyByteBuf data) {
        this(readFromPacket(data));
    }

    private static List<Entry> readFromPacket(RegistryFriendlyByteBuf data) {
        int size = data.readVarInt();
        List<Entry> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(new Entry(AEKey.readKey(data), data.readLong(), data.readLong()));
        }
        return items;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeVarInt(items.size());
        for (Entry item : items) {
            AEKey.writeKey(data, item.what());
            data.writeLong(item.availableAmount());
            data.writeLong(item.netOutput());
        }
    }

    public record Entry(AEKey what, long availableAmount, long netOutput) {}
}
