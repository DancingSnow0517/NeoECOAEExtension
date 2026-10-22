package cn.dancingsnow.neoecoae.impl.storage.persistence;

import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.platform.nbt.NECellNbt;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;

/** One repository per server; native DimensionDataStorage owns the actual SavedData lifetime. */
public final class ECOStorageRepository {
    private static final Map<MinecraftServer, Session> SESSIONS = new IdentityHashMap<>();
    private ECOStorageRepository() {}

    public record Access(ECOCellSavedData data, Session session, ISaveProvider owner, boolean claimed) {
        public boolean writable() { return session != null && !session.closed && claimed && data.canMutate()
            && (owner == null || session.owners.get(data.id()) == owner); }
    }
    public static Session session(MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, s -> {
            Session session = new Session(s.overworld().getDataStorage(), s.getWorldPath(LevelResource.ROOT).resolve("data"));
            session.server = s;
            return session;
        });
    }
    public static Access open(ItemStack stack, ISaveProvider owner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            UUID id = ECOCellReference.id(stack);
            ECOCellSavedData data = new ECOCellSavedData(id == null ? new UUID(0, 0) : id);
            if (id == null) data.importLegacy(NECellNbt.readStacks(stack));
            else {
                var preview = NEItemData.read(stack).getCompound(ECOCellReference.TAG).getList("preview", 10);
                for (var raw : preview) {
                    var value = appeng.api.stacks.GenericStack.readTag((net.minecraft.nbt.CompoundTag) raw);
                    if (value != null && value.amount() > 0) data.amounts().add(value.what(), value.amount());
                }
            }
            String failure = NEItemData.read(stack).getCompound(ECOCellReference.TAG).getString("failure");
            if (!failure.isEmpty()) data.unavailable(failure);
            return new Access(data, null, owner, false);
        }
        return session(server).open(stack, owner);
    }
    public static void release(ItemStack stack, ISaveProvider owner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) session(server).release(stack, owner);
    }
    public static void persistMetadata(ItemStack stack, boolean flush) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        UUID id = ECOCellReference.id(stack);
        if (server == null || !server.isSameThread() || id == null) return;
        ECOCellSavedData data = session(server).cells.get(id);
        if (data == null || !data.canMutate()) throw new IllegalStateException("Unavailable ECO disk metadata");
        data.metadata(ECOCellReference.metadata(stack));
        if (flush && !data.flush()) throw new IllegalStateException("Cannot persist ECO disk state: " + data.failure());
    }
    public static void register(MinecraftServer server, StorageSavedData data) { session(server).track(data); }
    public static void tick(MinecraftServer server, long tick) { Session s = SESSIONS.get(server); if (s != null) s.tick(tick); }
    public static void flush(MinecraftServer server) { Session s = SESSIONS.get(server); if (s != null) s.flush(); }
    public static void stopped(MinecraftServer server) {
        Session session = SESSIONS.remove(server);
        if (session != null) { session.closed = true; session.owners.clear(); session.notifications.clear(); session.dirty.clear(); }
    }

    public static final class Session {
        private final DimensionDataStorage storage;
        private MinecraftServer server;
        private final Path directory;
        private final Map<UUID, ECOCellSavedData> cells = new HashMap<>();
        private final Map<UUID, ISaveProvider> owners = new HashMap<>();
        private final Map<ItemStack, Runnable> notifications = new IdentityHashMap<>();
        private final java.util.LinkedHashMap<StorageSavedData, Long> dirty = new java.util.LinkedHashMap<>();
        private long currentTick;
        private boolean closed;
        public Session(DimensionDataStorage storage, Path directory) { this.storage = storage; this.directory = directory; }
        public Path directory() { return directory; }
        public MinecraftServer server() { return server; }
        public DimensionDataStorage storage() { return storage; }
        public void track(StorageSavedData data) {
            data.dirtyListener(() -> dirty.putIfAbsent(data, currentTick));
            if (data.isDirty()) dirty.putIfAbsent(data, currentTick);
        }
        public Access open(ItemStack stack, ISaveProvider owner) {
            var root = NEItemData.read(stack);
            var ref = root.getCompound(ECOCellReference.TAG);
            if (root.contains(ECOCellReference.TAG) && (ref.getInt("format") != 1 || !ref.hasUUID("id"))) {
                var invalid = new ECOCellSavedData(new UUID(0, 0));
                invalid.unavailable("Invalid ECO disk reference; item data retained");
                return new Access(invalid, this, owner, false);
            }
            UUID id = ECOCellReference.id(stack);
            boolean fresh = id == null;
            if (fresh) id = UUID.randomUUID();
            final UUID identity = id;
            ECOCellSavedData data = cells.get(id);
            if (data == null) {
                String key = "neoecoae_cells/" + id;
                Path file = directory.resolve(key + ".dat");
                data = storage.get(tag -> ECOCellSavedData.load(tag, identity), key);
                if (data == null) {
                    data = new ECOCellSavedData(id);
                    if (!fresh || Files.exists(file) || Files.exists(file.resolveSibling(file.getFileName() + ".bak"))) {
                        data.unavailable("ECO disk SavedData missing or unreadable; identity retained: " + id);
                    } else {
                        data.importLegacy(NECellNbt.readStacks(stack));
                        data.metadata(ECOCellReference.metadata(stack));
                        data.legacyMigration(NEItemData.read(stack).hasUUID("neoecoae_migration_id"));
                    }
                    storage.set(key, data);
                }
                data.bind(file); track(data); cells.put(id, data);
                if (fresh && (!data.canMutate() || !data.flush())) {
                    throw new IllegalStateException("Cannot import ECO disk; original item contents retained: " + data.failure());
                }
                if (!fresh && data.canMutate()) StorageTransferCoordinator.recover(this, data);
            }
            if (data.canRead()) ECOCellReference.applyMetadata(stack, data.metadata());
            ISaveProvider existing = owners.get(id);
            if (existing instanceof BlockEntity block && block.isRemoved()) { owners.remove(id); existing = null; }
            boolean claimed = owner == null || existing == null || existing == owner;
            if (owner != null && claimed) owners.put(id, owner);
            ECOCellReference.update(stack, data);
            if (!claimed) NEItemData.update(stack, tag -> tag.getCompound(ECOCellReference.TAG)
                .putString("failure", "ECO disk UUID is already mounted by another owner"));
            return new Access(data, this, owner, claimed);
        }
        public void release(ItemStack stack, ISaveProvider owner) {
            UUID id = ECOCellReference.id(stack);
            if (id == null || owners.get(id) != owner) return;
            ECOCellSavedData data = cells.get(id);
            if (data != null) {
                if (!data.flush()) org.slf4j.LoggerFactory.getLogger(ECOStorageRepository.class)
                    .error("ECO disk {} was unmounted with a failed checkpoint; SavedData retained for retry: {}", id, data.failure());
                ECOCellReference.update(stack, data);
            }
            owners.remove(id);
        }
        public void schedule(ItemStack stack, Runnable notify) { notifications.put(stack, notify); }
        public void tick(long tick) {
            if (!notifications.isEmpty()) {
                var pending = new java.util.ArrayList<>(notifications.values()); notifications.clear();
                for (Runnable notify : pending) notify.run();
            }
            if (tick < 0) return;
            currentTick = tick;
            if (dirty.isEmpty()) return;
            long deadline = System.nanoTime() + 2_000_000;
            int remaining = Math.min(16, dirty.size());
            while (remaining-- > 0 && !dirty.isEmpty() && System.nanoTime() < deadline) {
                var entry = dirty.entrySet().iterator().next();
                StorageSavedData data = entry.getKey();
                if (data.isDirty() && tick - entry.getValue() < 100) break;
                dirty.remove(data);
                if (data.isDirty() && !data.flush()) dirty.put(data, tick);
            }
        }
        public void flush() {
            tick(-1);
            for (StorageSavedData data : new java.util.ArrayList<>(dirty.keySet())) {
                if (data.flush()) dirty.remove(data);
            }
            if (!dirty.isEmpty()) throw new IllegalStateException("ECO storage still has " + dirty.size() + " unsaved records");
        }
    }
}
