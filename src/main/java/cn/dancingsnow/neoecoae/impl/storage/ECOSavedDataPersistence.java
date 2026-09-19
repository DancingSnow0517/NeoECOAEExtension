package cn.dancingsnow.neoecoae.impl.storage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.storage.DimensionDataStorage;

/** Explicit commits are local to one backend; world autosave still invokes each SavedData directly. */
public final class ECOSavedDataPersistence {
    private static final List<Backend> BACKENDS = new ArrayList<>();

    private ECOSavedDataPersistence() {}

    public static synchronized void register(Backend backend) {
        if (!BACKENDS.contains(backend)) {
            BACKENDS.add(backend);
        }
    }

    public static synchronized void unregister(Backend backend) {
        BACKENDS.remove(backend);
    }

    public static synchronized void clear() {
        BACKENDS.clear();
    }

    public static void flush(Backend backend) {
        if (backend != null && backend.needsPersistence()) {
            try {
                backend.preparePersistence();
                backend.commitPersistence();
            } catch (Exception e) {
                backend.persistenceFailed(e);
            }
        }
    }

    public static void flushAll() {
        List<Backend> backends;
        synchronized (ECOSavedDataPersistence.class) {
            backends = List.copyOf(BACKENDS);
        }
        for (Backend backend : backends) {
            flush(backend);
        }
    }

    public interface Backend {
        DimensionDataStorage dataStorage();

        boolean needsPersistence();

        void preparePersistence() throws Exception;

        void commitPersistence() throws Exception;

        void verifyPersistence() throws Exception;

        void persistenceFailed(Exception cause);
    }
}
