package cn.dancingsnow.neoecoae.gui.task;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Package-private, Minecraft-runtime-free delta protocol for task snapshots. */
final class HostTaskListProtocolState {
    record Entry(String id, CompoundTag tag) {
        Entry {
            tag = tag.copy();
        }
    }

    private Map<String, Entry> server = Map.of();
    private List<String> order = List.of();
    private int total;
    private long seq;
    private CompoundTag lastPayload = new CompoundTag();

    private Map<String, Entry> client = new LinkedHashMap<>();
    private List<String> clientOrder = List.of();
    private int clientTotal;
    private long clientSeq = Long.MIN_VALUE;

    CompoundTag createDelta(List<Entry> source) {
        source = source == null ? List.of() : source;

        Map<String, Entry> current = new LinkedHashMap<>();
        List<String> currentOrder = new ArrayList<>();
        for (Entry entry : source) {
            if (current.size() == HostTaskListSyncState.MAX_SYNCED_TASKS
                    || current.putIfAbsent(entry.id(), entry) != null) {
                continue;
            }
            currentOrder.add(entry.id());
        }

        CompoundTag payload = payload(current, currentOrder, source.size(), seq + 1);
        for (String oldId : order) {
            while (payload.sizeInBytes() > HostTaskListSyncState.MAX_SYNCED_TASK_BYTES
                    && !current.containsKey(oldId)) {
                while (current.size() >= HostTaskListSyncState.MAX_SYNCED_TASKS) {
                    String id = currentOrder.removeLast();
                    current.remove(id);
                }
                current.put(oldId, server.get(oldId));
                currentOrder.add(oldId);
                payload = payload(current, currentOrder, source.size(), seq + 1);
            }
        }

        while (payload.sizeInBytes() > HostTaskListSyncState.MAX_SYNCED_TASK_BYTES
                && !current.isEmpty()) {
            String id = currentOrder.removeLast();
            current.remove(id);
            payload = payload(current, currentOrder, source.size(), seq + 1);
        }

        if (payload.sizeInBytes() > HostTaskListSyncState.MAX_SYNCED_TASK_BYTES) {
            throw new IllegalStateException("Task sync metadata exceeds the wire budget");
        }
        if (current.equals(server) && currentOrder.equals(order) && total == source.size()) {
            return lastPayload.copy();
        }

        payload.putLong(HostTaskListSyncState.NBT_SEQUENCE, ++seq);
        server = Map.copyOf(current);
        order = List.copyOf(currentOrder);
        total = source.size();
        lastPayload = payload.copy();
        return payload;
    }

    void apply(CompoundTag payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        if (payload.contains(HostTaskListSyncState.NBT_SEQUENCE, Tag.TAG_LONG)) {
            long sequence = payload.getLong(HostTaskListSyncState.NBT_SEQUENCE);
            if (sequence <= clientSeq) {
                return;
            }
            clientSeq = sequence;
        }

        Map<String, Entry> next = new LinkedHashMap<>(client);
        if (payload.contains(HostTaskListSyncState.NBT_REMOVED, Tag.TAG_LIST)) {
            ListTag removed = payload.getList(HostTaskListSyncState.NBT_REMOVED, Tag.TAG_STRING);
            for (int i = 0; i < removed.size(); i++) {
                next.remove(removed.getString(i));
            }
        }
        if (payload.contains(HostTaskListSyncState.NBT_UPDATES, Tag.TAG_LIST)) {
            ListTag updates = payload.getList(HostTaskListSyncState.NBT_UPDATES, Tag.TAG_COMPOUND);
            for (int i = 0; i < updates.size(); i++) {
                CompoundTag tag = updates.getCompound(i);
                String id = tag.getString("id");
                next.put(id, new Entry(id, tag));
            }
        }

        List<String> nextOrder = order(next, payload);
        client = next;
        clientOrder = List.copyOf(nextOrder);
        clientTotal = payload.contains(HostTaskListSyncState.NBT_TOTAL, Tag.TAG_INT)
                ? payload.getInt(HostTaskListSyncState.NBT_TOTAL)
                : clientOrder.size();
    }

    List<Entry> clientEntries() {
        return clientOrder.stream().map(client::get).toList();
    }

    List<String> clientIds() {
        return clientOrder;
    }

    int clientTotal() {
        return clientTotal;
    }

    private List<String> order(Map<String, Entry> next, CompoundTag payload) {
        List<String> nextOrder = new ArrayList<>();
        if (payload.contains(HostTaskListSyncState.NBT_ORDER, Tag.TAG_LIST)) {
            ListTag wireOrder = payload.getList(HostTaskListSyncState.NBT_ORDER, Tag.TAG_STRING);
            for (int i = 0; i < wireOrder.size(); i++) {
                String id = wireOrder.getString(i);
                if (next.containsKey(id) && !nextOrder.contains(id)) {
                    nextOrder.add(id);
                }
            }
        } else {
            for (String id : clientOrder) {
                if (next.containsKey(id)) {
                    nextOrder.add(id);
                }
            }
        }
        for (String id : next.keySet()) {
            if (!nextOrder.contains(id)) {
                nextOrder.add(id);
            }
        }
        return nextOrder;
    }

    private CompoundTag payload(Map<String, Entry> current, List<String> currentOrder, int total, long sequence) {
        CompoundTag payload = new CompoundTag();
        payload.putLong(HostTaskListSyncState.NBT_SEQUENCE, sequence);
        payload.putInt(HostTaskListSyncState.NBT_TOTAL, total);

        ListTag updates = new ListTag();
        ListTag removed = new ListTag();
        ListTag wireOrder = new ListTag();
        for (Map.Entry<String, Entry> entry : current.entrySet()) {
            if (!entry.getValue().equals(server.get(entry.getKey()))) {
                updates.add(entry.getValue().tag());
            }
        }
        for (String id : server.keySet()) {
            if (!current.containsKey(id)) {
                removed.add(StringTag.valueOf(id));
            }
        }
        if (!updates.isEmpty()) {
            payload.put(HostTaskListSyncState.NBT_UPDATES, updates);
        }
        if (!removed.isEmpty()) {
            payload.put(HostTaskListSyncState.NBT_REMOVED, removed);
        }
        for (String id : currentOrder) {
            wireOrder.add(StringTag.valueOf(id));
        }
        payload.put(HostTaskListSyncState.NBT_ORDER, wireOrder);
        return payload;
    }
}
