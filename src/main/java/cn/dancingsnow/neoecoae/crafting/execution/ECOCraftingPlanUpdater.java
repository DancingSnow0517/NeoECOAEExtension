package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningImprovements;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import org.jetbrains.annotations.Nullable;

/** Server-thread handoff for bounded, asynchronous optimization of CPU-owned remaining work. */
final class ECOCraftingPlanUpdater {
    private static final int MAX_ATTEMPTS = 2;
    private final ECOCraftingCPULogic host;
    private @Nullable ExecutingCraftingJob owner;
    private @Nullable Future<ECOPlanningResult> search;
    private @Nullable Snapshot snapshot;
    private int attempts;
    private long nextAttempt;

    ECOCraftingPlanUpdater(ECOCraftingCPULogic host) { this.host = host; }

    void reset() {
        if (search != null) search.cancel(true);
        search = null;
        snapshot = null;
        owner = null;
        attempts = 0;
        nextAttempt = 0;
    }

    void tick(long tick) {
        var job = host.getJob();
        if (job != owner) { reset(); owner = job; }
        if (job == null || job.improvements == null) return;
        if (!job.improvements.isCurrent() || job.link.isCanceled()) { reset(); return; }
        if (search != null && search.isDone()) {
            try {
                if (snapshot != null) install(job, snapshot, search.get());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException ignored) {
                // Optional work never changes the live plan on failure.
            } finally {
                search = null;
                snapshot = null;
                nextAttempt = tick + 20;
            }
        }
        if (search != null || attempts >= MAX_ATTEMPTS || tick < nextAttempt || !quiescent(job)) return;
        snapshot = Snapshot.capture(job, host.getInventory().list);
        search = job.improvements.remaining(host.getInventory().list, job.remainingAmount);
        attempts++;
        if (search == null) { snapshot = null; nextAttempt = tick + 20; }
    }

    private boolean quiescent(ExecutingCraftingJob job) {
        return !job.exactOrder && !job.suspended && job.permanentExecutionError == null
                && !job.link.isCanceled() && !job.link.isDone()
                && job.remainingAmount > 0 && job.fuzzyPlanningItemIds.isEmpty()
                && job.waitingFor.list.isEmpty() && job.deferredStock.isEmpty() && job.deferredEmitted.isEmpty()
                && !host.outputDeliveryForPersistence().hasPendingFinalOutputs();
    }

    boolean install(ExecutingCraftingJob job, Snapshot expected, @Nullable ECOPlanningResult candidate) {
        if (host.getJob() != job || !quiescent(job) || !expected.matches(job, host.getInventory().list)
                || job.improvements == null || !job.improvements.isCurrent()
                || !ECOPlanningImprovements.executable(candidate)) return false;
        var plan = candidate.plan();
        if (!plan.finalOutput().equals(new GenericStack(job.finalOutput.what(), job.remainingAmount))
                || plan.bytes() > job.reservedBytes || !plan.emittedItems().isEmpty()
                || !plan.missingItems().isEmpty() || !candidate.fuzzyPlanningItemIds().equals(job.fuzzyPlanningItemIds)
                || ECOPlanningImprovements.firings(candidate).compareTo(expected.cost()) >= 0) return false;
        for (var used : plan.usedItems()) {
            if (used.getLongValue() < 0 || used.getLongValue() > expected.stock().getOrDefault(used.getKey(), 0L))
                return false;
        }
        ExecutingCraftingJob prepared;
        try {
            var contract = candidate.executionContract();
            if (!contract.executable() || !cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity
                    .matches(contract.planSignature(), plan)) return false;
            prepared = new ExecutingCraftingJob(plan, contract.executionPlan(),
                    host::postChange, job.link, job.playerId);
            if (!preservesSeeds(job, expected, prepared)) return false;
        } catch (RuntimeException unsupported) {
            return false;
        }
        if (!job.improvements.isCurrent()) return false;
        Set<AEKey> changed = new HashSet<>();
        job.tasks.keySet().forEach(pattern -> pattern.getOutputs().forEach(output -> changed.add(output.what())));
        prepared.tasks.keySet().forEach(pattern -> pattern.getOutputs().forEach(output -> changed.add(output.what())));
        job.replaceRemainingPlan(prepared);
        var scheduler = host.taskSchedulerForLifecycle();
        scheduler.reset();
        long tick = appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
        scheduler.bindDiagnostics(job.link.getCraftingID(), tick);
        scheduler.beginResolveTick(tick);
        changed.forEach(host::postChange);
        host.markCpuDirty();
        return true;
    }

