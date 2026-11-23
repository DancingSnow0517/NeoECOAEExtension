package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.storage.bulk.ECOBulkConversionPattern;
import cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell;
import cn.dancingsnow.neoecoae.storage.bulk.ECOCompressionService;
import org.jetbrains.annotations.Nullable;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One provider per ECO storage host. Queued outputs belong to the accepting disk. */
final class ECOBulkCraftingProvider implements ICraftingProvider, ECOFastPathDispatchProvider {
    private final ECOStorageSystemBlockEntity host;
    private final Map<ECODriveBlockEntity, Installation> installed = new LinkedHashMap<>();
    private final Map<IPatternDetails, TreeMap<Integer, Installation>> candidates = new LinkedHashMap<>();
    private final LinkedHashSet<Installation> changed = new LinkedHashSet<>();
    private final TreeMap<Integer, Installation> pending = new TreeMap<>();
    private boolean membershipDirty = true;
    private boolean publicationDirty;
    private boolean wasActive;
    private boolean mountedActive;
    private long compressionRevision = -1;
    private List<IPatternDetails> published = List.of();
    private int publishedPriority;
    private List<IPatternDetails> mounted = List.of();

    private static final class Installation {
        private final ECODriveBlockEntity drive;
        private final int order;
        private @Nullable ECOBulkStorageCell cell;
        private List<IPatternDetails> patterns = List.of();

        private Installation(ECODriveBlockEntity drive, int order) {
            this.drive = drive;
            this.order = order;
        }
    }
    ECOBulkCraftingProvider(ECOStorageSystemBlockEntity host) { this.host = host; }

    private boolean active() {
        return !host.isRemoved() && host.isFormed() && !host.isInfiniteMode() && !host.isStorageInterfaceTransferMode()
            && host.getMainNode().isActive();
    }

    void topologyChanged() {
        membershipDirty = true;
    }

    void detach() {
        installed.values().forEach(installation -> replace(installation, null));
        installed.clear();
        candidates.clear();
        changed.clear();
        pending.clear();
        published = List.of();
        mounted = List.of();
        publicationDirty = false;
        wasActive = false;
        mountedActive = false;
        topologyChanged();
    }

    void driveChanged(ECODriveBlockEntity drive, boolean removed) {
        if (membershipDirty) refreshDirectory();
        Installation installation = installed.get(drive);
        if (installation != null) {
            replace(installation, removed ? null : cellAt(drive));
            changed.add(installation);
        }
    }

    private @Nullable ECOBulkStorageCell cellAt(ECODriveBlockEntity drive) {
        return drive.isInstalledStorageAvailable() && drive.getCellInventory() instanceof ECOBulkStorageCell cell
            && host.getTier().compareTo(cell.getTier()) >= 0 ? cell : null;
    }

    private void replace(Installation installation, @Nullable ECOBulkStorageCell next) {
        if (installation.cell == next) return;
        if (installation.cell != null) installation.cell.setConversionChangeListener(null);
        installation.cell = next;
        if (next != null) {
            next.setConversionChangeListener(() -> {
                changed.add(installation);
                host.queueBulkCraftingPublication();
            });
        }
    }

    private void refreshDirectory() {
        if (membershipDirty) {
            installed.values().forEach(installation -> replace(installation, null));
            installed.clear();
            changed.clear();
            pending.clear();
            publicationDirty |= !candidates.isEmpty();
            candidates.clear();
            for (var drive : host.getStorageDrivesForIntegration()) {
                Installation installation = new Installation(drive, installed.size());
                installed.put(drive, installation);
                replace(installation, cellAt(drive));
                if (installation.cell != null) changed.add(installation);
            }
            membershipDirty = false;
        }
        long revision = ECOCompressionService.revision();
        if (compressionRevision != revision) {
            compressionRevision = revision;
            for (Installation installation : installed.values()) {
                if (installation.cell != null) changed.add(installation);
            }
        }
        while (!changed.isEmpty()) {
            Installation installation = changed.removeFirst();
            var cell = installation.cell;
            if (cell != null && cell.hasPendingOutputs()) pending.put(installation.order, installation);
            else pending.remove(installation.order);
            List<IPatternDetails> next = cell == null ? List.of() : cell.getConversionPatterns();
            if (next.equals(installation.patterns)) continue;
            for (var pattern : installation.patterns) {
                if (next.contains(pattern)) continue;
                var disks = candidates.get(pattern);
                disks.remove(installation.order);
                if (disks.isEmpty()) {
                    candidates.remove(pattern);
                    publicationDirty = true;
                }
            }
            for (var pattern : next) {
                var disks = candidates.get(pattern);
                if (disks == null) {
                    disks = new TreeMap<>();
                    candidates.put(pattern, disks);
                    publicationDirty = true;
                }
                disks.put(installation.order, installation);
            }
            installation.patterns = next;
        }
    }

