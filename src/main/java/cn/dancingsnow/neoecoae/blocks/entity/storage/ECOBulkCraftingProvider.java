package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.impl.storage.bulk.ECOBulkConversionPattern;
import cn.dancingsnow.neoecoae.impl.storage.bulk.ECOBulkStorageCell;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** One provider per ECO storage host. Queued outputs belong to the accepting disk. */
final class ECOBulkCraftingProvider implements ICraftingProvider, ECOFastPathDispatchProvider {
    private final ECOStorageSystemBlockEntity host;
    private List<IPatternDetails> published = List.of();
    private int publishedPriority;
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
    private List<IPatternDetails> patterns() {
        var patterns = new LinkedHashSet<IPatternDetails>();
        for (var cell : cells()) patterns.addAll(cell.getConversionPatterns());
        return List.copyOf(patterns);
    }
    void tick() {
        var cells = cells();
        if (!cells.isEmpty()) {
            var grid = host.getMainNode().getGrid();
            if (grid != null) for (var cell : cells) cell.drainOutputs(grid.getStorageService().getInventory(), IActionSource.ofMachine(host));
        }
        var next = patterns();
        if (!next.equals(published) || publishedPriority != getPatternPriority()) {
            published = next;
            publishedPriority = getPatternPriority();
            ICraftingProvider.requestUpdate(host.getMainNode());
        }
    }
    @Override public List<IPatternDetails> getAvailablePatterns() { return patterns(); }
    @Override public int getPatternPriority() { return host.getStoragePriority(); }
    @Override public boolean isBusy() { return !active(); }
    private ECOBulkStorageCell target(ECOBulkConversionPattern pattern) {
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
    @Override public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
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
    @Override public ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
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
