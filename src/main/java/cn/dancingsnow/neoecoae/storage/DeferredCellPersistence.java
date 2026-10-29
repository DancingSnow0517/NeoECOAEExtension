package cn.dancingsnow.neoecoae.storage;

/**
 * Implemented by an {@link appeng.api.storage.cells.ISaveProvider} that lets {@link ECOStorageCell} postpone its
 * component write to the end of the server tick ({@link ECOCellMutationBatch#flushDeferred()}). The host must call
 * {@link ECOCellMutationBatch#flushDeferred(appeng.api.storage.cells.ISaveProvider)} before it reads the cell stack's
 * components, saves or syncs it, or lets the stack leave.
 */
public interface DeferredCellPersistence {
    /** Whether a mutation happening now, on this host's server thread, may be persisted at the end of the tick. */
    boolean neoecoae$canDeferCellPersistence();
}
