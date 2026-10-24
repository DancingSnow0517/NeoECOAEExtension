package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import java.util.function.Consumer;
import net.minecraft.world.level.Level;

/** Menu-scoped push synchronization. No server-side viewport or client polling. */
public final class PatternPreviewSync {
    private static final int SLOTS_PER_TICK = 256;
    private static final long BUILD_NANOS = 2_000_000L;
    private static final int CLIENT_CACHE_LIMIT = 32;
    private static final long SERVER_VIEWER_RETENTION_TICKS = 1_200L;
    // Level identity prevents another server/save at the same coordinates from reusing this world's view.
    private static final Map<Level, Map<ClientCacheKey, ClientState>> CLIENT_CACHE = new WeakHashMap<>();
    private long nextSyncTick;
    private final ECOMachineInterfaceBlockEntity<?> host;
    /** Changes when this server-side block entity is recreated, preventing stale snapshots after restart. */
    private final long sessionEpoch = ThreadLocalRandom.current().nextLong();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private final List<CompoundTag> cachedEntries = new ArrayList<>();
    private final BitSet dirtySlots = new BitSet();
    private boolean reset = true;
    private int cachedRevision = -1;
    private boolean building;
    private int buildCursor;
    private ListTag layout = new ListTag();
    private WeakReference<Consumer<CompoundTag>> receiver = new WeakReference<>(null);

    private record ClientCacheKey(String dimension, long position) {}
    private record ClientState(ClientSnapshot snapshot, ClientView view) {}

    public record ClientView(String inputSearch, String outputSearch, int scrollRow) {
        public static final ClientView EMPTY = new ClientView("", "", 0);
    }

    public record ClientSnapshot(long epoch, int revision, PatternPreviewEntry[] entries) {
        public ClientSnapshot {
            entries = entries == null ? new PatternPreviewEntry[0] : entries.clone();
        }
        @Override
        public PatternPreviewEntry[] entries() {
            return entries.clone();
        }
    }

    public PatternPreviewSync(ECOMachineInterfaceBlockEntity<?> host) {
        this.host = host;
    }

    public void listen(Consumer<CompoundTag> receiver) {
        this.receiver = new WeakReference<>(receiver);
    }

    /** Restores the last completed client snapshot for this host, if one exists. */
    public ClientSnapshot clientSnapshot() {
        if (host.getLevel() == null || !host.getLevel().isClientSide) {
            return null;
        }
        synchronized (CLIENT_CACHE) {
            ClientState state = clientCache().get(clientCacheKey());
            return state == null ? null : state.snapshot();
        }
    }

    public ClientView clientView() {
        if (host.getLevel() == null || !host.getLevel().isClientSide) return ClientView.EMPTY;
        synchronized (CLIENT_CACHE) {
            ClientState state = clientCache().get(clientCacheKey());
            return state == null ? ClientView.EMPTY : state.view();
        }
    }

    public void cacheClientView(String inputs, String output, int scrollRow) {
        if (host.getLevel() == null || !host.getLevel().isClientSide) return;
        synchronized (CLIENT_CACHE) {
            var cache = clientCache();
            ClientState previous = cache.get(clientCacheKey());
            cache.put(clientCacheKey(), new ClientState(previous == null ? null : previous.snapshot(),
                    new ClientView(inputs, output, scrollRow)));
        }
    }

    /** Announces a newly opened menu and the client's cached revision to the server. */
    public void requestOpen(int menuId, int cachedRevision, long cachedEpoch) {
        if (host.getLevel() == null || !host.getLevel().isClientSide) {
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putInt("menu", menuId);
        payload.putInt("revision", cachedRevision);
        payload.putLong("epoch", cachedEpoch);
        host.rpcToServer("openPatternPreview", payload);
    }

    /** Stores a completed client snapshot for reuse by the next menu instance. */
    public void cacheClientSnapshot(long epoch, int revision, PatternPreviewEntry[] entries) {
        if (host.getLevel() == null || !host.getLevel().isClientSide || entries == null) {
            return;
        }
        synchronized (CLIENT_CACHE) {
            var cache = clientCache();
            ClientState previous = cache.get(clientCacheKey());
            cache.put(clientCacheKey(), new ClientState(new ClientSnapshot(epoch, revision, entries),
                    previous == null ? ClientView.EMPTY : previous.view()));
        }
    }

    private Map<ClientCacheKey, ClientState> clientCache() {
        return CLIENT_CACHE.computeIfAbsent(host.getLevel(), ignored -> newClientCache());
    }

    private static Map<ClientCacheKey, ClientState> newClientCache() {
        return new LinkedHashMap<>(CLIENT_CACHE_LIMIT, 0.75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<ClientCacheKey, ClientState> eldest) {
                return size() > CLIENT_CACHE_LIMIT;
            }
        };
    }

