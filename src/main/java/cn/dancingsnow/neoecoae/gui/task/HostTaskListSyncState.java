package cn.dancingsnow.neoecoae.gui.task;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Server delta encoder and client snapshot applier for computation tasks. */
final class HostTaskListSyncState {
    static final String NBT_SEQUENCE = "seq";
    static final String NBT_UPDATES = "updates";
    static final String NBT_REMOVED = "removed";
    static final String NBT_ORDER = "order";
    static final String NBT_TOTAL = "total";
    static final int MAX_SYNCED_TASKS = 96;
    static final int MAX_SYNCED_TASK_BYTES = 128_000;

    private final Supplier<HolderLookup.Provider> registries;
    private final HostTaskListProtocolState protocol = new HostTaskListProtocolState();
    private CompoundTag lastPayload = new CompoundTag();
    private List<ComputationTaskEntry> clientTasks = List.of();

    HostTaskListSyncState(Supplier<HolderLookup.Provider> registries) {
        this.registries = registries;
    }

    CompoundTag createDelta(List<ComputationTaskEntry> entries) {
        List<ComputationTaskEntry> source = entries == null ? List.of() : entries;
        List<HostTaskListProtocolState.Entry> wireEntries = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (ComputationTaskEntry entry : source) {
            if (seen.putIfAbsent(entry.id(), Boolean.TRUE) == null) {
                wireEntries.add(new HostTaskListProtocolState.Entry(
                        entry.id(), entry.writeToNBT(registries.get())));
            }
        }
        lastPayload = protocol.createDelta(wireEntries);
        return lastPayload.copy();
    }

    void apply(CompoundTag payload) {
        protocol.apply(payload);
        clientTasks = protocol.clientEntries().stream()
                .map(entry -> ComputationTaskEntry.readFromNBT(registries.get(), entry.tag()))
                .toList();
    }

    List<ComputationTaskEntry> tasks() {
        return clientTasks;
    }

    int totalTasks() {
        return protocol.clientTotal();
    }

    CompoundTag payload() {
        return lastPayload.copy();
    }

    void setPayload(CompoundTag payload) {
        lastPayload = payload == null ? new CompoundTag() : payload.copy();
        apply(lastPayload);
    }
}
