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
import org.jetbrains.annotations.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** One provider per ECO storage host. Queued outputs belong to the accepting disk. */
final class ECOBulkCraftingProvider implements ICraftingProvider, ECOFastPathDispatchProvider {
    private final ECOStorageSystemBlockEntity host;
    private List<IPatternDetails> published = List.of();
    private int publishedPriority;
    /**
     * Rebuilding conversion patterns allocates and divides BigIntegers for every account of every cell, so a
     * cell that keeps its identity is only re-derived this often; a cell added or removed is seen at once.
     * Advertising lags a config change by at most this long, and a withdrawn pattern is still refused at
     * dispatch because {@link #target} reads the cells live.
     */
    private static final int PATTERN_RECHECK_TICKS = 20;
    private List<ECOBulkStorageCell> publishedCells = List.of();
    private int ticksSincePatternCheck = PATTERN_RECHECK_TICKS;
    ECOBulkCraftingProvider(ECOStorageSystemBlockEntity host) { this.host = host; }

    private boolean active() {
        return host.isFormed() && !host.isInfiniteMode() && !host.isStorageInterfaceTransferMode()
            && host.getMainNode().isActive();
    }
    private List<ECOBulkStorageCell> cells() {
        if (!active()) return List.of();
        List<ECOBulkStorageCell> result = new ArrayList<>();
        for (var drive : host.getStorageDrivesForIntegration()) {
            if (drive.getCellInventory() instanceof ECOBulkStorageCell cell && host.getTier().compareTo(cell.getTier()) >= 0) result.add(cell);
        }
        return result;
    }
    private static List<IPatternDetails> patterns(List<ECOBulkStorageCell> cells) {
        var patterns = new LinkedHashSet<IPatternDetails>();
        for (var cell : cells) patterns.addAll(cell.getConversionPatterns());
        return List.copyOf(patterns);
    }
    private static boolean sameCells(List<ECOBulkStorageCell> first, List<ECOBulkStorageCell> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) if (first.get(i) != second.get(i)) return false;
        return true;
    }
    void tick() {
        var cells = cells();
        if (!cells.isEmpty()) {
            var grid = host.getMainNode().getGrid();
            if (grid != null) for (var cell : cells) cell.drainOutputs(grid.getStorageService().getInventory(), IActionSource.ofMachine(host));
        }
        boolean cellsChanged = !sameCells(cells, publishedCells);
        if (!cellsChanged && ++ticksSincePatternCheck < PATTERN_RECHECK_TICKS
                && publishedPriority == getPatternPriority()) {
            return;
        }
        ticksSincePatternCheck = 0;
        publishedCells = cells;
        var next = patterns(cells);
        if (!next.equals(published) || publishedPriority != getPatternPriority()) {
            published = next;
            publishedPriority = getPatternPriority();
            ICraftingProvider.requestUpdate(host.getMainNode());
        }
    }
    /** The list last handed to AE2 through requestUpdate; tick() keeps it current. */
    @Override public List<IPatternDetails> getAvailablePatterns() { return published; }
    @Override public int getPatternPriority() { return host.getStoragePriority(); }
    @Override public boolean isBusy() { return !active(); }
    private @Nullable ECOBulkStorageCell target(ECOBulkConversionPattern pattern) {
        for (var cell : cells()) if (cell.getConversionPatterns().contains(pattern)) return cell;
        return null;
    }
    @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        if (!(details instanceof ECOBulkConversionPattern pattern) || !pattern.matchesInputs(inputs)) return false;
        ECOBulkStorageCell cell = target(pattern);
        if (cell == null) return false;
        enqueue(cell, pattern, BigInteger.ONE);
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
            ECOBulkStorageCell cell = target(pattern);
            if (cell == null || copies <= 0 || copies > capacity || !batch.remainingTotal().isEmpty()
                    || !batch.exactInputTotal().isEmpty()
                    || !batch.inputTotal().equals(List.of(new GenericStack(pattern.inputKey(), Math.multiplyExact(pattern.inputCount(), copies))))
                    || !batch.outputTotal().equals(List.of(new GenericStack(pattern.outputKey(), Math.multiplyExact(pattern.outputCount(), copies))))) return false;
            enqueue(cell, pattern, BigInteger.valueOf(copies));
            return true;
        });
    }
    @Override public @Nullable ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
        if (requested.signum() <= 0 || eco$prepareFastPath(context) == null) return null;
        var pattern = (ECOBulkConversionPattern) context.pattern();
        return new ExactPreparation(requested, () -> {
            ECOBulkStorageCell cell = target(pattern);
            if (cell == null) return false;
            enqueue(cell, pattern, requested);
            return true;
        });
    }
    private void enqueue(ECOBulkStorageCell cell, ECOBulkConversionPattern pattern, BigInteger copies) {
        try { cell.enqueueOutput(pattern.outputKey(), BigInteger.valueOf(pattern.outputCount()).multiply(copies)); }
        catch (RuntimeException failure) { throw new ECOIndeterminateBatchException("ECO bulk conversion may already be saved on disk", failure); }
    }
}