    void refreshPublication() {
        refreshDirectory();
        boolean active = active();
        updatePublishedList(active);
        if (mounted != published || active != wasActive || publishedPriority != getPatternPriority()) {
            mounted = published;
            wasActive = active;
            publishedPriority = getPatternPriority();
            ICraftingProvider.requestUpdate(host.getMainNode());
        }
    }

    private boolean updatePublishedList(boolean active) {
        if (!publicationDirty && active == mountedActive) return false;
        publicationDirty = false;
        mountedActive = active;
        var next = active ? List.copyOf(candidates.keySet()) : List.<IPatternDetails>of();
        if (next.equals(published)) return false;
        published = next;
        return true;
    }

    void tick() {
        refreshPublication();
        if (!active() || pending.isEmpty()) return;
        var grid = host.getMainNode().getGrid();
        if (grid == null) return;
        var inventory = grid.getStorageService().getInventory();
        var source = IActionSource.ofMachine(host);
        for (var iterator = pending.values().iterator(); iterator.hasNext();) {
            var installation = iterator.next();
            var cell = installation.cell;
            if (cell == null || cellAt(installation.drive) != cell) continue;
            cell.drainOutputs(inventory, source);
            if (!cell.hasPendingOutputs()) iterator.remove();
        }
    }

    @Override public List<IPatternDetails> getAvailablePatterns() {
        refreshDirectory();
        updatePublishedList(active());
        return published;
    }
    @Override public int getPatternPriority() { return host.getStoragePriority(); }
    @Override public boolean isBusy() { return !active(); }
    private @Nullable Installation target(ECOBulkConversionPattern pattern) {
        if (!active()) return null;
        refreshDirectory();
        var disks = candidates.get(pattern);
        if (disks != null) for (var installation : disks.values()) {
            var cell = installation.cell;
            if (cell != null && cellAt(installation.drive) == cell && cell.supportsConversion(pattern)) return installation;
        }
        return null;
    }
    @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        if (!(details instanceof ECOBulkConversionPattern pattern) || !pattern.matchesInputs(inputs)) return false;
        var installation = target(pattern);
        if (installation == null) return false;
        enqueue(installation, pattern, BigInteger.ONE);
        inputs[0].clear();
        return true;
    }
    @Override public @Nullable Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        if (!(context.pattern() instanceof ECOBulkConversionPattern pattern) || target(pattern) == null
                || !context.containerItems().isEmpty() || !pattern.matchesInputs(context.inputCounters())
                || !context.outputs().equals(List.of(new GenericStack(pattern.outputKey(), pattern.outputCount())))) return null;
        long capacity = Math.min(Long.MAX_VALUE / pattern.inputCount(), Long.MAX_VALUE / pattern.outputCount());
        return new Preparation(capacity, null, false, batch -> {
            long copies = batch.craftCount();
            var installation = target(pattern);
            if (installation == null || copies <= 0 || copies > capacity || !batch.remainingTotal().isEmpty()
                    || !batch.exactInputTotal().isEmpty()
                    || !batch.inputTotal().equals(List.of(new GenericStack(pattern.inputKey(), Math.multiplyExact(pattern.inputCount(), copies))))
                    || !batch.outputTotal().equals(List.of(new GenericStack(pattern.outputKey(), Math.multiplyExact(pattern.outputCount(), copies))))) return false;
            enqueue(installation, pattern, BigInteger.valueOf(copies));
            return true;
        });
    }
    @Override public @Nullable ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
        if (requested.signum() <= 0 || eco$prepareFastPath(context) == null) return null;
        var pattern = (ECOBulkConversionPattern) context.pattern();
        return new ExactPreparation(requested, () -> {
            var installation = target(pattern);
            if (installation == null) return false;
            enqueue(installation, pattern, requested);
            return true;
        });
    }
    private void enqueue(Installation installation, ECOBulkConversionPattern pattern, BigInteger copies) {
        pending.put(installation.order, installation);
        try { installation.cell.enqueueOutput(pattern.outputKey(), BigInteger.valueOf(pattern.outputCount()).multiply(copies)); }
        catch (RuntimeException failure) { throw new ECOIndeterminateBatchException("ECO bulk conversion may already be saved on disk", failure); }
    }
}
