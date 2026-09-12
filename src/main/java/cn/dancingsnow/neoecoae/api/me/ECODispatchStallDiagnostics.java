package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOPatternPushDiagnostics;

import java.util.EnumMap;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import cn.dancingsnow.neoecoae.config.NEConfig;

/**
 * Bounded, transient observations for a crafting job that has stopped making physical progress.
 * This class deliberately has no recovery or scheduler mutation behavior.
 */
final class ECODispatchStallDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.dispatch");
    private static final long INITIAL_REPORT_DELAY = 200L;
    private static final long REPORT_INTERVAL = 1200L;
    private static final int MAX_MISSING_SLOTS = 8;
    private static final int MAX_ALTERNATIVES_PER_SLOT = 8;
    private static final int MAX_FUZZY_TEMPLATES = 8;
    private static final int MAX_DETAIL_LENGTH = 4096;

    enum Cause {
        NO_CANDIDATES,
        PHASE_BARRIER,
        BUDGET,
        PROVIDER_BUSY,
        NO_READY_PROVIDER,
        MISSING_INPUTS,
        INSUFFICIENT_POWER,
        BATCH_REJECTED,
        PUSH_REJECTED,
        WAITING_FOR_OUTPUT,
        FINAL_DELIVERY_BLOCKED
    }

    private UUID jobId;
    private long lastProgressTick;
    private long lastReportTick;
    private boolean reported;
    private long probes;
    private long rejects;
    private long busy;
    private long providers;
    private long eligibleProviders;
    private int candidates;
    private int operationLimit;
    private int probeLimit;
    private long resolveStatsTick = Long.MIN_VALUE;
    private long resolveAttempts;
    private long resolveFailures;
    private long repeatedFailureSameEpoch;
    private final EnumMap<Cause, Long> causes = new EnumMap<>(Cause.class);
    private final EnumMap<ECOPatternPushDiagnostics.Reason, Long> pushReasons =
        new EnumMap<>(ECOPatternPushDiagnostics.Reason.class);
    private String lastRejection = "none";
    private String lastMissing = "none";
    private String lastBusy = "none";
    private String lastPower = "none";
    private String lastFinalDelivery = "none";

    void bind(UUID id, long tick) {
        if (!NEConfig.ecoDispatchWatchdogDebug) {
            if (jobId != null) reset();
            return;
        }
        if (!id.equals(jobId) || tick < lastProgressTick) {
            jobId = id;
            lastProgressTick = tick;
            lastReportTick = tick;
            reported = false;
            clearObservations();
        }
    }

    void reset() {
        jobId = null;
        lastProgressTick = 0L;
        lastReportTick = 0L;
        reported = false;
        resolveStatsTick = Long.MIN_VALUE;
        resolveAttempts = 0L;
        resolveFailures = 0L;
        repeatedFailureSameEpoch = 0L;
        clearObservations();
    }

    boolean isActive() {
        return jobId != null;
    }

    void progress(long tick) {
        if (jobId == null) return;
        lastProgressTick = tick;
        lastReportTick = tick;
        reported = false;
        clearObservations();
    }

    void beginDispatch(int operationLimit, int probeLimit) {
        if (jobId == null) return;
        this.operationLimit = operationLimit;
        this.probeLimit = probeLimit;
    }

    void beginResolveTick(long tick) {
        if (jobId == null || resolveStatsTick == tick) return;
        resolveStatsTick = tick;
        resolveAttempts = 0L;
        resolveFailures = 0L;
        repeatedFailureSameEpoch = 0L;
    }

    void resolveAttempt() {
        if (jobId != null) resolveAttempts = saturatingIncrement(resolveAttempts);
    }

    void resolveFailure() {
        if (jobId != null) resolveFailures = saturatingIncrement(resolveFailures);
    }

    void repeatedFailureSameEpoch() {
        if (jobId != null) repeatedFailureSameEpoch = saturatingIncrement(repeatedFailureSameEpoch);
    }

    void finishResolveTick() {
        if (jobId == null || (resolveFailures == 0L && repeatedFailureSameEpoch == 0L)) return;
        LOGGER.info("[ECO Dispatch Resolve] tick={} resolveAttempts={} resolveFailures={} repeatedFailureSameEpoch={}",
            resolveStatsTick, resolveAttempts, resolveFailures, repeatedFailureSameEpoch);
    }

    void candidates(int count) {
        if (jobId == null) return;
        candidates = Math.max(0, count);
    }

    void noCandidates(boolean phaseBarrier) {
        if (jobId == null) return;
        cause(phaseBarrier ? Cause.PHASE_BARRIER : Cause.NO_CANDIDATES);
    }

    void phaseBarrier() {
        if (jobId == null) return;
        cause(Cause.PHASE_BARRIER);
    }

    void budget() {
        if (jobId == null) return;
        cause(Cause.BUDGET);
    }

    void providerConsidered(boolean eligible) {
        if (jobId == null) return;
        providers = saturatingIncrement(providers);
        if (eligible) eligibleProviders = saturatingIncrement(eligibleProviders);
        else cause(Cause.BUDGET);
    }

    void provider(IPatternDetails pattern, ICraftingProvider provider, boolean isBusy) {
        if (jobId == null) return;
        if (!isBusy) return;
        busy = saturatingIncrement(busy);
        cause(Cause.PROVIDER_BUSY);
        if ("none".equals(lastBusy)) lastBusy = describeProvider(pattern, provider, true);
    }

    void noReadyProvider() {
        if (jobId == null) return;
        cause(Cause.NO_READY_PROVIDER);
    }

    void probe() {
        if (jobId == null) return;
        probes = saturatingIncrement(probes);
    }

    void missingInputs(IPatternDetails pattern, ICraftingInventory inventory) {
        if (jobId == null) return;
        cause(Cause.MISSING_INPUTS);
        try {
            var text = new StringBuilder("pattern=").append(compact(pattern.getDefinition()))
                .append(" slots=[");
            var inputs = pattern.getInputs();
            int slotLimit = Math.min(inputs.length, MAX_MISSING_SLOTS);
            for (int slot = 0; slot < slotLimit; slot++) {
                if (slot > 0) text.append(", ");
                var input = inputs[slot];
                text.append("slot=").append(slot).append('{');
                if (input == null || input.getPossibleInputs() == null) {
                    text.append("unavailable}");
                    continue;
                }
                var alternatives = input.getPossibleInputs();
                int alternativeLimit = Math.min(alternatives.length, MAX_ALTERNATIVES_PER_SLOT);
                boolean satisfied = false;
                for (int alternative = 0; alternative < alternativeLimit; alternative++) {
                    if (alternative > 0) text.append("; ");
                    var possible = alternatives[alternative];
                    if (possible == null || possible.what() == null) {
                        text.append("alternative=").append(alternative).append(" unavailable");
                        continue;
                    }
                    long need = multiply(input.getMultiplier(), possible.amount());
                    long available = inventory.extract(possible.what(), Long.MAX_VALUE, Actionable.SIMULATE);
                    long missing = Math.max(0L, need - available);
                    satisfied |= available >= need;
                    text.append("candidate=").append(compact(possible.what()))
                        .append(" need=").append(need)
                        .append(" available=").append(available)
                        .append(" missing=").append(missing);
                    if (missing > 0L) appendFuzzyTemplates(text, inventory, possible.what());
                }
                if (alternatives.length > alternativeLimit) {
                    text.append("; ... ").append(alternatives.length - alternativeLimit).append(" alternatives omitted");
                }
                text.append(" status=").append(satisfied ? "ok" : "MISSING").append('}');
            }
            if (inputs.length > slotLimit) {
                text.append(", ... ").append(inputs.length - slotLimit).append(" slots omitted");
            }
            lastMissing = limit(text.append(']').toString());
        } catch (RuntimeException failure) {
            lastMissing = "pattern=" + compact(pattern.getDefinition()) + " diagnostics=unavailable";
        }
    }

    void insufficientPower(double required, double available) {
        if (jobId == null) return;
        cause(Cause.INSUFFICIENT_POWER);
        lastPower = "requiredPower=" + required + " availablePower=" + available;
    }

    void batchRejected(IPatternDetails pattern, ICraftingProvider provider) {
        if (jobId == null) return;
        rejects = saturatingIncrement(rejects);
        cause(Cause.BATCH_REJECTED);
        lastRejection = "pattern=" + compact(pattern.getDefinition())
            + " provider=" + provider.getClass().getName() + " path=BATCH result=REJECT";
    }

    void pushRejected(IPatternDetails pattern, ICraftingProvider provider) {
        if (jobId == null) return;
        rejects = saturatingIncrement(rejects);
        cause(Cause.PUSH_REJECTED);
        lastRejection = describeProvider(pattern, provider, false);
    }

    void finalDeliveryBlocked(AEKey key, long amount) {
        if (jobId == null) return;
        cause(Cause.FINAL_DELIVERY_BLOCKED);
        lastFinalDelivery = "final=" + compact(key) + " amount=" + amount + " inserted=0";
    }

    void check(long tick, ExecutingCraftingJob job) {
        if (!NEConfig.ecoDispatchWatchdogDebug) {
            if (jobId != null) reset();
            return;
        }
        if (jobId == null || job == null || !jobId.equals(job.link.getCraftingID())) return;
        if (!job.waitingFor.list.isEmpty()) cause(Cause.WAITING_FOR_OUTPUT);
        long stalledTicks = Math.max(0L, tick - lastProgressTick);
        if (stalledTicks < INITIAL_REPORT_DELAY || (reported && tick - lastReportTick < REPORT_INTERVAL)) return;
        lastReportTick = tick;
        reported = true;

        long pendingTasks = 0L;
        for (var task : job.tasks.values()) {
            pendingTasks = saturatingAdd(pendingTasks, Math.max(0L, task.value));
        }
        int waitingTypes = 0;
        var waiting = new java.util.ArrayList<WaitingEntry>();
        for (var entry : job.waitingFor.list) {
            if (entry.getLongValue() <= 0L) continue;
            waitingTypes++;
            waiting.add(new WaitingEntry(entry.getKey(), entry.getLongValue()));
        }
        waiting.sort((left, right) -> Long.compare(right.amount(), left.amount()));
        var waitingText = new StringBuilder("[");
        for (int i = 0; i < Math.min(3, waiting.size()); i++) {
            if (i > 0) waitingText.append(", ");
            waitingText.append(compact(waiting.get(i).key())).append('=').append(waiting.get(i).amount());
        }
        if (waiting.size() > 3) waitingText.append(", ...");
        waitingText.append(']');
        String runtimeState = "none";
        if (job.executionRuntime != null) {
            try {
                runtimeState = job.executionRuntime.describeStallState();
            } catch (RuntimeException failure) {
                runtimeState = "diagnostics=unavailable exception=" + failure.getClass().getName();
            }
        }

        LOGGER.warn("[ECO Dispatch Stall]\njob={}\nstalledTicks={}\npendingTasks={}\nwaitingTypes={}"
                + "\nremainingOutput={}\nruntime={}\ncandidates={}\noperationLimit={}\nprobeLimit={}"
                + "\nproviders={}\neligible={}\nprobes={}\naccepted=0\nrejects={}\nbusy={}\ncauses={}"
                + "\npushReasons={}\nwaiting={}\nlastMissing=[{}]\nlastReject=[{}]\nlastBusy=[{}]"
                + "\nlastPower=[{}]\nlastFinalDelivery=[{}]\nruntimeState=[{}]",
            jobId, stalledTicks, pendingTasks, waitingTypes, job.remainingAmount,
            job.executionRuntime != null, candidates, operationLimit, probeLimit,
            providers, eligibleProviders, probes, rejects, busy, causes,
            pushReasons, waitingText, lastMissing, lastRejection, lastBusy, lastPower, lastFinalDelivery,
            runtimeState);
    }

    private void cause(Cause cause) {
        causes.merge(cause, 1L, ECODispatchStallDiagnostics::saturatingAdd);
    }

    private String describeProvider(IPatternDetails pattern, ICraftingProvider provider, boolean ignoreSnapshotReasons) {
        try {
            String prefix = "pattern=" + compact(pattern.getDefinition())
                + " provider=" + provider.getClass().getName();
            if (provider instanceof ECOPatternPushDiagnostics diagnostics) {
                var snapshot = diagnostics.neoecoae$getPushDiagnostics();
                if (!ignoreSnapshotReasons) recordPushReasons(snapshot.reasons());
                return prefix + " location=" + snapshot.location()
                    + " reasons=" + (ignoreSnapshotReasons ? "unavailable" : snapshot.reasons())
                    + " bufferedStacks=" + snapshot.bufferedStacks()
                    + " sendDirection=" + snapshot.sendDirection();
            }
            if (!ignoreSnapshotReasons) recordPushReasons(java.util.Set.of());
            return prefix + " diagnostics=unsupported";
        } catch (RuntimeException failure) {
            if (!ignoreSnapshotReasons) recordPushReasons(java.util.Set.of());
            return "provider=" + provider.getClass().getName() + " diagnostics=unavailable";
        }
    }

    private void recordPushReasons(java.util.Set<ECOPatternPushDiagnostics.Reason> observed) {
        if (observed.isEmpty()) {
            pushReasons.merge(ECOPatternPushDiagnostics.Reason.UNKNOWN, 1L,
                ECODispatchStallDiagnostics::saturatingAdd);
            return;
        }
        for (var reason : observed) {
            pushReasons.merge(reason, 1L, ECODispatchStallDiagnostics::saturatingAdd);
        }
    }

    private static void appendFuzzyTemplates(StringBuilder text, ICraftingInventory inventory, AEKey key) {
        text.append(" fuzzy=[");
        int count = 0;
        for (var candidate : inventory.findFuzzyTemplates(key)) {
            if (count > 0) text.append(',');
            if (count++ >= MAX_FUZZY_TEMPLATES) {
                text.append("...");
                break;
            }
            text.append(compact(candidate));
        }
        text.append(']');
    }

    private void clearObservations() {
        probes = 0L;
        rejects = 0L;
        busy = 0L;
        providers = 0L;
        eligibleProviders = 0L;
        candidates = 0;
        operationLimit = 0;
        probeLimit = 0;
        causes.clear();
        pushReasons.clear();
        lastRejection = "none";
        lastMissing = "none";
        lastBusy = "none";
        lastPower = "none";
        lastFinalDelivery = "none";
    }

    private static String compact(@Nullable Object value) {
        String text = String.valueOf(value);
        return text.length() <= 512 ? text : text.substring(0, 512) + "...";
    }

    private static String limit(String text) {
        return text.length() <= MAX_DETAIL_LENGTH ? text : text.substring(0, MAX_DETAIL_LENGTH) + "...";
    }

    private static long multiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatingIncrement(long value) {
        return value == Long.MAX_VALUE ? value : value + 1L;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return left;
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record WaitingEntry(AEKey key, long amount) {}
}