    private ClientCacheKey clientCacheKey() {
        return new ClientCacheKey(host.getLevel().dimension().location().toString(),
                host.getBlockPos().asLong());
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

    /** Handles the client-side cache probe for a newly opened menu. */
    public void open(ServerPlayer player, int menuId, int clientRevision, long clientEpoch) {
        if (!isViewer(player) || player.containerMenu.containerId != menuId) {
            return;
        }
        Viewer viewer = viewers.computeIfAbsent(player.getUUID(), ignored -> new Viewer(player.getUUID(), player.containerMenu));
        viewer.menu = player.containerMenu;
        viewer.lastSeenTick = host.getLevel() instanceof ServerLevel level ? level.getGameTime() : 0L;
        viewer.openRequested = true;
        viewer.clientRevision = clientRevision;
        viewer.clientEpoch = clientEpoch;
        viewer.pending = null;
        viewer.changed.clear();
        MenuDataTransport.cancel(player, MenuDataTransport.Channel.PATTERNS);
    }

    public void dirty(int firstSlot, int count) {
        if (firstSlot >= 0 && count > 0) dirtySlots.set(firstSlot, Math.addExact(firstSlot, count));
    }

    public boolean isViewer(ServerPlayer player) {
        return player.containerMenu instanceof ModularUIContainer menu
                && menu.getModularUI().holder instanceof BlockUIMenuType.BlockUIHolder holder
                && holder.pos.equals(host.getBlockPos())
                && player.level() == host.getLevel()
                && player.blockPosition().distSqr(host.getBlockPos()) <= 64.0D;
    }

    public void tick(ServerLevel level) {
        List<ServerPlayer> active = level.players().stream().filter(this::isViewer).toList();
        for (Viewer viewer : viewers.values()) {
            if (active.stream().noneMatch(player -> player.getUUID().equals(viewer.playerId))) {
                viewer.menu = null;
            }
        }
        viewers.entrySet().removeIf(entry -> level.getGameTime() - entry.getValue().lastSeenTick
                > SERVER_VIEWER_RETENTION_TICKS);
        if (active.isEmpty()) return;
        // Drain immutable pages every tick; refresh the live catalogue at the normal cadence.
        boolean hasPendingOpen = viewers.values().stream().anyMatch(viewer -> viewer.openRequested);
        if (level.getGameTime() < nextSyncTick && !building && !hasPendingOpen) {
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
            layout = host.getPatternPreviewLayout();
            dirtySlots.or(host.getOccupiedPatternPreviewSlots());
            buildCursor = 0;
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
            // Capacity is described by the provider layout. Empty slots have no content record.
            if (entry.getCompound("stack").isEmpty() && !entry.getBoolean("diskSlot")
                    && entry.getList("disk", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty()
                    && entry.getString("inputs").isEmpty() && entry.getString("output").isEmpty()) entry = null;
            if (!java.util.Objects.equals(entry, cachedEntries.get(index))) {
                cachedEntries.set(index, entry);
                for (Viewer viewer : viewers.values()) viewer.changed.set(index);
            }
            dirtySlots.clear(index);
            buildCursor = index + 1;
            visited++;
            // Like AE2's terminal, the initial snapshot reads the already indexed occupied slots directly.
            // Only subsequent dirty work is spread over ticks; unused capacity never delays opening.
            if (!full && (--budget == 0 || (budget <= SLOTS_PER_TICK - 16 && System.nanoTime() >= deadline))) break;
        }
        building = !dirtySlots.isEmpty();
        // Publish completed slices even under continuous automation. Remaining dirty slots follow in the
        // next delta; clicks validate physical contents rather than treating this revision as an inventory lock.
        cachedRevision = revision;
        for (ServerPlayer player : active) {
            Viewer viewer = viewers.get(player.getUUID());
            if (viewer == null) {
                MenuDataTransport.cancel(player, MenuDataTransport.Channel.PATTERNS);
                viewer = new Viewer(player.getUUID(), player.containerMenu);
                viewers.put(player.getUUID(), viewer);
            } else if (viewer.menu != player.containerMenu) {
                viewer.menu = player.containerMenu;
                viewer.pending = null;
                viewer.changed.clear();
                // A menu changed without the cache handshake (for example an older client or a
                // race during screen construction). Fall back to a safe full baseline.
                viewer.revision = -1;
            }
            viewer.lastSeenTick = level.getGameTime();
            if (viewer.openRequested) {
                viewer.openRequested = false;
                if (viewer.clientEpoch == sessionEpoch && viewer.clientRevision == cachedRevision
                        && viewer.clientRevision >= 0
                        && cachedEntries.size() == size) {
                    sendReuse(player, viewer, cachedRevision, size);
                    continue;
                }
                viewer.revision = -1;
            }
            // Finish the immutable snapshot before diffing against it. This avoids starvation while
            // automation keeps changing the live catalogue during a multi-tick initial transfer.
            if (MenuDataTransport.busy(player, MenuDataTransport.Channel.PATTERNS)) continue;
            if (viewer.pending == null && (viewer.revision != cachedRevision || !viewer.changed.isEmpty())) {
                boolean sendFull = viewer.revision < 0;
                List<CompoundTag> changes = new ArrayList<>();
                if (sendFull) {
                    viewer.disks.clear();
                    for (int index = 0; index < size; index++) {
                        CompoundTag entry = cachedEntries.get(index);
                        if (entry != null) {
                            changes.add(entry);
                            rememberDisk(viewer, index, entry);
                        }
                    }
                }
                else for (int index = viewer.changed.nextSetBit(0); index >= 0;
                          index = viewer.changed.nextSetBit(index + 1)) {
                    CompoundTag entry = cachedEntries.get(index);
                    // A removed pattern must still send its physical address as a tombstone.
                    changes.add(diskDelta(viewer, index, entry == null ? encode(index) : entry));
                }
                viewer.pending = new Pending(sendFull, viewer.revision, cachedRevision, size, changes,
                        sendFull ? layout : null);
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
        // Queue the immutable pages together. Transport drains as many as fit in its byte budget,
        // instead of spending a whole game tick on each (possibly tiny) page.
        do {
            int end = Math.min(pending.entries.size(), pending.offset + SLOTS_PER_TICK);
            send(player, pending, end);
            pending.offset = end;
        } while (pending.offset < pending.entries.size());
        viewer.revision = pending.revision;
        viewer.pending = null;
    }

    private static final class Pending {
        final boolean full;
        final int base, revision, size;
        final List<CompoundTag> entries;
        final ListTag layout;
        int offset;
        Pending(boolean full, int base, int revision, int size, List<CompoundTag> entries, ListTag layout) {
            this.full = full;
            this.base = base;
            this.revision = revision;
            this.size = size;
            this.entries = entries;
            this.layout = layout;
        }
    }

    private static final class Viewer {
        final UUID playerId;
        AbstractContainerMenu menu;
        final BitSet changed = new BitSet();
        final Map<Integer, ListTag> disks = new HashMap<>();
        Pending pending;
        int revision = -1;
        int clientRevision = -1;
        long clientEpoch;
        boolean openRequested;
        long lastSeenTick;
        Viewer(UUID playerId, AbstractContainerMenu menu) {
            this.menu = menu;
            this.playerId = playerId;
        }
    }

    private void sendReuse(ServerPlayer player, Viewer viewer, int revision, int size) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("menu", player.containerMenu.containerId);
        payload.putInt("revision", revision);
        payload.putLong("epoch", sessionEpoch);
        payload.putInt("base", revision);
        payload.putInt("size", size);
        payload.putBoolean("full", false);
        payload.putBoolean("reuse", true);
        payload.putBoolean("first", true);
        payload.putBoolean("last", true);
        payload.put("entries", new ListTag());
        MenuDataTransport.sendQueued(player, MenuDataTransport.Channel.PATTERNS, buf -> {
            buf.writeBlockPos(host.getBlockPos());
            PatternPreviewCodec.write(buf, payload);
        });
        viewer.revision = revision;
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
        payload.putLong("epoch", sessionEpoch);
        payload.putInt("base", pending.base);
        payload.putInt("size", pending.size);
        payload.putBoolean("full", pending.full);
        payload.putBoolean("first", pending.offset == 0);
        payload.putBoolean("last", end == pending.entries.size());
        if (pending.full && pending.offset == 0) {
            payload.put("layout", pending.layout);
            payload.putIntArray("occupied", pending.entries.stream().mapToInt(entry -> entry.getInt("index")).toArray());
        }
        ListTag batch = new ListTag();
        batch.addAll(pending.entries.subList(pending.offset, end));
        payload.put("entries", batch);
        MenuDataTransport.sendQueued(player, MenuDataTransport.Channel.PATTERNS, buf -> {
            buf.writeBlockPos(host.getBlockPos());
            PatternPreviewCodec.write(buf, payload);
        });
    }
}
