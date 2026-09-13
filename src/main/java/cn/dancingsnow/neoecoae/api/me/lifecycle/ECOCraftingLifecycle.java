package cn.dancingsnow.neoecoae.api.me.lifecycle;

import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Process-wide registration point for ECO crafting lifecycle observers. */
public final class ECOCraftingLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.api");
    private static final CopyOnWriteArrayList<ECOCraftingLifecycleListener> LISTENERS = new CopyOnWriteArrayList<>();

    private ECOCraftingLifecycle() {
    }

    public static void register(ECOCraftingLifecycleListener listener) {
        if (listener == null) throw new NullPointerException("listener");
        LISTENERS.addIfAbsent(listener);
    }

    public static void unregister(ECOCraftingLifecycleListener listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    public static void addListener(ECOCraftingLifecycleListener listener) {
        register(listener);
    }

    public static void removeListener(ECOCraftingLifecycleListener listener) {
        unregister(listener);
    }

    @ApiStatus.Internal
    public static void fireJobStarted(ECOCraftingJobContext context) {
        for (var listener : LISTENERS) {
            try {
                listener.onJobStarted(context);
            } catch (RuntimeException failure) {
                LOGGER.warn("ECO crafting lifecycle listener failed during job start", failure);
            }
        }
    }

    @ApiStatus.Internal
    public static void firePatternDispatched(ECOCraftingDispatchEvent event) {
        for (var listener : LISTENERS) {
            try {
                listener.onPatternDispatched(event);
            } catch (RuntimeException failure) {
                LOGGER.warn("ECO crafting lifecycle listener failed during dispatch notification", failure);
            }
        }
    }

    @ApiStatus.Internal
    public static void fireJobFinished(ECOCraftingJobContext context, ECOCraftingJobResult result) {
        for (var listener : LISTENERS) {
            try {
                listener.onJobFinished(context, result);
            } catch (RuntimeException failure) {
                LOGGER.warn("ECO crafting lifecycle listener failed during job finish", failure);
            }
        }
    }
}
