package cn.dancingsnow.neoecoae.gui.crafting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Pure NBT codec used by the structured crafting-host sync element. */
final class CraftingHostBatchSyncCodec {
    private CraftingHostBatchSyncCodec() {
    }

    static CompoundTag encode(List<CraftingHostBatchSyncElement.HostBatchData> hosts) {
        CompoundTag payload = new CompoundTag();
        ListTag list = new ListTag();
        if (hosts != null) {
            for (CraftingHostBatchSyncElement.HostBatchData host : hosts) {
                CompoundTag item = new CompoundTag();
                item.putBoolean("highEnergy", host.highEnergy());
                item.putInt("threads", host.threads());
                item.putLong("batch", host.batch());
                list.add(item);
            }
        }
        payload.put("hosts", list);
        return payload;
    }

    static List<CraftingHostBatchSyncElement.HostBatchData> decode(@Nullable CompoundTag payload) {
        List<CraftingHostBatchSyncElement.HostBatchData> hosts = new ArrayList<>();
        if (payload == null || !payload.contains("hosts", Tag.TAG_LIST)) return hosts;
        ListTag list = payload.getList("hosts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            if (item.contains("highEnergy", Tag.TAG_BYTE) && item.contains("threads", Tag.TAG_INT)
                    && item.contains("batch", Tag.TAG_LONG)) {
                hosts.add(new CraftingHostBatchSyncElement.HostBatchData(
                        item.getBoolean("highEnergy"), item.getInt("threads"), item.getLong("batch")));
            }
        }
        return hosts;
    }
}
