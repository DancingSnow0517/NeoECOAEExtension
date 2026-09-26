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
    private static final int SLOTS_PER_TICK = 256;
    private static final long BUILD_NANOS = 2_000_000L;
    private long nextSyncTick;
    private final ECOMachineInterfaceBlockEntity<?> host;
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private final List<CompoundTag> cachedEntries = new ArrayList<>();
    private final BitSet dirtySlots = new BitSet();
    private boolean reset = true;
    private int cachedRevision = -1;
    private boolean building;
    private int buildCursor;
    private int uncached;
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
        if (firstSlot >= 0 && count > 0) dirtySlots.set(firstSlot, Math.addExact(firstSlot, count));
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
        // Drain immutable pages every tick; refresh the live catalogue at the normal cadence.
        if (level.getGameTime() < nextSyncTick && !building) {
            for (ServerPlayer player : active) drain(player, viewers.get(player.getUUID()));
            return;
        }
        nextSyncTick = level.getGameTime() + MenuDataTransport.UPDATE_INTERVAL;
        if (reset || !dirtySlots.isEmpty()) host.refreshPatternCatalog();
        int size = host.getPatternInterfaceSlotCount();
        int revision = host.getPatternContentRevision();
        boolean full = reset || cachedEntries.size() != size;

        if (full) {
            cachedEntries.clear();
            for (int index = 0; index < size; index++) cachedEntries.add(null);
            dirtySlots.clear();
            dirtySlots.set(0, size);
            buildCursor = 0;
            uncached = size;
            building = true;
            for (Viewer viewer : viewers.values()) {
                viewer.revision = -1;
                viewer.pending = null;
                viewer.changed.clear();
            }
            for (ServerPlayer player : active) MenuDataTransport.cancel(player, MenuDataTransport.Channel.PATTERNS);
        }
        reset = false;
        long deadline = System.nanoTime() + BUILD_NANOS;
        int budget = SLOTS_PER_TICK;
        for (int visited = 0; visited < size && !dirtySlots.isEmpty();) {
            int index = dirtySlots.nextSetBit(buildCursor);
            if (index < 0 || index >= size) index = dirtySlots.nextSetBit(0);
            if (index < 0 || index >= size) break;
            CompoundTag entry = encode(index);
            if (cachedEntries.get(index) == null) uncached--;
            if (!entry.equals(cachedEntries.get(index))) {
                cachedEntries.set(index, entry);
                for (Viewer viewer : viewers.values()) viewer.changed.set(index);
            }
            dirtySlots.clear(index);
            buildCursor = index + 1;
            visited++;
            if (--budget == 0 || (budget <= SLOTS_PER_TICK - 16 && System.nanoTime() >= deadline)) break;
        }
        building = !dirtySlots.isEmpty();
        if (uncached > 0) {
            for (ServerPlayer player : active) drain(player, viewers.get(player.getUUID()));
            return;
        }
        // Publish completed slices even under continuous automation. Remaining dirty slots follow in the
        // next delta; clicks validate physical contents rather than treating this revision as an inventory lock.
        cachedRevision = revision;
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
            if (viewer.pending == null && (viewer.revision != cachedRevision || !viewer.changed.isEmpty())) {
                boolean sendFull = viewer.revision < 0;
                List<CompoundTag> changes = new ArrayList<>();
                if (sendFull) {
                    changes.addAll(cachedEntries);
                    viewer.disks.clear();
                    for (int index = 0; index < size; index++) rememberDisk(viewer, index, cachedEntries.get(index));
                }
                else for (int index = viewer.changed.nextSetBit(0); index >= 0;
                          index = viewer.changed.nextSetBit(index + 1)) changes.add(diskDelta(viewer, index, cachedEntries.get(index)));
                viewer.pending = new Pending(sendFull, viewer.revision, cachedRevision, size, List.copyOf(changes));
                viewer.changed.clear();
            }
            drain(player, viewer);
        }
    }

    private static void rememberDisk(Viewer viewer, int index, CompoundTag entry) {
        if (entry.getBoolean("diskSlot")) viewer.disks.put(index, entry.getList("disk", net.minecraft.nbt.Tag.TAG_COMPOUND));
        else viewer.disks.remove(index);
    }

    private static CompoundTag diskDelta(Viewer viewer, int index, CompoundTag entry) {
        ListTag before = viewer.disks.get(index);
        rememberDisk(viewer, index, entry);
        if (before == null || !entry.getBoolean("diskSlot")) return entry;
        ListTag after = entry.getList("disk", net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag delta = entry.copy();
        delta.remove("disk");
        delta.putInt("diskSize", after.size());
        ListTag changes = new ListTag();
        for (int recipe = 0; recipe < after.size(); recipe++) {
            if (recipe >= before.size() || !after.get(recipe).equals(before.get(recipe))) {
                CompoundTag changed = after.getCompound(recipe).copy();
                changed.putInt("recipe", recipe);
                changes.add(changed);
            }
        }
        delta.put("diskChanges", changes);
        return delta;
    }

    private void drain(ServerPlayer player, Viewer viewer) {
        if (viewer == null || viewer.menu != player.containerMenu || viewer.pending == null
                || MenuDataTransport.busy(player, MenuDataTransport.Channel.PATTERNS)) return;
        Pending pending = viewer.pending;
        int end = Math.min(pending.entries.size(), pending.offset + SLOTS_PER_TICK);
        send(player, pending, end);
        pending.offset = end;
        if (end == pending.entries.size()) {
            viewer.revision = pending.revision;
            viewer.pending = null;
        }
    }

    private static final class Pending {
        final boolean full;
        final int base, revision, size;
        final List<CompoundTag> entries;
        int offset;
        Pending(boolean full, int base, int revision, int size, List<CompoundTag> entries) {
            this.full = full;
            this.base = base;
            this.revision = revision;
            this.size = size;
            this.entries = entries;
        }
    }

    private static final class Viewer {
        final AbstractContainerMenu menu;
        final BitSet changed = new BitSet();
        final Map<Integer, ListTag> disks = new HashMap<>();
        Pending pending;
        int revision = -1;
        Viewer(AbstractContainerMenu menu) { this.menu = menu; }
    }

    private CompoundTag encode(int index) {
        CompoundTag entry = host.getPatternPreviewEntry(index).encode(host.getLevel().registryAccess());
        entry.putInt("index", index);
        return entry;
    }

    private void send(ServerPlayer player, Pending pending, int end) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("menu", player.containerMenu.containerId);
        payload.putInt("revision", pending.revision);
        payload.putInt("base", pending.base);
        payload.putInt("size", pending.size);
        payload.putBoolean("full", pending.full);
        payload.putBoolean("first", pending.offset == 0);
        payload.putBoolean("last", end == pending.entries.size());
        ListTag batch = new ListTag();
        batch.addAll(pending.entries.subList(pending.offset, end));
        payload.put("entries", batch);
        MenuDataTransport.send(player, MenuDataTransport.Channel.PATTERNS, buf -> {
            buf.writeBlockPos(host.getBlockPos());
            PatternPreviewCodec.write(buf, payload);
        });
    }
}
