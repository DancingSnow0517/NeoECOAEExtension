package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimRequest;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimResult;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns CPU output claims, delivery retries and the dynamic final-output buffer. */
final class ECOCraftingOutputDelivery {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOCraftingCPULogic.class);

    private final ECOCraftingCPULogic host;
    private final Map<AEKey, Long> pendingFinalOutputs = new LinkedHashMap<>();
    private boolean deliveringFinalOutput;

    ECOCraftingOutputDelivery(ECOCraftingCPULogic host) {
        this.host = host;
    }

    void clearPendingFinalOutputs() {
        pendingFinalOutputs.clear();
    }

    boolean hasPendingFinalOutputs() {
        return !pendingFinalOutputs.isEmpty();
    }

    void deliverStoredFinalOutput() {
        var current = host.getJob();
        if (current == null) {
            return;
        }
        deliverPendingFinalOutputs(current);
        if (host.getJob() != current || current.finalOutput == null) {
            return;
        }
        AEKey key = current.finalOutput.what();
        long storedFinalOutput = host.getInventory().list.get(key);
        if (storedFinalOutput > 0L) {
            PlannerAmount reserve = PlannerAmount.ZERO;
            for (var task : current.tasks.entrySet()) {
                reserve = reserve.add(ECOPhaseScheduler.growingPatternFeedbackReserveExact(
                    task.getKey(), task.getValue().value, key));
            }
            if (current.executionRuntime != null) {
                reserve = reserve.max(PlannerAmount.of(current.executionRuntime.reservedInputAmount(key)));
            }
            // Keep material reserved for the next growth phase, then deliver only the excess final output.
            PlannerAmount deliverable = PlannerAmount.of(storedFinalOutput)
                .subtract(reserve).max(PlannerAmount.ZERO);
            long amount = deliverable.min(PlannerAmount.of(Math.max(0L, current.remainingAmount))).longValueExact();
            if (amount > 0L) {
                long inserted = deliverFinalOutputToDestination(current, key, amount);
                host.getInventory().extract(key, inserted, Actionable.MODULATE);
                current.remainingAmount = Math.max(0L, current.remainingAmount - inserted);
                host.markCpuDirty();
                if (inserted > 0L) {
                    host.taskSchedulerForOutput().progress(appeng.hooks.ticking.TickHandler.instance().getCurrentTick());
                } else {
                    host.taskSchedulerForOutput().finalDeliveryBlocked(key, amount);
                }
            }
        }
        boolean tasksDone = !host.taskSchedulerForOutput().hasPendingTasks(current);
        boolean physicallyComplete = current.remainingAmount <= 0L
                && current.waitingFor.list.isEmpty()
                && tasksDone
                && pendingFinalOutputs.isEmpty();
        if (physicallyComplete) {
            if (current.executionRuntime != null && !current.executionRuntime.isComplete()) {
                LOGGER.warn("ECO crafting job {} reached terminal crafting state but execution runtime is incomplete; "
                                + "forcing finalization", current.link.getCraftingID());
            }
            host.finishJob(true);
        }
    }

    long insert(AEKey what, long amount, Actionable type) {
        return insert(what, amount, type, null);
    }

    long insert(AEKey what, long amount, Actionable type, @Nullable Long preflightAccepted) {
        return insert(what, amount, type, preflightAccepted, null);
    }

    long insert(AEKey what, long amount, Actionable type, @Nullable Long preflightAccepted,
            @Nullable Boolean preflightMatchesFinalOutput) {
        var current = host.getJob();
        if (what == null || amount <= 0L || current == null) {
            return 0L;
        }
        if (ECOCraftingJobLifecycle.isTerminated(host.cpu.getLevel(), current.link.getCraftingID())) {
            return 0L;
        }
        boolean matchesFinalOutput = preflightMatchesFinalOutput != null
                ? preflightMatchesFinalOutput
                : current.finalOutput != null && what.matches(current.finalOutput);
        if (deliveringFinalOutput && matchesFinalOutput) {
            return 0L;
        }
        long accepted = preflightAccepted != null
                ? preflightAccepted
                : current.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (accepted <= 0L) {
            return 0L;
        }
        if (type == Actionable.MODULATE) {
            host.getInventory().insert(what, accepted, Actionable.MODULATE);
            host.taskSchedulerForOutput().recordPhysicalInsert(what);
            current.waitingFor.extract(what, accepted, Actionable.MODULATE);
            host.recordCompletedCraftingWork(accepted, what.getType());
            host.markCpuDirty();
            host.taskSchedulerForOutput().progress(appeng.hooks.ticking.TickHandler.instance().getCurrentTick());
        }
        return accepted;
    }

    long insertForJob(UUID craftingJobId, AEKey what, long amount, Actionable type) {
        var current = host.getJob();
        if (what == null || amount <= 0L || craftingJobId == null || current == null
                || !craftingJobId.equals(current.link.getCraftingID())
                || ECOCraftingJobLifecycle.isTerminated(host.cpu.getLevel(), craftingJobId)) {
            return 0L;
        }
        long accepted = insert(what, amount, type);
        if (accepted < 0L || accepted > amount) {
            throw new IllegalStateException("Invalid CPU insertion amount: " + accepted + " for " + amount);
        }
        // Directed output belongs to this CPU, but must not consume unrelated waiting entries.
        if (type == Actionable.MODULATE && accepted < amount) {
            host.getInventory().insert(what, amount - accepted, Actionable.MODULATE);
            host.taskSchedulerForOutput().recordPhysicalInsert(what);
            host.markCpuDirty();
        }
        return amount;
    }

    ECOCraftingOutputClaimResult claimCraftingOutput(ECOCraftingOutputClaimRequest request) {
        long requested = request == null ? 0L : Math.max(0L, request.amount());
        if (request == null || request.craftingJobId() == null || request.expectedKey() == null
                || request.actualKey() == null || request.amount() <= 0L || request.mode() == null) {
            return claimResult(ECOCraftingOutputClaimResult.Status.INVALID_REQUEST, requested, 0L, 0L);
        }

        var current = host.getJob();
        if (current == null) {
            return claimResult(ECOCraftingOutputClaimResult.Status.NO_JOB, requested, 0L, 0L);
        }
        if (!request.craftingJobId().equals(current.link.getCraftingID())
                || ECOCraftingJobLifecycle.isTerminated(host.cpu.getLevel(), request.craftingJobId())) {
            return claimResult(ECOCraftingOutputClaimResult.Status.TERMINAL, requested, 0L,
                    Math.max(0L, current.remainingAmount));
        }

        long claim = current.waitingFor.extract(
                request.expectedKey(), request.amount(), Actionable.SIMULATE);
        if (claim <= 0L) {
            return claimResult(ECOCraftingOutputClaimResult.Status.NO_MATCH, requested, 0L,
                    Math.max(0L, current.remainingAmount));
        }

        boolean finalOutputClaim = current.finalOutput != null
                && request.expectedKey().equals(current.finalOutput.what());
        if (request.mode() == Actionable.SIMULATE) {
            OutputRoute route = routeClaimedOutput(
                    current, request.actualKey(), claim, request.mode(), finalOutputClaim);
            long remaining = finalOutputClaim
                    ? Math.max(0L, current.remainingAmount - route.deliveredAmount())
                    : Math.max(0L, current.remainingAmount);
            return new ECOCraftingOutputClaimResult(
                    ECOCraftingOutputClaimResult.Status.ACCEPTED,
                    requested,
                    claim,
                    route.deliveredToRequester(),
                    route.deliveredToNetwork(),
                    route.storedInCpu(),
                    remaining,
                    false);
        }

        long claimed = current.waitingFor.extract(request.expectedKey(), claim, Actionable.MODULATE);
        if (claimed <= 0L) {
            return claimResult(ECOCraftingOutputClaimResult.Status.NO_MATCH, requested, 0L,
                    Math.max(0L, current.remainingAmount));
        }
        // The waiting inventory is server-thread local. A mismatch can only be caused by a re-entrant callback;
        // route no more than the amount actually removed and retain the uncommitted tail in the CPU.
        claim = Math.min(claim, claimed);
        OutputRoute route = routeClaimedOutput(
                current, request.actualKey(), claim, Actionable.MODULATE, finalOutputClaim);

        if (route.storedInCpu() > 0L) {
            host.getInventory().insert(request.actualKey(), route.storedInCpu(), Actionable.MODULATE);
            host.taskSchedulerForOutput().recordPhysicalInsert(request.actualKey());
            if (finalOutputClaim && current.finalOutput != null
                    && !request.actualKey().equals(current.finalOutput.what())) {
                pendingFinalOutputs.merge(request.actualKey(), route.storedInCpu(), NEMath::saturatingAdd);
            }
        }
        host.recordCompletedCraftingWork(claim, request.actualKey().getType());
        if (finalOutputClaim) {
            current.remainingAmount = Math.max(0L, current.remainingAmount - route.deliveredAmount());
        }
        if (route.deliveredToRequester() > 0L || route.deliveredToNetwork() > 0L) {
            host.postChange(request.actualKey());
        }
        host.markCpuDirty();
        host.taskSchedulerForOutput().progress(appeng.hooks.ticking.TickHandler.instance().getCurrentTick());

        boolean finished = finishIfComplete(current);
        long remaining = finished ? 0L : Math.max(0L, current.remainingAmount);
        return new ECOCraftingOutputClaimResult(
                ECOCraftingOutputClaimResult.Status.ACCEPTED,
                requested,
                claim,
                route.deliveredToRequester(),
                route.deliveredToNetwork(),
                route.storedInCpu(),
                remaining,
                finished);
    }

    private ECOCraftingOutputClaimResult claimResult(ECOCraftingOutputClaimResult.Status status,
                                                     long requested, long claimed, long remaining) {
        return new ECOCraftingOutputClaimResult(status, requested, claimed, 0L, 0L, 0L, remaining, false);
    }

    /** Routes a claimed final output without allowing a failing destination to consume the waiting ledger. */
    private OutputRoute routeClaimedOutput(ExecutingCraftingJob current, AEKey actualKey, long amount,
                                           Actionable mode, boolean finalOutputClaim) {
        if (!finalOutputClaim) {
            return new OutputRoute(0L, 0L, amount);
        }

        if (!current.link.isStandalone()) {
            try {
                long inserted = current.link.insert(actualKey, amount, mode);
                inserted = clampRoutedAmount(inserted, amount);
                return new OutputRoute(inserted, 0L, amount - inserted);
            } catch (RuntimeException failure) {
                LOGGER.warn("ECO crafting requester rejected a claimed output; retaining it in the CPU", failure);
                return new OutputRoute(0L, 0L, amount);
            }
        }

        IGrid grid = host.cpu.getGrid();
        if (grid == null) {
            return new OutputRoute(0L, 0L, amount);
        }
        try {
            long inserted = grid.getStorageService().getInventory().insert(
                    actualKey, amount, mode, host.cpu.getActionSource());
            inserted = clampRoutedAmount(inserted, amount);
            return new OutputRoute(0L, inserted, amount - inserted);
        } catch (RuntimeException failure) {
            LOGGER.warn("ECO crafting network rejected a claimed output; retaining it in the CPU", failure);
            return new OutputRoute(0L, 0L, amount);
        }
    }

    /** Retries dynamic final keys that could not be delivered during their original claim. */
    private void deliverPendingFinalOutputs(ExecutingCraftingJob current) {
        if (pendingFinalOutputs.isEmpty()) {
            return;
        }
        for (var entry : List.copyOf(pendingFinalOutputs.entrySet())) {
            AEKey key = entry.getKey();
            long stored = Math.min(entry.getValue(), host.getInventory().list.get(key));
            if (stored <= 0L) {
                pendingFinalOutputs.remove(key);
                continue;
            }
            long amount = Math.min(stored, Math.max(0L, current.remainingAmount));
            if (amount <= 0L) {
                continue;
            }
            long inserted = deliverFinalOutputToDestination(current, key, amount);
            host.getInventory().extract(key, inserted, Actionable.MODULATE);
            if (inserted > 0L) {
                long remaining = Math.max(0L, stored - inserted);
                if (remaining == 0L) {
                    pendingFinalOutputs.remove(key);
                } else {
                    pendingFinalOutputs.put(key, remaining);
                }
                current.remainingAmount = Math.max(0L, current.remainingAmount - inserted);
                host.markCpuDirty();
                host.taskSchedulerForOutput().progress(appeng.hooks.ticking.TickHandler.instance().getCurrentTick());
            } else {
                host.taskSchedulerForOutput().finalDeliveryBlocked(key, amount);
            }
        }
    }

    private long deliverFinalOutputToDestination(ExecutingCraftingJob current, AEKey key, long amount) {
        try {
            deliveringFinalOutput = true;
            long inserted;
            if (current.link.isStandalone()) {
                var grid = host.cpu.getGrid();
                inserted = grid == null ? 0L : grid.getStorageService().getInventory()
                        .insert(key, amount, Actionable.MODULATE, host.cpu.getActionSource());
            } else {
                inserted = current.link.insert(key, amount, Actionable.MODULATE);
            }
            return clampRoutedAmount(inserted, amount);
        } catch (RuntimeException failure) {
            LOGGER.error("Final output delivery failed; items remain in the CPU inventory", failure);
            return 0L;
        } finally {
            deliveringFinalOutput = false;
        }
    }

    private static long clampRoutedAmount(long inserted, long offered) {
        return inserted <= 0L ? 0L : Math.min(inserted, offered);
    }

    private boolean finishIfComplete(ExecutingCraftingJob expected) {
        if (host.getJob() != expected) {
            return !host.hasJob();
        }
        if (expected.remainingAmount > 0L || !expected.waitingFor.list.isEmpty()
                || host.taskSchedulerForOutput().hasPendingTasks(expected)
                || !pendingFinalOutputs.isEmpty()
                || (expected.executionRuntime != null && !expected.executionRuntime.isComplete())) {
            return false;
        }
        host.finishJob(true);
        return !host.hasJob();
    }

    void loadPendingFinalOutputs(CompoundTag jobData, HolderLookup.Provider registries) {
        ListTag entries = jobData.getList("pendingFinalOutputs", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            try {
                GenericStack stack = GenericStack.readTag(registries, entries.getCompound(index));
                if (stack != null && stack.amount() > 0L) {
                    pendingFinalOutputs.merge(stack.what(), stack.amount(), NEMath::saturatingAdd);
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("Ignoring invalid persisted dynamic final-output delivery entry {}", index, failure);
            }
        }
    }

    void writePendingFinalOutputs(CompoundTag jobData, HolderLookup.Provider registries) {
        if (pendingFinalOutputs.isEmpty()) {
            jobData.remove("pendingFinalOutputs");
            return;
        }
        ListTag entries = new ListTag();
        for (var entry : pendingFinalOutputs.entrySet()) {
            if (entry.getValue() > 0L) {
                entries.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getValue())));
            }
        }
        if (entries.isEmpty()) {
            jobData.remove("pendingFinalOutputs");
        } else {
            jobData.put("pendingFinalOutputs", entries);
        }
    }

    private record OutputRoute(long deliveredToRequester, long deliveredToNetwork, long storedInCpu) {
        long deliveredAmount() {
            return NEMath.saturatingAdd(deliveredToRequester, deliveredToNetwork);
        }
    }
}
