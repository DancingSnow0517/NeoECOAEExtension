package cn.dancingsnow.neoecoae.impl.storage;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.storage.DimensionDataStorage;

/** Coordinates NeoEco SavedData writes so one DimensionDataStorage is saved once per flush. */
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

    public static synchronized void flush(Backend backend) {
        if (backend != null && backend.needsPersistence()) {
            flushAll();
        }
    }

    public static synchronized void flushAll() {
        Map<DimensionDataStorage, List<Backend>> pendingByStorage = new IdentityHashMap<>();
        for (Backend backend : List.copyOf(BACKENDS)) {
            if (backend.needsPersistence()) {
                pendingByStorage
                        .computeIfAbsent(backend.dataStorage(), ignored -> new ArrayList<>())
                        .add(backend);
            }
        }

        for (Map.Entry<DimensionDataStorage, List<Backend>> entry : pendingByStorage.entrySet()) {
            List<Backend> pending = entry.getValue();
            try {
                for (Backend backend : pending) {
                    backend.preparePersistence();
                }
                entry.getKey().save();
            } catch (Exception e) {
                for (Backend backend : pending) {
                    backend.persistenceFailed(e);
                }
                continue;
            }
            for (Backend backend : pending) {
                try {
                    backend.verifyPersistence();
                } catch (Exception e) {
                    backend.persistenceFailed(e);
                }
            }
        }
    }

    public interface Backend {
        DimensionDataStorage dataStorage();

        boolean needsPersistence();

        void preparePersistence() throws Exception;

        void verifyPersistence() throws Exception;

        void persistenceFailed(Exception cause);
    }
}
