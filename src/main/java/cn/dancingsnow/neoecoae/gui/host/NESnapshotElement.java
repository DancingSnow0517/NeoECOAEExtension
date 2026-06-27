package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.Arrays;
import java.util.function.Supplier;

abstract class NESnapshotElement extends UIElement {
    private final Supplier<byte[]> serverSnapshot;
    private byte[] cachedSnapshot = NEHostSnapshots.EMPTY;

    protected NESnapshotElement(Supplier<byte[]> serverSnapshot) {
        this.serverSnapshot = serverSnapshot;
        var syncValue = DataBindingBuilder.<byte[]>create(this::cachedSnapshot, ignored -> {})
            .syncType(byte[].class)
            .c2sStrategy(SyncStrategy.NONE)
            .build()
            .getSyncValue();
        syncValue.addListener(this::acceptSnapshot);
        addSyncValue(syncValue);
    }

    protected abstract void acceptSnapshot(byte[] snapshotData);

    private byte[] cachedSnapshot() {
        byte[] next = serverSnapshot.get();
        if (next == null) {
            next = NEHostSnapshots.EMPTY;
        }
        if (!Arrays.equals(cachedSnapshot, next)) {
            cachedSnapshot = next;
        }
        return cachedSnapshot;
    }
}
