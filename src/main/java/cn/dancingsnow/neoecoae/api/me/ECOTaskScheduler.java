package cn.dancingsnow.neoecoae.api.me;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;

/** The single ready/blocked/deferred scheduler used by crafting CPU production logic. */
public class ECOTaskScheduler<T> {
    public enum TaskState { READY, BLOCKED, DEFERRED, LEASED, ABSENT }

    private final ArrayDeque<T> readyNormal = new ArrayDeque<>();
    private final ArrayDeque<T> readyImmediate = new ArrayDeque<>();
    private final ArrayDeque<T> readyDeferred = new ArrayDeque<>();
    private final Set<T> queued = Collections.newSetFromMap(new IdentityHashMap<>());
    private final IdentityHashMap<T, InputDependencies> blocked = new IdentityHashMap<>();
    private final IdentityHashMap<T, Long> blockedAtTicks = new IdentityHashMap<>();
    private final IdentityHashMap<T, Long> deferredUntil = new IdentityHashMap<>();
    private final Map<AEKey, Set<T>> blockedByExact = new LinkedHashMap<>();
    private final Map<net.minecraft.resources.ResourceLocation, Set<T>> blockedByFuzzy =
            new LinkedHashMap<>();
    private final Set<T> blockedOnAnyChange = Collections.newSetFromMap(new IdentityHashMap<>());
    private List<T> jobOrder = List.of();
    @Nullable private T leased;
    private long currentTick = Long.MIN_VALUE;
    private int immediateBurst;
    private int normalBurst;

    public void startJob(List<T> tasks) {
        readyNormal.clear();
        readyImmediate.clear();
        readyDeferred.clear();
        queued.clear();
        blocked.clear();
        blockedAtTicks.clear();
        deferredUntil.clear();
        blockedByExact.clear();
        blockedByFuzzy.clear();
        blockedOnAnyChange.clear();
        jobOrder = List.copyOf(tasks);
        leased = null;
        immediateBurst = 0;
        normalBurst = 0;
        for (T task : tasks) enqueue(task);
    }

    @Nullable
    public T poll() {
        T task = pollReady();
        if (task != null) leased = task;
        return task;
    }

    public void block(T task, InputDependencies dependencies) {
        if (task == leased) leased = null;
        removeFromReady(task);
        removeBlockedIndexes(task);
        deferredUntil.remove(task);
        blocked.put(task, dependencies);
        blockedAtTicks.put(task, currentTick);
        if (dependencies.wakeOnAnyChange()) blockedOnAnyChange.add(task);
        for (AEKey key : dependencies.exactKeys()) {
            blockedByExact.computeIfAbsent(key, ignored ->
                    Collections.newSetFromMap(new IdentityHashMap<>())).add(task);
        }
        for (var fuzzyId : dependencies.fuzzyItemIds()) {
            blockedByFuzzy.computeIfAbsent(fuzzyId, ignored ->
                    Collections.newSetFromMap(new IdentityHashMap<>())).add(task);
        }
    }

    public int wake(AEKey key) {
        Set<T> awakened = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<T> exact = blockedByExact.get(key);
        if (exact != null) awakened.addAll(exact);
        awakened.addAll(blockedOnAnyChange);
        return wakeTasks(awakened);
    }

    public int wakeFuzzy(net.minecraft.resources.ResourceLocation fuzzyItemId) {
        Set<T> awakened = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<T> fuzzy = blockedByFuzzy.get(fuzzyItemId);
        if (fuzzy != null) awakened.addAll(fuzzy);
        awakened.addAll(blockedOnAnyChange);
        return wakeTasks(awakened);
    }

    public void beginTick(long tick) {
        currentTick = tick;
        immediateBurst = 0;
        normalBurst = 0;
        releaseLeasedIfUnresolved();
        List<T> awakened = new ArrayList<>();
        for (var entry : deferredUntil.entrySet()) {
            if (entry.getValue() <= tick) awakened.add(entry.getKey());
        }
        for (T task : awakened) {
            deferredUntil.remove(task);
            enqueueDeferred(task);
        }
    }

    public void deferUntilNextTick(T task) {
        if (task == leased) leased = null;
        removeFromReady(task);
        long nextTick = currentTick == Long.MAX_VALUE ? Long.MAX_VALUE : currentTick + 1L;
        deferredUntil.put(task, nextTick);
    }

    public void requeue(T task) {
        if (task == leased) leased = null;
        deferredUntil.remove(task);
        enqueue(task);
    }

    public void remove(T task) {
        if (task == leased) leased = null;
        removeFromReady(task);
        deferredUntil.remove(task);
        removeBlockedIndexes(task);
        blocked.remove(task);
        blockedAtTicks.remove(task);
    }

    public int readySize() {
        return readyNormal.size() + readyImmediate.size() + readyDeferred.size();
    }

    public int blockedSize() { return blocked.size(); }
    public int deferredSize() { return deferredUntil.size(); }
    public int leasedSize() { return leased == null ? 0 : 1; }

    public TaskState stateOf(@Nullable T task) {
        if (task == null) return TaskState.ABSENT;
        if (task == leased) return TaskState.LEASED;
        if (queued.contains(task)) return TaskState.READY;
        if (blocked.containsKey(task)) return TaskState.BLOCKED;
        if (deferredUntil.containsKey(task)) return TaskState.DEFERRED;
        return TaskState.ABSENT;
    }

