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
    private final Map<AEKey, Long> nextRetryTick = new HashMap<>();
    private final Map<AEKey, Integer> backoff = new HashMap<>();
    private final Object2LongMap<AEKey> snapshot = new Object2LongOpenHashMap<>();
    private long lastReconcileTick = Long.MIN_VALUE;
    private long lastAdapterFailureLogTick = Long.MIN_VALUE;
    private boolean running;
    private long sourceSimulates;
    private long sourceModulates;
    private long longMaxProbes;
    private long reservations;
    private long rollbacks;
    private long reconciliations;

    public ECOTransferScheduler(
        ECOFiniteStorageDomain domain,
        IGrid grid,
        MEStorage network,
        IActionSource source,
        ECOStorageSourceAdapterRegistry adapters,
        int keyBudget,
        long nanosBudget,
        Runnable mutationListener
    ) {
        this.domain = domain;
        this.grid = grid;
        this.network = network;
        this.source = source;
        this.adapter = adapters.select(grid, network);
        this.keyBudget = Math.max(1, keyBudget);
        this.nanosBudget = Math.max(1L, nanosBudget);
        this.mutationListener = mutationListener;
    }

    public void start(long gameTick) {
        if (running) return;
        running = true;
        reconcile(gameTick);
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
        if (!running) return 0L;
        if (lastReconcileTick == Long.MIN_VALUE || gameTick - lastReconcileTick >= RECONCILE_INTERVAL) {
            reconcile(gameTick);
        }
        long start = System.nanoTime();
        long moved = 0L;
        int processed = 0;
        int initialSize = dirtyQueue.size();
        while (processed < keyBudget && initialSize-- > 0 && !dirtyQueue.isEmpty()) {
            if (processed > 0 && System.nanoTime() - start >= nanosBudget) break;
            AEKey key = dirtyQueue.removeFirst();
            queued.remove(key);
            if (halted.contains(key)) continue;
            long retry = nextRetryTick.getOrDefault(key, Long.MIN_VALUE);
            if (gameTick < retry) {
                enqueue(key);
                continue;
            }
            processed++;
            long amount = domain.mode() == ECOStorageInterfaceMode.INPUT
                ? transferInput(key)
                : transferOutput(key);
            moved = NEMath.saturatingAdd(moved, amount);
            if (amount > 0L) {
                clearBackoff(key);
                mutationListener.run();
            } else {
                scheduleRetry(key, gameTick);
            }
        }
        return moved;
    }

    private long transferInput(AEKey key) {
        long conventionalInfinite = NEMath.saturatingMultiply(Integer.MAX_VALUE, Math.max(1L, key.getAmountPerUnit()));
        // This bounded probe returns the exact amount for ordinary keys and can also discover a newly-dirty key that
        // was absent from the last full snapshot. Long.MAX_VALUE is reserved for threshold-sized candidates only.
        sourceSimulates++;
        long extractable = network.extract(key, conventionalInfinite, Actionable.SIMULATE, source);
        if (extractable <= 0L) return 0L;
        if (extractable >= conventionalInfinite) {
            longMaxProbes++;
            sourceSimulates++;
            if (network.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE) {
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
        long extracted = network.extract(key, planned, Actionable.MODULATE, source);
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
        if (remaining == 0L && extractable < conventionalInfinite) snapshot.removeLong(key);
        else {
            snapshot.put(key, Math.max(remaining, 1L));
            enqueue(key);
        }
        return accepted;
    }

    private long transferOutput(AEKey key) {
        long available = snapshot.getLong(key);
        if (available <= 0L) return 0L;
        ECOTransferTransaction reservation = domain.reserveExtract(key, available, source);
        reservations++;
        long planned = reservation.plan().amount();
        if (planned <= 0L) {
            reservation.rollback();
            return 0L;
        }
        long accepted = network.insert(key, planned, Actionable.SIMULATE, source);
        sourceSimulates++;
        if (accepted <= 0L) {
            reservation.rollback();
            return 0L;
        }
        long extracted = domain.commitExtract(reservation, accepted, source);
        long inserted = network.insert(key, extracted, Actionable.MODULATE, source);
        sourceModulates++;
        if (inserted < extracted) {
            long restored = domain.insert(key, extracted - inserted, Actionable.MODULATE, source);
            rollbacks++;
            if (restored != extracted - inserted) {
                halt(key, "output rollback restored " + restored + " of " + (extracted - inserted));
            }
        }
        long remaining = Math.max(0L, available - inserted);
        if (remaining == 0L) snapshot.removeLong(key);
        else {
            snapshot.put(key, remaining);
            enqueue(key);
        }
        return inserted;
    }

    private void reconcile(long gameTick) {
        KeyCounter current = new KeyCounter();
        MEStorage observed = domain.mode() == ECOStorageInterfaceMode.INPUT ? network : domain;
        observed.getAvailableStacks(current);
        Set<AEKey> seen = new HashSet<>();
        for (Object2LongMap.Entry<AEKey> entry : current) {
            seen.add(entry.getKey());
            long amount = Math.max(0L, entry.getLongValue());
            if (snapshot.getLong(entry.getKey()) != amount) {
                snapshot.put(entry.getKey(), amount);
                markDirty(entry.getKey());
            }
        }
        for (AEKey key : Set.copyOf(snapshot.keySet())) {
            if (!seen.contains(key)) {
                snapshot.removeLong(key);
                markDirty(key);
            }
        }
        lastReconcileTick = gameTick;
        reconciliations++;
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
        halted.clear();
        markAllDirty();
    }

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
        halted.add(key);
        LOGGER.error("ECO finite storage transfer halted for {}: {}", key, reason);
    }

    private void logAdapterFailure(long gameTick, RuntimeException exception) {
        if (lastAdapterFailureLogTick == Long.MIN_VALUE || gameTick - lastAdapterFailureLogTick >= 200L) {
            lastAdapterFailureLogTick = gameTick;
            LOGGER.warn("ECO storage source adapter failed; periodic reconciliation remains active", exception);
        }
    }

    public Metrics metrics() {
        return new Metrics(sourceSimulates, sourceModulates, longMaxProbes, reservations, rollbacks,
            reconciliations, dirtyQueue.size(), halted.size());
    }

    public record Metrics(
        long simulateCalls,
        long modulateCalls,
        long longMaxProbes,
        long reservations,
        long rollbacks,
        long reconciliations,
        int dirtyQueueLength,
        int haltedKeys
    ) {
    }
}