    private static boolean preservesSeeds(ExecutingCraftingJob job, Snapshot expected, ExecutingCraftingJob prepared) {
        var before = finalStock(expected.stock(), expected.tasks(), job.finalOutput.what(), job.remainingAmount);
        var after = finalStock(expected.stock(), taskCounts(prepared), job.finalOutput.what(), job.remainingAmount);
        if (before == null || after == null || after.values().stream().anyMatch(amount -> amount.signum() < 0))
            return false;
        for (AEKey seed : job.seedKeys) {
            if (after.getOrDefault(seed, BigInteger.ZERO).compareTo(
                    before.getOrDefault(seed, BigInteger.ZERO).max(BigInteger.ZERO)) < 0) return false;
        }
        var newProtection = prepared.executionRuntime == null ? Map.<AEKey, Long>of()
                : prepared.executionRuntime.protectedStartupSeed(null);
        for (var seed : expected.protectedSeeds().entrySet()) {
            // A seed either remains assigned to the new schedule or is provably unused until final return.
            if (newProtection.getOrDefault(seed.getKey(), 0L) < seed.getValue()
                    && after.getOrDefault(seed.getKey(), BigInteger.ZERO)
                        .compareTo(BigInteger.valueOf(seed.getValue())) < 0) return false;
        }
        return true;
    }

    /** Conservative physical balance. Ambiguous substitutions and tool/container contracts keep the old plan. */
    private static @Nullable Map<AEKey, BigInteger> finalStock(Map<AEKey, Long> stock,
            Map<IPatternDetails, BigInteger> tasks, AEKey goal, long amount) {
        Map<AEKey, BigInteger> balance = new LinkedHashMap<>();
        stock.forEach((key, count) -> balance.put(key, BigInteger.valueOf(count)));
        for (var task : tasks.entrySet()) {
            for (var input : task.getKey().getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length != 1 || input.getMultiplier() <= 0 || possible[0].amount() <= 0
                        || input.getRemainingKey(possible[0].what()) != null) return null;
                var consumed = BigInteger.valueOf(possible[0].amount())
                        .multiply(BigInteger.valueOf(input.getMultiplier())).multiply(task.getValue());
                balance.merge(possible[0].what(), consumed.negate(), BigInteger::add);
            }
            for (var output : task.getKey().getOutputs())
                balance.merge(output.what(), BigInteger.valueOf(output.amount()).multiply(task.getValue()), BigInteger::add);
        }
        balance.merge(goal, BigInteger.valueOf(amount).negate(), BigInteger::add);
        return balance;
    }

    private static Map<IPatternDetails, BigInteger> taskCounts(ExecutingCraftingJob job) {
        Map<IPatternDetails, BigInteger> counts = new LinkedHashMap<>();
        job.tasks.forEach((pattern, progress) -> {
            if (progress.remainingExact().signum() > 0) counts.put(pattern, progress.remainingExact());
        });
        return Map.copyOf(counts);
    }

    record Snapshot(UUID jobId, long remaining, Map<AEKey, Long> stock, Map<IPatternDetails, BigInteger> tasks,
                    Map<AEKey, Long> protectedSeeds) {
        static Snapshot capture(ExecutingCraftingJob job, KeyCounter inventory) {
            Map<AEKey, Long> stock = new LinkedHashMap<>();
            for (var entry : inventory) if (entry.getLongValue() > 0) stock.put(entry.getKey(), entry.getLongValue());
            return new Snapshot(job.link.getCraftingID(), job.remainingAmount, Map.copyOf(stock), taskCounts(job),
                    job.executionRuntime == null ? Map.of() : Map.copyOf(job.executionRuntime.protectedStartupSeed(null)));
        }

        boolean matches(ExecutingCraftingJob job, KeyCounter inventory) { return equals(capture(job, inventory)); }
        BigInteger cost() { return tasks.values().stream().reduce(BigInteger.ZERO, BigInteger::add); }
    }
}
