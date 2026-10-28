package cn.dancingsnow.neoecoae.storage.persistence;

import java.nio.file.Files;
import java.util.HashSet;
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
    public boolean readable() { return readable; }
    public boolean contains(UUID domain) { return domains.contains(domain); }
    public boolean publish(UUID domain) {
        if (!readable) return false;
        if (domains.add(domain)) setDirty();
        return flush();
    }
    @Override protected boolean canSaveSnapshot() { return readable; }
    @Override public CompoundTag save(CompoundTag tag) {
        if (!readable) throw new IllegalStateException("Unreadable ECO storage catalog");
        tag.putInt("format", 1);
        ListTag ids = new ListTag(); domains.forEach(id -> ids.add(NbtUtils.createUUID(id)));
        tag.put("domains", ids); return tag;
    }
    public static StorageCatalogSavedData load(CompoundTag tag) {
        var data = new StorageCatalogSavedData(); data.loadPersistence(tag);
        if (tag.getInt("format") != 1 || !tag.contains("domains", Tag.TAG_LIST)) { data.readable = false; return data; }
        ListTag ids = (ListTag) tag.get("domains");
        if (!ids.isEmpty() && ids.getElementType() != Tag.TAG_INT_ARRAY) { data.readable = false; return data; }
        try { for (Tag id : tag.getList("domains", Tag.TAG_INT_ARRAY)) data.domains.add(NbtUtils.loadUUID(id)); }
        catch (RuntimeException invalid) { data.readable = false; }
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
