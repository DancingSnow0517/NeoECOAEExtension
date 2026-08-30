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
            items.add(new Entry(AEKey.readKey(data), data.readLong(), data.readLong(), data.readVarInt()));
        }
        return items;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeVarInt(items.size());
        for (Entry item : items) {
            AEKey.writeKey(data, item.what());
            data.writeLong(item.singleNetOutput());
            data.writeLong(item.totalNetOutput());
            data.writeVarInt(item.componentId());
        }
    }

    public record Entry(AEKey what, long singleNetOutput, long totalNetOutput, int componentId) {
        /** Compatibility constructor for callers that only have the old three numeric fields. */
        public Entry(AEKey what, long singleNetOutput, long totalNetOutput) {
            this(what, singleNetOutput, totalNetOutput, -1);
        }
    }
}
