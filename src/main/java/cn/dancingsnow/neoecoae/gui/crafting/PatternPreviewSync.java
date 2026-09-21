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
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import java.util.function.Consumer;

/** Menu-scoped push synchronization. No server-side viewport or client polling. */
public final class PatternPreviewSync {
    private long nextSyncTick;
    private final ECOMachineInterfaceBlockEntity<?> host;
    private final Map<UUID, Viewer> viewers = new HashMap<>();
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
        MenuDataTransport.cancel(player, MenuDataTransport.Channel.PATTERNS);
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
        if (level.getGameTime() < nextSyncTick) return;
        nextSyncTick = level.getGameTime() + MenuDataTransport.UPDATE_INTERVAL;
        host.refreshPatternCatalog();
        int size = host.getPatternInterfaceSlotCount();
        int revision = host.getPatternContentRevision();
        boolean full = reset || cachedEntries.size() != size;

        if (full) {
            cachedEntries.clear();
            for (int index = 0; index < size; index++) cachedEntries.add(encode(index));
        } else {
            for (int index = dirtySlots.nextSetBit(0); index >= 0 && index < size;
                 index = dirtySlots.nextSetBit(index + 1)) {
                CompoundTag entry = encode(index);
                if (!entry.equals(cachedEntries.get(index))) {
                    cachedEntries.set(index, entry);

                }
            }
        }
        cachedRevision = revision;
        reset = false;
        dirtySlots.clear();
        for (ServerPlayer player : active) {
            Viewer viewer = viewers.get(player.getUUID());
            if (viewer == null || viewer.menu != player.containerMenu) {
                MenuDataTransport.cancel(player, MenuDataTransport.Channel.PATTERNS);
                viewer = new Viewer(player.containerMenu);
                viewers.put(player.getUUID(), viewer);
            }
            // Finish the immutable snapshot before diffing against it. This avoids starvation while
            // automation keeps changing the live catalogue during a multi-tick initial transfer.
            if (MenuDataTransport.busy(player, MenuDataTransport.Channel.PATTERNS)) continue;
            boolean sendFull = viewer.revision < 0 || viewer.entries.size() != cachedEntries.size();
            List<CompoundTag> changes = new ArrayList<>();
            for (int index = 0; index < cachedEntries.size(); index++) {
                if (sendFull || !cachedEntries.get(index).equals(viewer.entries.get(index)))
                    changes.add(cachedEntries.get(index));
            }
            if (sendFull || viewer.revision != cachedRevision || !changes.isEmpty()) {
                send(player, sendFull, viewer.revision, changes);
                viewer.entries = List.copyOf(cachedEntries);
                viewer.revision = cachedRevision;
            }
        }
    }

    private static final class Viewer {
        final AbstractContainerMenu menu;
        List<CompoundTag> entries = List.of();
        int revision = -1;
        Viewer(AbstractContainerMenu menu) { this.menu = menu; }
    }

    private CompoundTag encode(int index) {
        CompoundTag entry = host.getPatternPreviewEntry(index).encode(host.getLevel().registryAccess());
        entry.putInt("index", index);
        return entry;
    }

    private void send(ServerPlayer player, boolean full, int baseRevision, List<CompoundTag> entries) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("menu", player.containerMenu.containerId);
        payload.putInt("revision", cachedRevision);
        payload.putInt("base", baseRevision);
        payload.putInt("size", cachedEntries.size());
        payload.putBoolean("full", full);
        payload.putBoolean("first", true);
        payload.putBoolean("last", true);
        ListTag batch = new ListTag();
        batch.addAll(entries);
        payload.put("entries", batch);
        MenuDataTransport.send(player, MenuDataTransport.Channel.PATTERNS, buf -> {
            buf.writeBlockPos(host.getBlockPos());
            buf.writeNbt(payload);
        });
    }
}
