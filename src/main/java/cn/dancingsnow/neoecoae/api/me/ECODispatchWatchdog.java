package cn.dancingsnow.neoecoae.api.me;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Transient observations only. No timeout here establishes ownership of external machine inputs. */
final class ECODispatchWatchdog {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.dispatch");
    private static final long STALL_TICKS = 200;
    private static final long REPORT_INTERVAL = 1200;
    enum State { NO_PROBE, REJECTING, PROVIDER_BUSY, ACCEPTED_WAITING }
    enum Skip { NO_READY_PROVIDER, MISSING_INPUTS, INSUFFICIENT_POWER, BUDGET, PHASE_BARRIER, FINAL_DELIVERY }

    private UUID jobId;
    private long lastProgressTick;
    private long lastReportTick;
    private long lastRejectLogTick;
    private boolean resynced;
    private long probes;
    private long rejected;
    private long busy;
    private final EnumMap<Skip, Long> skipped = new EnumMap<>(Skip.class);
    private final EnumMap<ECOPatternPushDiagnostics.Reason, Long> reasons =
        new EnumMap<>(ECOPatternPushDiagnostics.Reason.class);
    private String lastRejection = "none";
    private String lastBusy = "none";
    private String lastMissing = "none";
    private String lastGlass = "none";
    private boolean glassLedgerActive;
    private long planUsedGlass;
    private long planUsedGlassFamily;
    private long cpuAfterInitialExtractionGlass;
    private long cpuAfterInitialExtractionGlassFamily;
    private long totalPatternGlassDemand;
    private long totalPatternGlassDemandFamily;
    private long plannedPatternGlassSupply;
    private long plannedPatternGlassSupplyFamily;
    private long emittedGlassSupply;
    private long emittedGlassSupplyFamily;
    private long initialSeedGlass;
    private long initialSeedGlassFamily;
    private long acceptedInputGlass;
    private long acceptedInputGlassFamily;
    private long acceptedPatternGlassSupply;
    private long acceptedPatternGlassSupplyFamily;

    void bind(UUID id, long tick) {
        if (!id.equals(jobId) || tick < lastProgressTick) {
            jobId = id;
            lastRejectLogTick = tick - STALL_TICKS;
            progress(tick);
        }
    }

    void reset() {
        jobId = null;
        clearObservations();
        clearGlassLedger();
    }

    void progress(long tick) {
        lastProgressTick = tick;
        lastReportTick = tick;
        resynced = false;
        clearObservations();
    }

    private void clearObservations() {
        probes = rejected = busy = 0;
        skipped.clear();
        reasons.clear();
        lastRejection = lastBusy = lastMissing = lastGlass = "none";
    }

    private void clearGlassLedger() {
        glassLedgerActive = false;
        planUsedGlass = planUsedGlassFamily = 0L;
        cpuAfterInitialExtractionGlass = cpuAfterInitialExtractionGlassFamily = 0L;
        totalPatternGlassDemand = totalPatternGlassDemandFamily = 0L;
        plannedPatternGlassSupply = plannedPatternGlassSupplyFamily = 0L;
        emittedGlassSupply = emittedGlassSupplyFamily = 0L;
        initialSeedGlass = initialSeedGlassFamily = 0L;
        acceptedInputGlass = acceptedInputGlassFamily = 0L;
        acceptedPatternGlassSupply = acceptedPatternGlassSupplyFamily = 0L;
    }

