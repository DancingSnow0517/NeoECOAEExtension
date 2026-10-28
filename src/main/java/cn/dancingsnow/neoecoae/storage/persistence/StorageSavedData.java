package cn.dancingsnow.neoecoae.storage.persistence;

import cn.dancingsnow.neoecoae.platform.nbt.NEAtomicNbtFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.LoggerFactory;

/** One native SavedData record with separate accepted and durable revisions. Server-thread owned. */
public abstract class StorageSavedData extends SavedData {
    private Path dataFile;
    private long contentRevision;
    private long durableRevision = -1;
    private long savedSnapshots;
    private long lastSaveNanos;
    private long maximumSaveNanos;
    private String writeFailure;
    private Runnable dirtyListener = () -> {};

    public final void dirtyListener(Runnable listener) { dirtyListener = listener; }

    public final void bind(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        if (dataFile != null && !dataFile.equals(absolute)) throw new IllegalStateException("Storage file identity changed");
        dataFile = absolute;
    }
    public final Path dataFile() { return dataFile; }
    public final long contentRevision() { return contentRevision; }
    public final long durableRevision() { return durableRevision; }
    public final String writeFailure() { return writeFailure; }
    public final long savedSnapshots() { return savedSnapshots; }
    public final long lastSaveNanos() { return lastSaveNanos; }
    public final long maximumSaveNanos() { return maximumSaveNanos; }
    @Override public void setDirty(boolean dirty) {
        if (dirty) contentRevision++;
        super.setDirty(dirty);
        if (dirty) dirtyListener.run();
    }
    protected final void loadPersistence(CompoundTag tag) {
        contentRevision = Math.max(0, tag.getLong("storageRevision"));
        durableRevision = contentRevision;
    }
    protected boolean canSaveSnapshot() { return true; }
    protected void snapshotSaved() throws IOException {}
    public final CompoundTag snapshot() {
        CompoundTag data = save(new CompoundTag());
        data.putLong("storageRevision", contentRevision);
        return data;
    }
    @Override public final void save(File file) {
        if (!canSaveSnapshot() || (!isDirty() && writeFailure == null)) return;
        long revision = contentRevision, started = System.nanoTime();
        try {
            Path target = file.toPath().toAbsolutePath().normalize();
            if (dataFile != null && !dataFile.equals(target)) throw new IllegalStateException("Storage file identity changed");
            CompoundTag root = new CompoundTag();
            root.put("data", snapshot());
            NbtUtils.addCurrentDataVersion(root);
            NEAtomicNbtFile.write(root, target);
            bind(target);
            durableRevision = revision;
            snapshotSaved();
            writeFailure = null;
            if (revision == contentRevision) super.setDirty(false);
            savedSnapshots++;
        } catch (IOException | RuntimeException failure) {
            if (writeFailure == null) LoggerFactory.getLogger(getClass())
                .error("Cannot save ECO inventory {}; dirty contents retained", file, failure);
            writeFailure = failure.toString();
            super.setDirty(true);
        } finally {
            lastSaveNanos = System.nanoTime() - started;
            maximumSaveNanos = Math.max(maximumSaveNanos, lastSaveNanos);
        }
    }
    public final boolean flushThrough(long revision) {
        if (!canSaveSnapshot() || dataFile == null) return false;
        if (durableRevision < revision) super.setDirty(true);
        if (durableRevision < revision || isDirty() || writeFailure != null) save(dataFile.toFile());
        return writeFailure == null && durableRevision >= revision && !isDirty();
    }
    public final boolean flush() { return flushThrough(contentRevision); }
}
