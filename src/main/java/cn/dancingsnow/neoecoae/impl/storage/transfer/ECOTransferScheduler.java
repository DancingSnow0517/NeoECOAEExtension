package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.util.NEMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main-thread bounded transfer scheduler with FIFO dirty keys, retry backoff and periodic reconciliation. */
public final class ECOTransferScheduler implements SourceChangeSink {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOTransferScheduler.class);
    private static final int RECONCILE_INTERVAL = 200;
    private static final int MAX_BACKOFF = 200;
    static final int INITIAL_CHUNK_SIZE = 4_096;
    static final int MIN_CHUNK_SIZE = 64;
    static final int MAX_CHUNK_SIZE = 65_536;
    static final long SLOW_EXTRACT_NANOS = 1_000_000L;
    static final long FAST_EXTRACT_NANOS = 250_000L;

    private final ECOFiniteStorageDomain domain;
    private final IGrid grid;
    private final MEStorage network;
    private final IActionSource source;
    private final ECOStorageSourceAdapter adapter;
    private final int keyBudget;
    private final long nanosBudget;
    private final Runnable mutationListener;
    private final ArrayDeque<AEKey> dirtyQueue = new ArrayDeque<>();
    private final Set<AEKey> queued = new HashSet<>();
    private final Set<AEKey> halted = new HashSet<>();
    private final Set<AEKey> transferHalted = new HashSet<>();
    private final Set<AEKey> flushHalted = new HashSet<>();
    private final Map<AEKey, Long> nextRetryTick = new HashMap<>();
    private final Map<AEKey, Integer> backoff = new HashMap<>();
    private final Map<AEKey, AdaptiveChunk> adaptiveChunks = new HashMap<>();
    private final Object2LongMap<AEKey> snapshot = new Object2LongOpenHashMap<>();
    private long lastReconcileTick = Long.MIN_VALUE;
    private java.util.Iterator<Object2LongMap.Entry<AEKey>> reconcileEntries;
    private java.util.Iterator<AEKey> reconcileRemovals;
    private final Set<AEKey> reconcileSeen = new HashSet<>();
    private boolean reconcileWakeAll;
    private long lastAdapterFailureLogTick = Long.MIN_VALUE;
    private boolean running;
    private long sourceSimulates;
    private long sourceModulates;
    private long longMaxProbes;
    private long reservations;
    private long rollbacks;
    private long reconciliations;
    private long maxSingleExtractNanos;
    private long currentGameTick;
    private final long transferAmountBudget;
    private long lastFlushFailureLogTick = Long.MIN_VALUE;
    private final ECOSophisticatedMutationBatch.FlushFailureSink flushSink =
        new ECOSophisticatedMutationBatch.FlushFailureSink() {
            @Override
            public void onFlushFailure(Set<AEKey> keys, Throwable failure) {
                ECOTransferScheduler.this.onFlushFailure(keys, failure);
            }

            @Override
            public void onFlushSuccess(Set<AEKey> keys) {
                flushHalted.removeAll(keys);
                keys.stream().filter(key -> !transferHalted.contains(key)).forEach(halted::remove);
                keys.forEach(ECOTransferScheduler.this::markDirty);
            }
        };

    public ECOTransferScheduler(
        ECOFiniteStorageDomain domain,
        IGrid grid,
        MEStorage network,
        IActionSource source,
        ECOStorageSourceAdapterRegistry adapters,
        int keyBudget,
        long nanosBudget,
        long transferAmountBudget,
        Runnable mutationListener
    ) {
        this.domain = domain;
        this.grid = grid;
        this.network = network;
        this.source = source;
        this.adapter = adapters.select(grid, network);
        this.keyBudget = Math.max(1, keyBudget);
        this.nanosBudget = Math.max(1L, nanosBudget);
        this.transferAmountBudget = Math.max(1L, transferAmountBudget);
        this.mutationListener = mutationListener;
    }

    public void start(long gameTick) {
        if (running) return;
        running = true;
        try {
            adapter.subscribe(this);
            adapter.refreshSnapshot(this);
        } catch (RuntimeException e) {
            logAdapterFailure(gameTick, e);
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        try {
            adapter.unsubscribe(this);
        } catch (RuntimeException e) {
            LOGGER.warn("ECO storage source adapter failed while unsubscribing", e);
        }
        dirtyQueue.clear();
        queued.clear();
    }

    public long tick(long gameTick) {
        return tick(gameTick, nanosBudget);
    }

    public long tick(long gameTick, long availableNanos) {
        if (!running) return 0L;
        long nanosBudget = Math.min(this.nanosBudget, availableNanos);
        currentGameTick = gameTick;
        long start = System.nanoTime();
        if (reconcileEntries == null && reconcileRemovals == null
            && (lastReconcileTick == Long.MIN_VALUE || gameTick - lastReconcileTick >= RECONCILE_INTERVAL)) {
            try { beginReconcile(); }
            catch (RuntimeException e) {
                lastReconcileTick = gameTick;
                logAdapterFailure(gameTick, e);
            }
        }
        advanceReconcile(gameTick, start, nanosBudget / 2L);
        if (reconcileRemovals != null) return 0L;
        if (System.nanoTime() - start >= nanosBudget) return 0L;
        try (var batch = adapter.mutationBatch(flushSink)) {
            long moved = 0L;
            int processed = 0;
            int initialSize = dirtyQueue.size();
            while (processed < keyBudget && initialSize-- > 0 && !dirtyQueue.isEmpty()) {
                if (System.nanoTime() - start >= nanosBudget) break;
                if (moved >= transferAmountBudget) break;
                AEKey key = dirtyQueue.removeFirst();
                queued.remove(key);
                if (halted.contains(key)) continue;
                long retry = nextRetryTick.getOrDefault(key, Long.MIN_VALUE);
                if (gameTick < retry) {
                    enqueue(key);
                    continue;
                }
                ECOSophisticatedMutationBatch.setCurrentKey(key);
                processed++;
                long amount;
                try {
                    amount = domain.mode() == ECOStorageInterfaceMode.INPUT
                        ? transferInput(key, transferAmountBudget - moved)
                        : transferOutput(key, transferAmountBudget - moved);
                } catch (RuntimeException e) {
                    halt(key, "transfer outcome uncertain: " + e);
                    continue;
                } finally {
                    ECOSophisticatedMutationBatch.setCurrentKey(null);
                }
                moved = NEMath.saturatingAdd(moved, amount);
                if (amount > 0L) {
                    clearBackoff(key);
                    mutationListener.run();
                } else {
                    scheduleRetry(key, gameTick);
                }
            }
            ECOSophisticatedMutationBatch.setCurrentKey(null);
            return moved;
        } finally {
            ECOSophisticatedMutationBatch.setCurrentKey(null);
        }
    }

    private long transferInput(AEKey key, long remainingBudget) {
        long conventionalInfinite = NEMath.saturatingMultiply(Integer.MAX_VALUE, Math.max(1L, key.getAmountPerUnit()));
        // This bounded probe returns the exact amount for ordinary keys and can also discover a newly-dirty key that
        // was absent from the last full snapshot. Long.MAX_VALUE is reserved for threshold-sized candidates only.
        if (ECOStorageSourceSafety.isEffectivelyInfiniteSource(network, key, snapshot.getLong(key), source)) {
            return 0L;
        }
        boolean chunked = adapter.status() == ECOStorageSourceAdapter.Status.ACTIVE;
        AdaptiveChunk adaptive = adaptiveChunks.computeIfAbsent(key, ignored -> new AdaptiveChunk());
        long requestAmount = chunked
            ? Math.min(conventionalInfinite, NEMath.saturatingMultiply(adaptive.size(), Math.max(1L, key.getAmountPerUnit())))
            : conventionalInfinite;
        requestAmount = Math.min(requestAmount, remainingBudget);
        sourceSimulates++;
        long extractable = Math.min(requestAmount, observedExtract(key, requestAmount, Actionable.SIMULATE, adaptive));
        if (extractable <= 0L) {
            return 0L;
        }
        if (!chunked && extractable >= conventionalInfinite) {
            longMaxProbes++;
            sourceSimulates++;
            if (observedExtract(key, Long.MAX_VALUE, Actionable.SIMULATE, adaptive) == Long.MAX_VALUE) {
                return 0L;
            }
        }
        ECOTransferTransaction reservation = domain.reserveInsert(key, extractable, source);
        reservations++;
        long planned = reservation.plan().amount();
        if (planned <= 0L) {
            reservation.rollback();
            return 0L;
        }
        sourceModulates++;
        long extracted = observedExtract(key, planned, Actionable.MODULATE, adaptive);
        if (extracted < 0L || extracted > planned) throw new IllegalStateException("Invalid source acknowledgement");
        if (extracted <= 0L) {
            reservation.rollback();
            return 0L;
        }
        long accepted = domain.commitInsert(reservation, extracted, source);
        if (accepted < extracted) {
            sourceModulates++;
            long restored = network.insert(key, extracted - accepted, Actionable.MODULATE, source);
            rollbacks++;
            if (restored != extracted - accepted) {
                halt(key, "input rollback restored " + restored + " of " + (extracted - accepted));
            }
        }
        long remaining = Math.max(0L, extractable - extracted);
        boolean mayHaveMore = extractable >= requestAmount;
        if (remaining == 0L && extractable < conventionalInfinite && !mayHaveMore) snapshot.removeLong(key);
        else {
            snapshot.put(key, Math.max(remaining, mayHaveMore ? 1L : 0L));
            enqueue(key);
        }
        return accepted;
    }

    private long transferOutput(AEKey key, long remainingBudget) {
        long available = Math.min(snapshot.getLong(key), remainingBudget);
        if (available <= 0L) return 0L;
        ECOTransferTransaction reservation = domain.reserveExtract(key, available, source);
        reservations++;
        long planned = reservation.plan().amount();
        if (planned <= 0L) {
            reservation.rollback();
            return 0L;
        }
        long accepted = Math.min(planned, network.insert(key, planned, Actionable.SIMULATE, source));
        sourceSimulates++;
        if (accepted <= 0L) {
            reservation.rollback();
            return 0L;
        }
        long extracted = domain.commitExtract(reservation, accepted, source);
        long inserted = network.insert(key, extracted, Actionable.MODULATE, source);
        if (inserted < 0L || inserted > extracted) throw new IllegalStateException("Invalid destination acknowledgement");
        sourceModulates++;
        if (inserted < extracted) {
            long restored = domain.insert(key, extracted - inserted, Actionable.MODULATE, source);
            rollbacks++;
            if (restored != extracted - inserted) {
                halt(key, "output rollback restored " + restored + " of " + (extracted - inserted));
            }
        }
        long remaining = Math.max(0L, snapshot.getLong(key) - inserted);
        if (remaining == 0L) snapshot.removeLong(key);
        else {
            snapshot.put(key, remaining);
            enqueue(key);
        }
        return inserted;
    }

    private void beginReconcile() {
        reconcileWakeAll = lastReconcileTick != Long.MIN_VALUE && adapter.status() == ECOStorageSourceAdapter.Status.ACTIVE;
        KeyCounter current = new KeyCounter();
        MEStorage observed = domain.mode() == ECOStorageInterfaceMode.INPUT ? network : domain;
        if (observed == network) {
            try (var ignored = adapter.observationScope(this)) {
                observed.getAvailableStacks(current);
            } catch (Exception e) {
                throw e instanceof RuntimeException runtime ? runtime : new RuntimeException(e);
            }
        } else {
            observed.getAvailableStacks(current);
        }
        reconcileSeen.clear();
        reconcileEntries = current.iterator();
    }

    private void advanceReconcile(long gameTick, long start, long budget) {
        int processed = 0;
        while (reconcileEntries != null && processed++ < 256 && System.nanoTime() - start < budget) {
            if (!reconcileEntries.hasNext()) {
                reconcileEntries = null;
                reconcileRemovals = snapshot.keySet().iterator();
                break;
            }
            Object2LongMap.Entry<AEKey> entry = reconcileEntries.next();
            reconcileSeen.add(entry.getKey());
            long amount = Math.max(0L, entry.getLongValue());
            if (snapshot.getLong(entry.getKey()) != amount || reconcileWakeAll) {
                snapshot.put(entry.getKey(), amount);
                markDirty(entry.getKey());
            }
        }
        while (reconcileRemovals != null && processed++ < 256 && System.nanoTime() - start < budget) {
            if (!reconcileRemovals.hasNext()) {
                reconcileRemovals = null;
                reconcileSeen.clear();
                lastReconcileTick = gameTick;
                reconciliations++;
                break;
            }
            AEKey key = reconcileRemovals.next();
            if (!reconcileSeen.contains(key)) {
                reconcileRemovals.remove();
                markDirty(key);
            }
        }
    }

    @Override
    public void markDirty(AEKey key) {
        if (key == null) return;
        clearBackoff(key);
        enqueue(key);
    }

    @Override
    public void markAllDirty() {
        for (AEKey key : snapshot.keySet()) markDirty(key);
    }

    public void wakeAll() {
        transferHalted.clear();
        halted.clear();
        halted.addAll(flushHalted);
        markAllDirty();
    }

    public Set<AEKey> haltedKeys() { return Set.copyOf(transferHalted); }
    public void restoreHalted(Set<AEKey> keys) { transferHalted.addAll(keys); halted.addAll(keys); }

    private void enqueue(AEKey key) {
        if (queued.add(key)) dirtyQueue.addLast(key);
    }

    private void scheduleRetry(AEKey key, long gameTick) {
        int delay = nextBackoff(backoff.getOrDefault(key, 0));
        backoff.put(key, delay);
        nextRetryTick.put(key, gameTick + delay);
        enqueue(key);
    }

    static int nextBackoff(int previous) {
        return previous <= 0 ? 1 : Math.min(MAX_BACKOFF, previous * 2);
    }

    private void clearBackoff(AEKey key) {
        backoff.remove(key);
        nextRetryTick.remove(key);
    }

    private void halt(AEKey key, String reason) {
        transferHalted.add(key);
        halted.add(key);
        LOGGER.error("ECO finite storage transfer halted for {}: {}", key, reason);
    }

    private long observedExtract(AEKey key, long amount, Actionable mode, AdaptiveChunk adaptive) {
        long start = System.nanoTime();
        try (var ignored = adapter.observationScope(this)) {
            return network.extract(key, amount, mode, source);
        } catch (Exception e) {
            throw e instanceof RuntimeException runtime ? runtime : new RuntimeException(e);
        } finally {
            long elapsed = System.nanoTime() - start;
            maxSingleExtractNanos = Math.max(maxSingleExtractNanos, elapsed);
            adaptive.record(elapsed);
        }
    }

    private void onFlushFailure(Set<AEKey> keys, Throwable failure) {
        flushHalted.addAll(keys);
        halted.addAll(keys);
        if (lastFlushFailureLogTick == Long.MIN_VALUE || currentGameTick - lastFlushFailureLogTick >= 200L) {
            lastFlushFailureLogTick = currentGameTick;
            LOGGER.error("ECO Sophisticated batch flush failed; {} related keys were halted", keys.size(), failure);
        }
    }


    private void logAdapterFailure(long gameTick, RuntimeException exception) {
        if (lastAdapterFailureLogTick == Long.MIN_VALUE || gameTick - lastAdapterFailureLogTick >= 200L) {
            lastAdapterFailureLogTick = gameTick;
            LOGGER.warn("ECO storage source adapter failed; periodic reconciliation remains active", exception);
        }
    }

    public Metrics metrics() {
        var sophisticated = ECOSophisticatedMetrics.snapshot();
        int adaptiveChunkSize = adaptiveChunks.values().stream().mapToInt(AdaptiveChunk::size)
            .max().orElse(INITIAL_CHUNK_SIZE);
        return new Metrics(sourceSimulates, sourceModulates, longMaxProbes, reservations, rollbacks,
            reconciliations, dirtyQueue.size(), halted.size(), sophisticated.indexedExtracts(),
            sophisticated.fallbackExtracts(), sophisticated.matchingSlots(), sophisticated.avoidedSlotScans(),
            sophisticated.dirtySlotEvents(), sophisticated.deferredSaves(), sophisticated.batchFlushes(),
            sophisticated.domainFlushFailures(), maxSingleExtractNanos, adaptiveChunkSize, transferAmountBudget);
    }

    public record Metrics(
        long simulateCalls,
        long modulateCalls,
        long longMaxProbes,
        long reservations,
        long rollbacks,
        long reconciliations,
        int dirtyQueueLength,
        int haltedKeys,
        long sophisticatedIndexedExtracts,
        long sophisticatedFallbackExtracts,
        long sophisticatedMatchingSlots,
        long sophisticatedAvoidedSlotScans,
        long sophisticatedDirtySlotEvents,
        long sophisticatedDeferredSaves,
        long sophisticatedBatchFlushes,
        long domainFlushFailures,
        long maxSingleExtractNanos,
        int adaptiveChunkSize,
        long transferAmountBudget
    ) {
    }

    static final class AdaptiveChunk {
        private int size = INITIAL_CHUNK_SIZE;
        private int consecutiveFast;

        int size() {
            return size;
        }

        void record(long nanos) {
            if (nanos > SLOW_EXTRACT_NANOS) {
                size = Math.max(MIN_CHUNK_SIZE, size / 2);
                consecutiveFast = 0;
            } else if (nanos < FAST_EXTRACT_NANOS) {
                if (++consecutiveFast >= 8) {
                    size = Math.min(MAX_CHUNK_SIZE, size * 2);
                    consecutiveFast = 0;
                }
            } else {
                consecutiveFast = 0;
            }
        }
    }

}