    void capturePlan(ICraftingPlan plan, @Nullable ECOExecutionPlan executionPlan) {
        clearGlassLedger();
        glassLedgerActive = true;
        try {
            for (var entry : plan.usedItems()) {
            planUsedGlass = addGlass(planUsedGlass, entry.getKey(), entry.getLongValue(), false);
            planUsedGlassFamily = addGlass(planUsedGlassFamily, entry.getKey(), entry.getLongValue(), true);
            }
            for (var entry : plan.emittedItems()) {
            emittedGlassSupply = addGlass(emittedGlassSupply, entry.getKey(), entry.getLongValue(), false);
            emittedGlassSupplyFamily = addGlass(emittedGlassSupplyFamily, entry.getKey(), entry.getLongValue(), true);
            }
            for (var entry : plan.patternTimes().entrySet()) {
            long times = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
            if (times == 0L) continue;
            for (var input : entry.getKey().getInputs()) {
                if (input == null || input.getPossibleInputs() == null || input.getPossibleInputs().length == 0
                        || input.getPossibleInputs()[0] == null) continue;
                GenericStack primary = input.getPossibleInputs()[0];
                long amount = multiply(primary.amount(), multiply(input.getMultiplier(), times));
                totalPatternGlassDemand = addGlass(totalPatternGlassDemand, primary.what(), amount, false);
                totalPatternGlassDemandFamily = addGlass(totalPatternGlassDemandFamily, primary.what(), amount, true);
            }
            for (var output : entry.getKey().getOutputs()) {
                long amount = multiply(output.amount(), times);
                plannedPatternGlassSupply = addGlass(plannedPatternGlassSupply, output.what(), amount, false);
                plannedPatternGlassSupplyFamily = addGlass(plannedPatternGlassSupplyFamily, output.what(), amount, true);
            }
            }
            if (executionPlan != null) {
            for (var phase : executionPlan.phases()) {
                for (var entry : phase.initialSeed().entrySet()) {
                    initialSeedGlass = addGlass(initialSeedGlass, entry.getKey(), entry.getValue(), false);
                    initialSeedGlassFamily = addGlass(initialSeedGlassFamily, entry.getKey(), entry.getValue(), true);
                }
            }
            }
        } catch (RuntimeException failure) {
            LOGGER.debug("Unable to capture the complete ECO glass ledger", failure);
        }
    }

    void captureCpuInventory(ListCraftingInventory inventory) {
        if (!glassLedgerActive) return;
        cpuAfterInitialExtractionGlass = glassAmount(inventory.list, false);
        cpuAfterInitialExtractionGlassFamily = glassAmount(inventory.list, true);
    }

    void accepted(IPatternDetails pattern, KeyCounter[] inputs, long count) {
        if (!glassLedgerActive || count <= 0L) return;
        long inputGlass = glassAmount(inputs, false);
        long inputGlassFamily = glassAmount(inputs, true);
        acceptedInputGlass = saturatingAdd(acceptedInputGlass, multiply(inputGlass, count));
        acceptedInputGlassFamily = saturatingAdd(acceptedInputGlassFamily, multiply(inputGlassFamily, count));
        for (var output : pattern.getOutputs()) {
            long amount = multiply(output.amount(), count);
            acceptedPatternGlassSupply = addGlass(acceptedPatternGlassSupply, output.what(), amount, false);
            acceptedPatternGlassSupplyFamily = addGlass(acceptedPatternGlassSupplyFamily, output.what(), amount, true);
        }
    }

    void logSubmission(UUID id) {
        if (!glassLedgerActive) return;
        LOGGER.info("[ECO Glass Ledger] job={} planUsedGlass={} cpuAfterInitialExtractionGlass={}"
                + " totalPatternGlassDemand={} plannedPatternGlassSupply={} emittedGlassSupply={} initialSeedGlass={}"
                + " familyPlanUsedGlass={} familyCpuAfterInitialExtractionGlass={} familyDemand={} familySupply={}"
                + " familyEmitted={} familySeed={}", id, planUsedGlass, cpuAfterInitialExtractionGlass,
            totalPatternGlassDemand, plannedPatternGlassSupply, emittedGlassSupply, initialSeedGlass,
            planUsedGlassFamily, cpuAfterInitialExtractionGlassFamily, totalPatternGlassDemandFamily,
            plannedPatternGlassSupplyFamily, emittedGlassSupplyFamily, initialSeedGlassFamily);
    }

    String describeGlassLedger(@Nullable ExecutingCraftingJob job, ListCraftingInventory inventory) {
        if (!glassLedgerActive) return "inactive";
        long remainingDemand = 0L;
        long remainingProducer = 0L;
        if (job != null) {
            for (var entry : job.tasks.entrySet()) {
                long count = Math.max(0L, entry.getValue().value);
                for (var input : entry.getKey().getInputs()) {
                    if (input == null || input.getPossibleInputs() == null || input.getPossibleInputs().length == 0
                            || input.getPossibleInputs()[0] == null) continue;
                    GenericStack primary = input.getPossibleInputs()[0];
                    long amount = multiply(primary.amount(), multiply(input.getMultiplier(), count));
                    remainingDemand = addGlass(remainingDemand, primary.what(), amount, false);
                }
                for (var output : entry.getKey().getOutputs()) {
                    remainingProducer = addGlass(remainingProducer, output.what(),
                        multiply(output.amount(), count), false);
                }
            }
        }
        return "planUsedGlass=" + planUsedGlass + ",cpuAfterInitialExtractionGlass="
            + cpuAfterInitialExtractionGlass + ",totalPatternGlassDemand=" + totalPatternGlassDemand
            + ",plannedPatternGlassSupply=" + plannedPatternGlassSupply + ",emittedGlassSupply="
            + emittedGlassSupply + ",initialSeedGlass=" + initialSeedGlass + ",acceptedInputGlass="
            + acceptedInputGlass + ",acceptedPatternGlassSupply=" + acceptedPatternGlassSupply
            + ",remainingTaskGlassDemand=" + remainingDemand + ",remainingTaskGlassProducer=" + remainingProducer
            + ",cpuInventoryGlass=" + glassAmount(inventory.list, false) + ",waitingGlass="
            + (job == null ? 0L : glassAmount(job.waitingFor.list, false));
    }

