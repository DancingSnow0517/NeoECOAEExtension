package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Menu-scoped push synchronization. No server-side viewport or client polling. */
public final class PatternPreviewSync {
    private static final int ENTRIES_PER_PACKET = 16;
    private final ECOMachineInterfaceBlockEntity<?> host;
    private final Map<UUID, AbstractContainerMenu> viewers = new HashMap<>();
    private final List<CompoundTag> cachedEntries = new ArrayList<>();
    private final BitSet dirtySlots = new BitSet();
    private boolean reset = true;
    private int cachedRevision = -1;
    private WeakReference<Consumer<CompoundTag>> receiver = new WeakReference<>(null);

    public PatternPreviewSync(ECOMachineInterfaceBlockEntity<?> host) {
        this.host = host;
    }

    public void listen(Consumer<CompoundTag> receiver) {
        this.receiver = new WeakReference<>(receiver);
    }

    public void receive(CompoundTag payload) {
        var listener = receiver.get();
        if (listener != null) listener.accept(payload);
    }

    public void reset() {
        reset = true;
        dirtySlots.clear();
    }

    public void resend(ServerPlayer player) {
        viewers.remove(player.getUUID());
    }

    public void dirty(int firstSlot, int count) {
        dirtySlots.set(firstSlot, firstSlot + count);
    }

    public boolean isViewer(ServerPlayer player) {
        return player.containerMenu instanceof ModularUIContainerMenu menu
                && menu.uiHolder instanceof BlockUIMenuType.BlockUIHolder holder
                && holder.pos.equals(host.getBlockPos())
                && player.level() == host.getLevel()
                && player.blockPosition().distSqr(host.getBlockPos()) <= 64.0D;
    }

    public void tick(ServerLevel level) {
        List<ServerPlayer> active = level.players().stream().filter(this::isViewer).toList();
        viewers.keySet().removeIf(id -> active.stream().noneMatch(player -> player.getUUID().equals(id)));
        if (active.isEmpty()) return;
        int size = host.getPatternInterfaceSlotCount();
        int revision = host.getPatternContentRevision();
        boolean full = reset || cachedEntries.size() != size;
        int previousRevision = cachedRevision;
        List<CompoundTag> changed = new ArrayList<>();
        if (full) {
            cachedEntries.clear();
            for (int index = 0; index < size; index++) cachedEntries.add(encode(index));
        } else {
            for (int index = dirtySlots.nextSetBit(0); index >= 0 && index < size;
                 index = dirtySlots.nextSetBit(index + 1)) {
                CompoundTag entry = encode(index);
                if (!entry.equals(cachedEntries.get(index))) {
                    cachedEntries.set(index, entry);
                    changed.add(entry);
                }
            }
        }
        cachedRevision = revision;
        reset = false;
        dirtySlots.clear();
        for (ServerPlayer player : active) {
            boolean newMenu = viewers.put(player.getUUID(), player.containerMenu) != player.containerMenu;
            if (full || newMenu) send(player, true, -1, cachedEntries);
            else if (previousRevision != revision) send(player, false, previousRevision, changed);
        }
    }

    private CompoundTag encode(int index) {
        CompoundTag entry = host.getPatternPreviewEntry(index).encode(host.getLevel().registryAccess());
        entry.putInt("index", index);
        return entry;
    }

    private void send(ServerPlayer player, boolean full, int baseRevision, List<CompoundTag> entries) {
        // Bounded chunks avoid a single oversized RPC when thousands of buses are connected.
        for (int offset = 0; offset < Math.max(1, entries.size()); offset += ENTRIES_PER_PACKET) {
            CompoundTag payload = new CompoundTag();
            payload.putInt("menu", player.containerMenu.containerId);
            payload.putInt("revision", cachedRevision);
            payload.putInt("base", baseRevision);
            payload.putInt("size", cachedEntries.size());
            payload.putBoolean("full", full);
            payload.putBoolean("first", offset == 0);
            payload.putBoolean("last", offset + ENTRIES_PER_PACKET >= entries.size());
            ListTag batch = new ListTag();
            for (int index = offset; index < Math.min(offset + ENTRIES_PER_PACKET, entries.size()); index++) {
                batch.add(entries.get(index));
            }
            payload.put("entries", batch);
            host.rpcToPlayer(player, "setPatternPreview", payload);
        }
    }
}