    private int wakeTasks(Iterable<T> tasks) {
        Set<T> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        for (T task : tasks) candidates.add(task);
        int awakened = 0;
        for (T task : jobOrder) {
            if (candidates.contains(task) && blocked.containsKey(task)) {
                removeBlockedIndexes(task);
                blocked.remove(task);
                blockedAtTicks.remove(task);
                enqueueImmediate(task);
                awakened++;
            }
        }
        return awakened;
    }

    public void releaseLeasedIfUnresolved() {
        if (leased != null && !blocked.containsKey(leased)
                && !deferredUntil.containsKey(leased) && !queued.contains(leased)) {
            T previous = leased;
            leased = null;
            enqueue(previous);
        } else {
            leased = null;
        }
    }

    private void removeBlockedIndexes(T task) {
        InputDependencies dependencies = blocked.get(task);
        if (dependencies == null) {
            blockedOnAnyChange.remove(task);
            return;
        }
        blockedOnAnyChange.remove(task);
        for (AEKey key : dependencies.exactKeys()) removeIndexEntry(blockedByExact, key, task);
        for (var fuzzyId : dependencies.fuzzyItemIds()) removeIndexEntry(blockedByFuzzy, fuzzyId, task);
    }

    private static <K, T> void removeIndexEntry(Map<K, Set<T>> index, K key, T task) {
        Set<T> tasks = index.get(key);
        if (tasks != null) {
            tasks.remove(task);
            if (tasks.isEmpty()) index.remove(key);
        }
    }

    private void enqueue(T task) {
        if (!blocked.containsKey(task) && !deferredUntil.containsKey(task) && queued.add(task)) {
            readyNormal.add(task);
        }
    }

    private void enqueueImmediate(T task) {
        if (!blocked.containsKey(task) && !deferredUntil.containsKey(task) && queued.add(task)) {
            readyImmediate.add(task);
        }
    }

    private void enqueueDeferred(T task) {
        if (!blocked.containsKey(task) && queued.add(task)) readyDeferred.add(task);
    }

    @Nullable
    private T pollReady() {
        T task;
        boolean hasOrdinary = !readyNormal.isEmpty() || !readyDeferred.isEmpty();
        if (!readyImmediate.isEmpty() && (!hasOrdinary || immediateBurst
                < Math.max(0, ECOFairReadyQueue.IMMEDIATE_BURST_LIMIT - 2))) {
            task = readyImmediate.poll();
            immediateBurst++;
            normalBurst = 0;
        } else if (!readyNormal.isEmpty() && (readyDeferred.isEmpty()
                || normalBurst < ECOFairReadyQueue.NORMAL_BURST_LIMIT)) {
            task = readyNormal.poll();
            normalBurst++;
            immediateBurst = 0;
        } else if (!readyDeferred.isEmpty()) {
            task = readyDeferred.poll();
            normalBurst = 0;
            immediateBurst = 0;
        } else {
            task = readyImmediate.poll();
            if (task != null) {
                immediateBurst++;
                normalBurst = 0;
            }
        }
        if (task != null) queued.remove(task);
        return task;
    }

    private void removeFromReady(T task) {
        queued.remove(task);
        removeIdentity(readyNormal, task);
        removeIdentity(readyImmediate, task);
        removeIdentity(readyDeferred, task);
    }

    private static <T> void removeIdentity(ArrayDeque<T> queue, T target) {
        for (var iterator = queue.iterator(); iterator.hasNext();) {
            if (iterator.next() == target) {
                iterator.remove();
                return;
            }
        }
    }

    public List<BlockedTask<T>> blockedTasksSnapshot() {
        List<BlockedTask<T>> result = new ArrayList<>();
        for (T task : jobOrder) {
            InputDependencies dependencies = blocked.get(task);
            if (dependencies != null) {
                result.add(new BlockedTask<>(task, dependencies,
                        blockedAtTicks.getOrDefault(task, currentTick)));
            }
        }
        return List.copyOf(result);
    }

    public static class InputDependencies {
        private final Set<AEKey> exactKeys;
        private final Set<net.minecraft.resources.ResourceLocation> fuzzyItemIds;
        private final boolean wakeOnAnyChange;

        public InputDependencies(Set<AEKey> exactKeys,
                Set<net.minecraft.resources.ResourceLocation> fuzzyItemIds,
                boolean wakeOnAnyChange) {
            this.exactKeys = Set.copyOf(exactKeys);
            this.fuzzyItemIds = Set.copyOf(fuzzyItemIds);
            this.wakeOnAnyChange = wakeOnAnyChange;
        }

        public Set<AEKey> exactKeys() { return exactKeys; }
        public Set<net.minecraft.resources.ResourceLocation> fuzzyItemIds() { return fuzzyItemIds; }
        public boolean wakeOnAnyChange() { return wakeOnAnyChange; }
        public boolean matches(AEKey key) { return wakeOnAnyChange || exactKeys.contains(key); }
        public boolean matchesFuzzy(net.minecraft.resources.ResourceLocation fuzzyItemId) {
            return wakeOnAnyChange || fuzzyItemIds.contains(fuzzyItemId);
        }
    }

    public record BlockedTask<T>(T task, InputDependencies dependencies, long blockedAtTick) { }
}