    void skip(Skip reason) { skipped.merge(reason, 1L, Long::sum); }

    void provider(IPatternDetails pattern, ICraftingProvider provider, boolean isBusy) {
        if (!isBusy) return;
        busy++;
        // Keep only one bounded sample; do not turn a large provider list into retained diagnostic state.
        if (lastBusy.equals("none")) lastBusy = describe(pattern, provider, true);
    }

    void probe() { probes++; }

    void missingInputs(IPatternDetails pattern, ICraftingInventory inventory, Level level) {
        try {
            var text = new StringBuilder("pattern=").append(pattern.getDefinition()).append(" slots=");
            var inputs = pattern.getInputs();
            for (int slot = 0; slot < inputs.length; slot++) {
                var input = inputs[slot];
                long required = input.getMultiplier();
                boolean satisfied = false;
                text.append(slot).append('[');
                for (var possible : input.getPossibleInputs()) {
                    long available = inventory.extract(possible.what(), Long.MAX_VALUE, appeng.api.config.Actionable.SIMULATE);
                    long need = required * possible.amount();
                    long missing = Math.max(0L, need - available);
                    text.append(possible.what()).append(" need=").append(need)
                        .append(" available=").append(available).append(" missing=").append(missing).append(';');
                    if (available >= need) satisfied = true;
                    if (missing > 0 && isGlass(possible.what())) {
                        var fuzzy = new StringBuilder();
                        int count = 0;
                        for (var key : inventory.findFuzzyTemplates(possible.what())) {
                            if (count++ > 0) fuzzy.append(',');
                            if (count > 12) { fuzzy.append("..."); break; }
                            fuzzy.append(key);
                        }
                        lastGlass = "slot=" + slot + " required=" + possible.what()
                            + " need=" + need + " available=" + available + " missing=" + missing
                            + " fuzzy=[" + fuzzy + "]";
                    }
                }
                text.append(satisfied ? "ok" : "MISSING").append(']');
            }
            lastMissing = compact(text);
        } catch (RuntimeException failure) {
            lastMissing = "pattern=" + pattern.getDefinition() + " diagnostics=unavailable";
        }
    }

    void rejected(IPatternDetails pattern, ICraftingProvider provider, long tick) {
        rejected++;
        lastRejection = describe(pattern, provider, false);
        if (LOGGER.isDebugEnabled() && tick - lastRejectLogTick >= STALL_TICKS) {
            lastRejectLogTick = tick;
            LOGGER.debug("[ECO Dispatch] job={} result=REJECT {}", jobId, lastRejection);
        }
    }

    void batchRejected(ICraftingProvider provider) {
        rejected++;
        // Atomic batch rejection is not an AE2 pushPattern result; never read a stale ordinary snapshot.
        lastRejection = "provider=" + provider.getClass().getName() + " path=BATCH result=REJECT";
    }

    private String describe(IPatternDetails pattern, ICraftingProvider provider, boolean busySample) {
        // A diagnostic extension must not change dispatch or input rollback if it cannot supply a snapshot.
        try {
            var definition = pattern.getDefinition();
            String prefix = "pattern=" + compact(definition == null ? pattern.getClass().getName() : definition)
                + " provider=" + provider.getClass().getName();
            if (provider instanceof ECOPatternPushDiagnostics diagnostics) {
                var snapshot = diagnostics.neoecoae$getPushDiagnostics();
                var observed = busySample
                    ? (snapshot.bufferedStacks() > 0
                        ? java.util.Set.of(ECOPatternPushDiagnostics.Reason.SEND_LIST_BUSY)
                        : java.util.Set.of(ECOPatternPushDiagnostics.Reason.UNKNOWN))
                    : snapshot.reasons();
                if (!busySample) {
                    if (observed.isEmpty()) reasons.merge(ECOPatternPushDiagnostics.Reason.UNKNOWN, 1L, Long::sum);
                    else for (var reason : observed) reasons.merge(reason, 1L, Long::sum);
                }
                return prefix + " location=" + snapshot.location() + " reason=" + observed
                    + " sendListStacks=" + snapshot.bufferedStacks() + " sendDirection=" + snapshot.sendDirection();
            }
            if (!busySample) reasons.merge(ECOPatternPushDiagnostics.Reason.UNKNOWN, 1L, Long::sum);
            return prefix + " reason=UNKNOWN";
        } catch (RuntimeException failure) {
            return "provider=" + provider.getClass().getName() + " diagnostics=unavailable";
        }
    }

