package cn.dancingsnow.neoecoae.api.storage;

/**
 * Stable integration point for reading and changing an ECO storage host's configured priority.
 * No AE2 submenu or ECO block entity implementation is required by this contract.
 */
public interface IECOStoragePriorityHost {
    /** Returns the configured priority, before per-cell mount priority adjustments. */
    int getStoragePriority();

    /**
     * Sets the configured priority to any signed {@code int} value, persisting and synchronizing it
     * and refreshing the affected AE2 storage mounts when it changes.
     *
     * <p>Call on the owning server thread after checking player/network permissions in the caller's
     * terminal or packet handler. This method does not authenticate a player. ECO hosts ignore calls
     * on the client or before they have a level.</p>
     *
     * @param priority the new configured storage priority
     */
    void setStoragePriority(int priority);
}
