package cn.dancingsnow.neoecoae.storage.persistence;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtOps;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/** Remembers already-published domains so deleting their snapshot cannot create a fresh empty domain. */
public final class StorageCatalogSavedData extends StorageSavedData {
    private final Set<UUID> domains = new HashSet<>();
    private boolean readable = true;
    private final Map<UUID, GlobalPos> locations = new HashMap<>();
    private final Map<GlobalPos, UUID> hosts = new HashMap<>();
    // Keep the token even when unbound. Moving a host replaces its token and position in one snapshot.
    private final Map<UUID, UUID> claimTokens = new HashMap<>();
    public boolean readable() { return readable; }
    public boolean contains(UUID domain) { return domains.contains(domain); }
    public boolean publish(UUID domain) {
        if (!readable) return false;
        if (domains.add(domain)) setDirty();
        return flush();
    }
    public boolean ready() { return readable && writeFailure() == null && !isDirty(); }
    public UUID domainAt(GlobalPos location) { return hosts.get(location); }
    public GlobalPos locationOf(UUID domain) { return locations.get(domain); }

    public UUID claimToken(UUID domain) { return claimTokens.get(domain); }

    public boolean bindHost(UUID domain, GlobalPos location, boolean rebind) {
        if (!readable || !domains.contains(domain) || !flush()) return false;
        GlobalPos previous = locations.get(domain);
        UUID displaced = hosts.get(location);
        if (!rebind && (previous != null && !previous.equals(location)
                || displaced != null && !displaced.equals(domain))) return false;
        if (!rebind && location.equals(previous) && claimTokens.containsKey(domain)) return true;
        if (previous != null) hosts.remove(previous);
        if (displaced != null) locations.remove(displaced);
        claimTokens.put(domain, UUID.randomUUID());
        locations.put(domain, location);
        hosts.put(location, domain);
        setDirty();
        return flush();
    }

    /** Release only this position; the published domain and its inventory remain in the save. */
    public boolean unbindHost(UUID domain, GlobalPos location) {
        if (!ready() || !location.equals(locations.get(domain)) || !domain.equals(hosts.get(location))) return false;
        locations.remove(domain);
        hosts.remove(location);
        setDirty();
        return flush();
    }

    @Override protected boolean canSaveSnapshot() { return readable; }
    @Override public CompoundTag save(CompoundTag tag) {
        if (!readable) throw new IllegalStateException("Unreadable ECO storage catalog");
        tag.putInt("format", 3);
        ListTag ids = new ListTag(); domains.forEach(id -> ids.add(NbtUtils.createUUID(id)));
        tag.put("domains", ids);
        CompoundTag associations = new CompoundTag();
        locations.forEach((id, location) -> associations.put(id.toString(),
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, location).getOrThrow(false, message -> { throw new IllegalStateException(message); })));
        tag.put("hostAssociations", associations);
        CompoundTag tokens = new CompoundTag();
        claimTokens.forEach((id, token) -> tokens.putUUID(id.toString(), token));
        tag.put("claimTokens", tokens);
        return tag;
    }
    public static StorageCatalogSavedData load(CompoundTag tag) {
        var data = new StorageCatalogSavedData(); data.loadPersistence(tag);
        int format = tag.getInt("format");
        if ((format < 1 || format > 3) || !tag.contains("domains", Tag.TAG_LIST)) { data.readable = false; return data; }
        ListTag ids = (ListTag) tag.get("domains");
        if (!ids.isEmpty() && ids.getElementType() != Tag.TAG_INT_ARRAY) { data.readable = false; return data; }
        try { for (Tag id : tag.getList("domains", Tag.TAG_INT_ARRAY)) data.domains.add(NbtUtils.loadUUID(id)); }
        catch (RuntimeException invalid) { data.readable = false; }
        if (format >= 2 || tag.contains("hostAssociations")) {
            if (!tag.contains("hostAssociations", Tag.TAG_COMPOUND)) { data.readable = false; return data; }
            CompoundTag associations = tag.getCompound("hostAssociations");
            try {
                for (String key : associations.getAllKeys()) {
                    UUID domain = UUID.fromString(key);
                    GlobalPos location = GlobalPos.CODEC.parse(NbtOps.INSTANCE, associations.get(key))
                        .getOrThrow(false, message -> { throw new IllegalStateException(message); });
                    if (!data.domains.contains(domain) || data.hosts.putIfAbsent(location, domain) != null)
                        throw new IllegalStateException("Invalid infinite domain host association");
                    if (data.locations.putIfAbsent(domain, location) != null) throw new IllegalStateException("Duplicate domain association");
                }
            } catch (RuntimeException invalid) { data.readable = false; }
        }
        if (format >= 3) {
            if (!tag.contains("claimTokens", Tag.TAG_COMPOUND)) { data.readable = false; return data; }
            var tokens = tag.getCompound("claimTokens");
            try {
                for (String key : tokens.getAllKeys()) {
                    UUID id = UUID.fromString(key);
                    if (!data.domains.contains(id) || !tokens.hasUUID(key) || data.claimTokens.putIfAbsent(id, tokens.getUUID(key)) != null)
                        throw new IllegalStateException("Invalid infinite domain claim token");
                }
            } catch (RuntimeException invalid) { data.readable = false; }
        }
        return data;
    }
    public static StorageCatalogSavedData open(ECOStorageRepository.Session session) {
        String key = "neoecoae_storage_catalog";
        var path = session.directory().resolve(key + ".dat");
        var data = session.storage().get(StorageCatalogSavedData::load, key);
        if (data == null) {
            data = new StorageCatalogSavedData();
            if (Files.exists(path) || Files.exists(path.resolveSibling(path.getFileName() + ".bak"))) data.readable = false;
            session.storage().set(key, data);
        }
        data.bind(path); session.track(data); return data;
    }
}