    private static String compact(Object value) {
        String text = String.valueOf(value);
        return text.length() <= 512 ? text : text.substring(0, 512) + "...";
    }

    private static long glassAmount(KeyCounter counter, boolean family) {
        long result = 0L;
        for (var entry : counter) {
            result = addGlass(result, entry.getKey(), entry.getLongValue(), family);
        }
        return result;
    }

    private static long glassAmount(KeyCounter[] counters, boolean family) {
        long result = 0L;
        if (counters == null) return result;
        for (var counter : counters) if (counter != null) result = saturatingAdd(result, glassAmount(counter, family));
        return result;
    }

    private static long addGlass(long current, appeng.api.stacks.AEKey key, long amount, boolean family) {
        if (amount <= 0L || (family ? !isGlass(key) : !isExactGlass(key))) return current;
        return saturatingAdd(current, amount);
    }

    private static long multiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return left;
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    /** Returns a one-shot request to invalidate scheduler caches, never the material ledger. */
    boolean check(long tick, boolean pendingTasks, KeyCounter waiting, long remainingOutput, String glassLedger) {
        if (jobId == null || (!pendingTasks && waiting.isEmpty() && remainingOutput <= 0)) return false;
        if (tick - lastProgressTick < STALL_TICKS) return false;
        boolean softResync = pendingTasks && !resynced;
        boolean firstReport = lastReportTick == lastProgressTick;
        if (!firstReport && tick - lastReportTick < REPORT_INTERVAL) return false;
        lastReportTick = tick;
        var states = EnumSet.noneOf(State.class);
        if (rejected > 0) states.add(State.REJECTING);
        if (busy > 0) states.add(State.PROVIDER_BUSY);
        if (!waiting.isEmpty()) states.add(State.ACCEPTED_WAITING);
        if (pendingTasks && probes == 0 && busy == 0) states.add(State.NO_PROBE);
        if (states.isEmpty()) states.add(State.NO_PROBE);
        StringBuilder outputs = new StringBuilder();
        int sampled = 0;
        for (var output : waiting) {
            if (sampled++ == 3) { outputs.append(" ..."); break; }
            outputs.append(' ').append(compact(output.getKey())).append('=').append(output.getLongValue());
        }
        LOGGER.warn("[ECO Dispatch] job={} states={} stalledTicks={} pendingTasks={} remainingOutput={}"
                + " probes={} rejects={} busyObservations={} skips={} reasons={} waitingSample=[{}]"
            + " lastReject=[{}] lastBusy=[{}] lastMissing=[{}] lastGlass=[{}] glassLedger=[{}] action={} ",
            jobId, states, tick - lastProgressTick, pendingTasks, remainingOutput, probes, rejected, busy,
            skipped, reasons, outputs, lastRejection, lastBusy, lastMissing, lastGlass, compact(glassLedger),
            softResync ? "SOFT_RESYNC" : "DIAGNOSTICS_ONLY");
        if (softResync) resynced = true;
        // Subsequent reports describe new observations, rather than permanently retaining an old busy/reject sample.
        clearObservations();
        return softResync;
    }

    private static boolean isGlass(appeng.api.stacks.AEKey key) {
        try {
            return String.valueOf(key.getId()).toLowerCase(Locale.ROOT).contains("glass");
        } catch (RuntimeException failure) {
            return String.valueOf(key).toLowerCase(Locale.ROOT).contains("glass");
        }
    }

    private static boolean isExactGlass(appeng.api.stacks.AEKey key) {
        try {
            return "minecraft:glass".equals(String.valueOf(key.getId()));
        } catch (RuntimeException failure) {
            return "minecraft:glass".equals(String.valueOf(key));
        }
    }
}
