package cn.dancingsnow.neoecoae.gui.host;

import java.util.List;

record NEStorageSnapshotData(
    boolean formed,
    long storedEnergy,
    long maxEnergy,
    List<NEStorageTypeStat> typeStats,
    List<NEStorageMatrixCell> matrixCells
) {
    static final NEStorageSnapshotData EMPTY = new NEStorageSnapshotData(false, 0L, 0L, List.of(), List.of());

    static NEStorageSnapshotData decode(byte[] snapshotData) {
        Holder holder = new Holder();
        NEHostSnapshots.decode(snapshotData, buf -> holder.value = new NEStorageSnapshotData(
            buf.readBoolean(),
            Math.max(0L, buf.readVarLong()),
            Math.max(0L, buf.readVarLong()),
            NEHostSnapshots.readTypeStats(buf),
            NEHostSnapshots.readMatrixCells(buf)
        ));
        return holder.value;
    }

    private static final class Holder {
        private NEStorageSnapshotData value = EMPTY;
    }
}
