package cn.dancingsnow.neoecoae.grid.storage.supply;

import cn.dancingsnow.neoecoae.grid.storage.cell.ECOCreativeCell;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import java.util.List;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Live creative supply for exact orders only. Never infer infinity from a saturated counter. */
public final class ECOCreativeSupply {
    public interface Source {
        ECOCreativeSupply neoecoae$creativeSupply();
        @Nullable ECOCreativeSupply neoecoae$activeRefillSupply();
        long neoecoae$probeCreative(ECOCreativeCell cell, AEKey key, IActionSource source);
    }

    public static final class Probe {
        public final ECOCreativeCell cell;
        public final AEKey key;
        public final IActionSource source;
        public final List<List<MEStorage>> mounts;
        public boolean bound;
        public boolean supplied;

        public Probe(ECOCreativeCell cell, AEKey key, IActionSource source) {
            this.cell = cell;
            this.key = key;
            this.source = source;
            this.mounts = List.of(List.of(cell));
        }
    }

    private final Map<ECOCreativeCell, Boolean> mounts = new HashMap<>();
    private final Map<AEKey, Set<ECOCreativeCell>> candidates = new HashMap<>();
    private final Map<AEKey, Set<Consumer<AEKey>>> refillListeners = new HashMap<>();
    private long candidateRevision;

    @Nullable
    public static ECOCreativeSupply get(MEStorage storage) {
        return storage instanceof Source indexed ? indexed.neoecoae$creativeSupply() : null;
    }

    public void mounted(ECOCreativeCell cell) {
        var drive = cell.drive();
        if (drive == null || mounts.putIfAbsent(cell, false) != null) return;
        availabilityChanged(cell, drive.isOnline());
    }

    public void unmounted(ECOCreativeCell cell) {
        if (Boolean.TRUE.equals(mounts.remove(cell))) updateCandidates(cell, false);
    }

    public void availabilityChanged(ECOCreativeCell cell, boolean online) {
        Boolean previous = mounts.replace(cell, online);
        if (previous != null && previous != online) updateCandidates(cell, online);
    }

    private void updateCandidates(ECOCreativeCell cell, boolean add) {
        candidateRevision++;
        for (AEKey key : cell.configuredKeys()) {
            if (add) {
                candidates.computeIfAbsent(key, ignored -> new HashSet<>()).add(cell);
                stockChanged(key);
            } else {
                var cells = candidates.get(key);
                cells.remove(cell);
                if (cells.isEmpty()) candidates.remove(key);
            }
        }
    }

    public void watchRefill(AEKey key, Consumer<AEKey> listener) {
        refillListeners.computeIfAbsent(key, ignored -> new HashSet<>()).add(listener);
    }

    public void unwatchRefill(AEKey key, Consumer<AEKey> listener) {
        var listeners = refillListeners.get(key);
        if (listeners != null && listeners.remove(listener) && listeners.isEmpty()) refillListeners.remove(key);
    }

    public void stockChanged(AEKey key) {
        var listeners = refillListeners.get(key);
        if (listeners == null) return;
        for (var listener : listeners) {
            listener.accept(key);
        }
    }

    public boolean hasRefillListeners() { return !refillListeners.isEmpty(); }

    public static BigInteger extract(IGrid grid, AEKey key, BigInteger requested, IActionSource source) {
        if (requested.signum() <= 0) return BigInteger.ZERO;
        if (!(grid.getStorageService().getInventory() instanceof Source indexed)) return BigInteger.ZERO;
        var supply = indexed.neoecoae$activeRefillSupply();
        var matching = supply == null ? null : supply.candidates.get(key);
        if (matching == null) return BigInteger.ZERO;
        for (var creative : matching) {
            var drive = creative.drive();
            if (drive == null || drive.isRemoved() || !drive.isMounted() || !drive.isOnline()
                    || drive.getMainNode().getGrid() != grid || drive.getCellInventory() != creative) continue;
            long revision = supply.candidateRevision;
            long available = indexed.neoecoae$probeCreative(creative, key, source);
            if (revision != supply.candidateRevision) return BigInteger.ZERO;
            if (available == 1 && !drive.isRemoved() && drive.getMainNode().getGrid() == grid
                    && drive.isOnline() && drive.isMounted() && drive.getCellInventory() == creative) return requested;
        }
        return BigInteger.ZERO;
    }
}
